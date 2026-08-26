package com.iot.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.common.model.DeviceMetricAgg;
import com.iot.common.model.TelemetryEvent;
import com.iot.flink.function.TelemetryQualityProcessFunction;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
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
 * IoT 大数据实时流计算主任务
 * 架构：Kafka -> Watermark & 迟到流分道 -> 质量清洗与死值检测 -> 1分钟聚合 (Doris) & 告警 CEP
 */
public class TelemetryStreamApp {
    private static final Logger log = LoggerFactory.getLogger(TelemetryStreamApp.class);

    // 迟到数据侧输出流 (直接存入 Iceberg / 历史湖)
    public static final OutputTag<TelemetryEvent> LATE_DATA_TAG = new OutputTag<TelemetryEvent>("late-telemetry-data") {};

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 开启 Checkpoint 保证 Exactly-Once / At-Least-Once
        env.enableCheckpointing(60000);
        env.setParallelism(2);

        String kafkaBootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        // 与后端 application.yml 的 app.kafka.topics.device-telemetry 保持一致，
        // 否则 Flink 消费不到后端写入的遥测数据
        String topic = System.getenv().getOrDefault("KAFKA_TELEMETRY_TOPIC", "device.telemetry.v1");

        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaBootstrap)
                .setTopics(topic)
                .setGroupId("iot-flink-telemetry-group")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        ObjectMapper mapper = new ObjectMapper();

        // 1. 数据摄入与反序列化
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
                });

        // 2. 水位线定义 (允许 5 秒数据乱序)
        WatermarkStrategy<TelemetryEvent> watermarkStrategy = WatermarkStrategy
                .<TelemetryEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                .withTimestampAssigner((event, timestamp) -> event.getTimestamp());

        DataStream<TelemetryEvent> watermarkedStream = rawStream.assignTimestampsAndWatermarks(watermarkStrategy);

        // 3. 实时数据质量清洗 (死值检测、极值过滤)
        SingleOutputStreamOperator<TelemetryEvent> validatedStream = watermarkedStream
                .keyBy(e -> e.getDeviceId() + "#" + e.getSensorId())
                .process(new TelemetryQualityProcessFunction());

        // 获取脏数据与异常流
        DataStream<TelemetryEvent> dirtyStream = validatedStream.getSideOutput(TelemetryQualityProcessFunction.DIRTY_DATA_TAG);
        dirtyStream.print("Dirty-Data-Alert");

        // 4. 实时 1 分钟滚动窗口聚合 (写入 Doris / OLAP 聚合表)
        SingleOutputStreamOperator<DeviceMetricAgg> oneMinAggStream = validatedStream
                .keyBy(e -> e.getDeviceId() + "#" + e.getSensorId())
                .window(TumblingEventTimeWindows.of(Time.minutes(1)))
                .sideOutputLateData(LATE_DATA_TAG) // 捕获迟到断网重传数据
                .aggregate(new MetricAggregator(), new MetricWindowFunction());

        oneMinAggStream.print("1min-Agg-To-Doris");

        // 5. 迟到数据旁路流 (写入数据湖 Iceberg，避免数据丢失)
        DataStream<TelemetryEvent> lateStream = oneMinAggStream.getSideOutput(LATE_DATA_TAG);
        lateStream.print("Late-Data-To-Iceberg-Lake");

        log.info("Flink IoT BigData Streaming Topology initialized successfully");
        env.execute("IoT-BigData-Telemetry-Stream-Pipeline");
    }

    // 窗口累加器
    public static class MetricAggregator implements AggregateFunction<TelemetryEvent, double[], double[]> {
        @Override
        public double[] createAccumulator() {
            // [sum, max, min, count]
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
                    .avgValue(avg)
                    .maxValue(stats[1])
                    .minValue(stats[2])
                    .sampleCount((long) count)
                    .build();
            out.collect(agg);
        }
    }
}