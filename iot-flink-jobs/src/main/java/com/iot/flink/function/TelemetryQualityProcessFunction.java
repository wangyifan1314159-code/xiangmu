package com.iot.flink.function;

import com.iot.common.model.TelemetryEvent;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 实时数据质量清洗与传感器死值 (Flatline / Stuck) 检测
 */
public class TelemetryQualityProcessFunction extends KeyedProcessFunction<String, TelemetryEvent, TelemetryEvent> {
    private static final Logger log = LoggerFactory.getLogger(TelemetryQualityProcessFunction.class);

    public static final OutputTag<TelemetryEvent> DIRTY_DATA_TAG = new OutputTag<TelemetryEvent>("dirty-telemetry-data") {};

    // 记录上一次采样值与重复次数 (用于死值/卡死检测)
    private transient ValueState<Double> lastValueState;
    private transient ValueState<Integer> sameValueCountState;

    @Override
    public void open(Configuration parameters) {
        lastValueState = getRuntimeContext().getState(new ValueStateDescriptor<>("last-val", Double.class));
        sameValueCountState = getRuntimeContext().getState(new ValueStateDescriptor<>("same-val-cnt", Integer.class));
    }

    @Override
    public void processElement(TelemetryEvent event, Context ctx, Collector<TelemetryEvent> out) throws Exception {
        Double val = event.getValue();
        if (val == null || Double.isNaN(val) || Double.isInfinite(val)) {
            event.setQualityFlag("INVALID_VALUE");
            ctx.output(DIRTY_DATA_TAG, event);
            return;
        }

        // 1. 物理极值过滤 (比如光照不能为负数，温度不会超过 1500 度等)
        if ("temperature".equalsIgnoreCase(event.getSensorType()) && (val < -100 || val > 1500)) {
            event.setQualityFlag("PHYSICAL_OUT_OF_RANGE");
            ctx.output(DIRTY_DATA_TAG, event);
            return;
        }

        // 2. 传感器死值检测 (连续 20 个点读数完全一样且方差为 0)
        Double lastVal = lastValueState.value();
        Integer sameCnt = sameValueCountState.value();
        if (sameCnt == null) sameCnt = 0;

        if (lastVal != null && Math.abs(lastVal - val) < 0.00001) {
            sameCnt++;
            if (sameCnt >= 20) {
                // 死值数据不再进入下游聚合：标记后旁路到脏数据流，避免污染统计结果
                event.setQualityFlag("SENSOR_FLATLINE_WARNING");
                log.warn("检测到传感器死值并拦截: deviceId={}, sensorId={}, value={}",
                        event.getDeviceId(), event.getSensorId(), val);
                ctx.output(DIRTY_DATA_TAG, event);
                lastValueState.update(val);
                sameValueCountState.update(sameCnt);
                return;
            }
        } else {
            sameCnt = 0;
        }

        lastValueState.update(val);
        sameValueCountState.update(sameCnt);

        // 正常数据输出到下游
        out.collect(event);
    }
}