package com.iot.dataservice;

import com.iot.common.model.DeviceMetricAgg;
import com.iot.dataservice.service.OlapAnalyticsService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class OlapAnalyticsServiceTest {

    private final OlapAnalyticsService service = new OlapAnalyticsService();

    @Test
    public void testQueryMetricSummaryWithRealAggregations() {
        // 模拟写入 Flink 聚合数据
        DeviceMetricAgg agg = DeviceMetricAgg.builder()
                .deviceId("EBZ-260-掘进机#01")
                .sensorId("cutter_temp")
                .avgValue(58.4)
                .maxValue(65.2)
                .minValue(52.1)
                .sampleCount(120L)
                .windowGranularity("1m")
                .windowStartTime(System.currentTimeMillis() - 60000)
                .windowEndTime(System.currentTimeMillis() - 100)
                .build();

        service.recordAggregate(agg);

        Map<String, Object> summary = service.queryMetricSummary("MINING", "1m", "1h");
        assertNotNull(summary);
        assertEquals(1, summary.get("totalAggregatedSeries"));
        List<?> items = (List<?>) summary.get("items");
        assertEquals(1, items.size());

        Map<String, Object> overview = service.queryScreenOverview();
        assertNotNull(overview);
        assertTrue((Long) overview.get("totalProcessedSamples") >= 120L);
    }

    @Test
    public void testQueryDeviceHealthScoresDynamicCalculation() {
        // 模拟异常温升与振动
        DeviceMetricAgg normalAgg = DeviceMetricAgg.builder()
                .deviceId("EBZ-260-掘进机#01")
                .sensorId("cutter_temp")
                .maxValue(55.0)
                .build();
        service.recordAggregate(normalAgg);

        List<Map<String, Object>> healthList = service.queryDeviceHealthScores();
        assertNotNull(healthList);
        assertFalse(healthList.isEmpty());
        Map<String, Object> dev = healthList.stream()
                .filter(d -> "EBZ-260-掘进机#01".equals(d.get("deviceId")))
                .findFirst().orElseThrow();
        assertEquals("HEALTHY", dev.get("status"));

        // 注入超温与大振动数据
        DeviceMetricAgg warningAgg = DeviceMetricAgg.builder()
                .deviceId("EBZ-260-掘进机#01")
                .sensorId("cutter_temp")
                .maxValue(85.0) // 85度超温
                .build();
        service.recordAggregate(warningAgg);

        List<Map<String, Object>> updatedHealth = service.queryDeviceHealthScores();
        Map<String, Object> warnDev = updatedHealth.stream()
                .filter(d -> "EBZ-260-掘进机#01".equals(d.get("deviceId")))
                .findFirst().orElseThrow();
        assertEquals("ATTENTION", warnDev.get("status"));
        assertTrue((Double) warnDev.get("healthScore") < 85.0);
    }
}