package com.iot.flink;

import com.iot.common.model.DeviceMetricAgg;
import com.iot.common.model.TelemetryEvent;
import com.iot.flink.function.TelemetryQualityProcessFunction;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.streaming.api.operators.KeyedProcessOperator;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;
import org.apache.flink.streaming.util.KeyedOneInputStreamOperatorTestHarness;
import org.apache.flink.streaming.util.OneInputStreamOperatorTestHarness;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TelemetryPipelineBenchmarkTest {

    @Test
    @DisplayName("端到端大数据流质量清洗与延迟性能基准测试 (10,000 帧实测)")
    public void testQualityProcessAndLatencyBenchmark() throws Exception {
        // 1. 初始化 Flink KeyedProcessFunction 测试算子
        TelemetryQualityProcessFunction function = new TelemetryQualityProcessFunction();
        KeyedOneInputStreamOperatorTestHarness<String, TelemetryEvent, TelemetryEvent> testHarness =
                new KeyedOneInputStreamOperatorTestHarness<>(
                        new KeyedProcessOperator<>(function),
                        e -> e.getDeviceId() + "#" + e.getSensorId(),
                        Types.STRING
                );

        testHarness.open();

        int totalEvents = 10000;
        List<Long> latencies = new ArrayList<>(totalEvents);
        List<TelemetryEvent> normalOutputs = new ArrayList<>();
        List<TelemetryEvent> dirtyOutputs = new ArrayList<>();

        long startTimeNano = System.nanoTime();
        long baseTime = System.currentTimeMillis() - 60000;

        // 2. 注入模拟高频数据流 (含常规数据、连续25个平线死值、超限极端值)
        for (int i = 0; i < totalEvents; i++) {
            long eventTime = baseTime + i * 5; // 5ms 产生一个事件
            double value = 50.0 + (i % 30);
            String sensorType = "temperature";
            String devId = "EBZ-260-掘进机#01";
            String sensorId = "cutter_temp";

            // 注入异常数据测试清洗
            if (i >= 100 && i < 130) {
                value = 65.55; // 连续 30 次完全相同死值
            } else if (i == 500) {
                value = 2500.0; // 物理超限 (极值)
            } else if (i == 600) {
                value = -500.0; // 物理超限 (极值)
            }

            TelemetryEvent event = TelemetryEvent.builder()
                    .deviceId(devId)
                    .sensorId(sensorId)
                    .sensorType(sensorType)
                    .value(value)
                    .unit("℃")
                    .timestamp(eventTime)
                    .build();

            long t0 = System.nanoTime();
            testHarness.processElement(new StreamRecord<>(event, eventTime));
            long t1 = System.nanoTime();

            latencies.add(t1 - t0);
        }

        long totalDurationNano = System.nanoTime() - startTimeNano;
        double totalDurationMs = totalDurationNano / 1_000_000.0;

        // 3. 统计清洗产出
        java.util.Queue<Object> rawOutput = testHarness.getOutput();
        for (Object item : rawOutput) {
            if (item instanceof StreamRecord) {
                normalOutputs.add((TelemetryEvent) ((StreamRecord<?>) item).getValue());
            }
        }

        java.util.Queue<StreamRecord<TelemetryEvent>> sideOutputs = testHarness.getSideOutput(TelemetryQualityProcessFunction.DIRTY_DATA_TAG);
        if (sideOutputs != null) {
            for (StreamRecord<TelemetryEvent> record : sideOutputs) {
                dirtyOutputs.add(record.getValue());
            }
        }

        testHarness.close();

        // 4. 延迟指标分位数计算
        Collections.sort(latencies);
        double minLatencyUs = latencies.get(0) / 1000.0;
        double p50LatencyUs = latencies.get((int) (totalEvents * 0.50)) / 1000.0;
        double p95LatencyUs = latencies.get((int) (totalEvents * 0.95)) / 1000.0;
        double p99LatencyUs = latencies.get((int) (totalEvents * 0.99)) / 1000.0;
        double maxLatencyUs = latencies.get(totalEvents - 1) / 1000.0;
        double throughputEps = (totalEvents / (totalDurationMs / 1000.0));

        // 5. 校验清洗断言
        Assertions.assertTrue(dirtyOutputs.size() >= 3, "必须成功拦截死值与物理极值脏数据");
        Assertions.assertTrue(normalOutputs.size() > 9900, "绝大部分正常数据顺利通行");

        System.out.println("==========================================================");
        System.out.println("★ Flink 大数据实时清洗与传输延迟基准测试结果 ★");
        System.out.println(String.format("总处理事件数: %,d 帧", totalEvents));
        System.out.println(String.format("总耗时: %.2f ms | 吞吐能力: %,.0f 事件/秒 (EPS)", totalDurationMs, throughputEps));
        System.out.println(String.format("最小处理延迟 (Min): %.2f μs (%.4f ms)", minLatencyUs, minLatencyUs / 1000.0));
        System.out.println(String.format("P50 中位数延迟: %.2f μs (%.4f ms)", p50LatencyUs, p50LatencyUs / 1000.0));
        System.out.println(String.format("P95 延迟包络: %.2f μs (%.4f ms)", p95LatencyUs, p95LatencyUs / 1000.0));
        System.out.println(String.format("P99 极端延迟: %.2f μs (%.4f ms)", p99LatencyUs, p99LatencyUs / 1000.0));
        System.out.println(String.format("最大延迟 (Max): %.2f μs (%.4f ms)", maxLatencyUs, maxLatencyUs / 1000.0));
        System.out.println(String.format("拦截脏数据帧数: %d 帧 | 正常通过帧数: %d 帧", dirtyOutputs.size(), normalOutputs.size()));
        System.out.println("==========================================================");
    }

    @Test
    @DisplayName("窗口聚合计算精度与聚合延迟测试")
    public void testAggregationAccuracy() {
        TelemetryStreamApp.MetricAggregator aggregator = new TelemetryStreamApp.MetricAggregator();
        double[] acc = aggregator.createAccumulator();

        double[] values = { 55.2, 58.4, 62.0, 54.8, 60.6 };
        for (double v : values) {
            acc = aggregator.add(TelemetryEvent.builder().value(v).build(), acc);
        }

        double[] result = aggregator.getResult(acc);
        double sum = result[0];
        double max = result[1];
        double min = result[2];
        double count = result[3];
        double avg = sum / count;

        Assertions.assertEquals(5.0, count, 0.001);
        Assertions.assertEquals(62.0, max, 0.001);
        Assertions.assertEquals(54.8, min, 0.001);
        Assertions.assertEquals(58.2, avg, 0.01);
    }
}
