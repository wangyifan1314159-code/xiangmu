package com.iot.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.common.model.DeviceMetricAgg;
import com.iot.common.model.TelemetryEvent;
import com.iot.flink.function.TelemetryQualityProcessFunction;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * IoT 大数据实时流计算主任务 (端到端闭环流水线)
 * 架构：Kafka (Raw Telemetry)
 *       → Watermark (允许 5s 乱序)
 *       → 质量清洗与死值过滤 (TelemetryQualityProcessFunction)
 *       → 1分钟滚动窗口聚合 (Doris/Kafka Sink: device.metric.agg.v1)
 *       → 异常脏数据告警 (Kafka Sink: alert.events.v1)
 */
public class TelemetryStreamApp {
    private static final Logger log = LoggerFactory.getLogger(TelemetryStreamApp.class);

    // 迟到数据侧输出流
    public static final OutputTag<TelemetryEvent> LATE_DATA_TAG = new OutputTag<TelemetryEvent>("late-telemetry-data") {};

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(30000);
        env.setParallelism(2);

        String kafkaBootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        String rawTopic = System.getenv().getOrDefault("KAFKA_TELEMETRY_TOPIC", "device.telemetry.v1");
        String aggTopic = System.getenv().getOrDefault("KAFKA_AGG_TOPIC", "device.metric.agg.v1");
        String alertTopic = System.getenv().getOrDefault("KAFKA_ALERT_TOPIC", "alert.events.v1");
        String lateTopic = System.getenv().getOrDefault("KAFKA_LATE_TOPIC", "device.late.v1");

        // 1. Kafka Source 数据源
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaBootstrap)
                .setTopics(rawTopic)
                .setGroupId("iot-flink-telemetry-group")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        ObjectMapper mapper = new ObjectMapper();

        // 2. 数据摄入与反序列化
        DataStream<TelemetryEvent> rawStream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "Kafka-Raw-Telemetry")
                .flatMap((String json, Collector<TelemetryEvent> out) -> {
                    try {
                        TelemetryEvent event = mapper.readValue(json, TelemetryEvent.class);
                        if (event.getTimestamp() == null) {
                            event.setTimestamp(System.currentTimeMillis());
                        }
                        out.collect(event);
                    } catch (Exception e) {
                        log.error("JSON parse failed: {}", json, e);
                    }
                })
                .returns(TelemetryEvent.class);

        // 3. 水位线定义 (允许 5 秒乱序)
        WatermarkStrategy<TelemetryEvent> watermarkStrategy = WatermarkStrategy
                .<TelemetryEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                .withTimestampAssigner((event, timestamp) -> event.getTimestamp())
                .withIdleness(Duration.ofSeconds(60));

        DataStream<TelemetryEvent> watermarkedStream = rawStream.assignTimestampsAndWatermarks(watermarkStrategy);

        // 4. 实时数据质量清洗 (死值检测、极值过滤)
        SingleOutputStreamOperator<TelemetryEvent> validatedStream = watermarkedStream
                .keyBy(e -> e.getDeviceId() + "#" + e.getSensorId())
                .process(new TelemetryQualityProcessFunction());

        // 5. 异常脏数据 Sink (Kafka alert.events.v1)
        DataStream<TelemetryEvent> dirtyStream = validatedStream.getSideOutput(TelemetryQualityProcessFunction.DIRTY_DATA_TAG);
        dirtyStream.print("Dirty-Data-Alert");

        KafkaSink<String> alertSink = KafkaSink.<String>builder()
                .setBootstrapServers(kafkaBootstrap)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.builder()
                                .setTopic(alertTopic)
                                .setValueSerializationSchema(new SimpleStringSchema())
                                .build()
                )
                .build();

        dirtyStream
                .map(event -> {
                    try { return mapper.writeValueAsString(event); }
                    catch (Exception e) { return "{}"; }
                })
                .sinkTo(alertSink)
                .name("Kafka-Alert-Sink");

        // 6. 实时 1 分钟滚动窗口聚合 (Kafka device.metric.agg.v1 & 湖仓 Sink)
        SingleOutputStreamOperator<DeviceMetricAgg> oneMinAggStream = validatedStream
                .keyBy(e -> e.getDeviceId() + "#" + e.getSensorId())
                .window(TumblingEventTimeWindows.of(Time.minutes(1)))
                .sideOutputLateData(LATE_DATA_TAG)
                .aggregate(new MetricAggregator(), new MetricWindowFunction());

        oneMinAggStream.print("1min-Agg-Metrics");

        KafkaSink<String> aggSink = KafkaSink.<String>builder()
                .setBootstrapServers(kafkaBootstrap)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.builder()
                                .setTopic(aggTopic)
                                .setValueSerializationSchema(new SimpleStringSchema())
                                .build()
                )
                .build();

        oneMinAggStream
                .map(agg -> {
                    try { return mapper.writeValueAsString(agg); }
                    catch (Exception e) { return "{}"; }
                })
                .sinkTo(aggSink)
                .name("Kafka-Agg-Sink");

        // 7. 迟到断网重传数据 Sink
        DataStream<TelemetryEvent> lateStream = oneMinAggStream.getSideOutput(LATE_DATA_TAG);
        lateStream.print("Late-Data-To-Lake");

        KafkaSink<String> lateSink = KafkaSink.<String>builder()
                .setBootstrapServers(kafkaBootstrap)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.builder()
                                .setTopic(lateTopic)
                                .setValueSerializationSchema(new SimpleStringSchema())
                                .build()
                )
                .build();

        lateStream
                .map(event -> {
                    try { return mapper.writeValueAsString(event); }
                    catch (Exception e) { return "{}"; }
                })
                .sinkTo(lateSink)
                .name("Kafka-Late-Sink");

        log.info("Flink IoT BigData Streaming Pipeline initialized successfully. Sinks connected.");
        env.execute("IoT-BigData-Telemetry-Stream-Pipeline");
    }

    // 窗口累加器 [sum, max, min, count]
    public static class MetricAggregator implements AggregateFunction<TelemetryEvent, double[], double[]> {
        @Override
        public double[] createAccumulator() {
            return new double[]{0.0, -Double.MAX_VALUE, Double.MAX_VALUE, 0.0};
        }

        @Override
        public double[] add(TelemetryEvent value, double[] acc) {
            double v = value.getValue();
            acc[0] += v;
            acc[1] = Math.max(acc[1], v);
            acc[2] = Math.min(acc[2], v);
            acc[3] += 1;
            return acc;
        }

        @Override
        public double[] getResult(double[] acc) {
            return acc;
        }

        @Override
        public double[] merge(double[] a, double[] b) {
            return new double[]{
                    a[0] + b[0],
                    Math.max(a[1], b[1]),
                    Math.min(a[2], b[2]),
                    a[3] + b[3]
            };
        }
    }

    public static class MetricWindowFunction extends ProcessWindowFunction<double[], DeviceMetricAgg, String, TimeWindow> {
        @Override
        public void process(String key, Context context, Iterable<double[]> elements, Collector<DeviceMetricAgg> out) {
            String[] parts = key.split("#");
            String deviceId = parts[0];
            String sensorId = parts.length > 1 ? parts[1] : "";
            double[] stats = elements.iterator().next();
            double count = stats[3];
            double avg = count > 0 ? stats[0] / count : 0.0;

            DeviceMetricAgg agg = DeviceMetricAgg.builder()
                    .deviceId(deviceId)
                    .sensorId(sensorId)
                    .windowGranularity("1m")
                    .windowStartTime(context.window().getStart())
                    .windowEndTime(context.window().getEnd())
                    .avgValue(Math.round(avg * 100.0) / 100.0)
                    .maxValue(Math.round(stats[1] * 100.0) / 100.0)
                    .minValue(Math.round(stats[2] * 100.0) / 100.0)
                    .sampleCount((long) count)
                    .build();
            out.collect(agg);
        }
    }
}