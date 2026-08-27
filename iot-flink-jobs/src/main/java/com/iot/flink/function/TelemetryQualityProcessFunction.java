package com.iot.flink.function;

import com.iot.common.model.TelemetryEvent;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.time.Time;
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
    // 平线周期内是否已发出过告警 (防止同一平线周期反复告警)
    private transient ValueState<Boolean> flatlineAlertedState;

    @Override
    public void open(Configuration parameters) {
        // 状态 TTL：设备停更 30 分钟后自动过期，避免为每台设备永久保留 keyed state。
        // 30 分钟足以覆盖死值检测所需的 20 个连续采样窗口，同时回收已下线设备的状态。
        StateTtlConfig ttlConfig = StateTtlConfig
                .newBuilder(Time.minutes(30))
                .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
                .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
                .cleanupIncrementally(10, false)
                .build();

        ValueStateDescriptor<Double> lastValDesc = new ValueStateDescriptor<>("last-val", Double.class);
        lastValDesc.enableTimeToLive(ttlConfig);
        lastValueState = getRuntimeContext().getState(lastValDesc);

        ValueStateDescriptor<Integer> sameCntDesc = new ValueStateDescriptor<>("same-val-cnt", Integer.class);
        sameCntDesc.enableTimeToLive(ttlConfig);
        sameValueCountState = getRuntimeContext().getState(sameCntDesc);

        ValueStateDescriptor<Boolean> flatlineAlertedDesc = new ValueStateDescriptor<>("flatline-alerted", Boolean.class);
        flatlineAlertedDesc.enableTimeToLive(ttlConfig);
        flatlineAlertedState = getRuntimeContext().getState(flatlineAlertedDesc);
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
        Boolean flatlineAlerted = flatlineAlertedState.value();

        if (lastVal != null && Math.abs(lastVal - val) < 0.00001) {
            sameCnt++;
            if (sameCnt >= 20) {
                // 死值数据不再进入下游聚合：达到阈值后旁路到脏数据流，避免污染统计结果。
                // 每个平线周期只告警一次：首次达到阈值时置位标志并发出告警，
                // 后续相同读数只被拦截(丢弃)，不再重复告警，避免告警风暴。
                if (!Boolean.TRUE.equals(flatlineAlerted)) {
                    event.setQualityFlag("SENSOR_FLATLINE_WARNING");
                    log.warn("检测到传感器死值并拦截: deviceId={}, sensorId={}, value={}",
                            event.getDeviceId(), event.getSensorId(), val);
                    ctx.output(DIRTY_DATA_TAG, event);
                    flatlineAlertedState.update(true);
                }
                // 计数器封顶，避免持续相同读数导致计数无限增长
                sameCnt = 20;
                lastValueState.update(val);
                sameValueCountState.update(sameCnt);
                return;
            }
        } else {
            sameCnt = 0;
            // 读数发生变化 -> 平线周期结束，允许下一轮平线重新告警
            if (Boolean.TRUE.equals(flatlineAlerted)) {
                flatlineAlertedState.update(false);
            }
        }

        lastValueState.update(val);
        sameValueCountState.update(sameCnt);

        // 正常数据输出到下游
        out.collect(event);
    }
}