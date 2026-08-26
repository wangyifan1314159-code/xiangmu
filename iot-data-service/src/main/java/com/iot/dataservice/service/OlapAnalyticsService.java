package com.iot.dataservice.service;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class OlapAnalyticsService {

    public Map<String, Object> queryMetricSummary(String productType, String granularity, String timeRange) {
        // 模拟/实际从 Doris DWS / ADS 聚合宽表查询
        List<Map<String, Object>> trend = new ArrayList<>();
        long now = System.currentTimeMillis();
        long step = 3600 * 1000L; // 1h
        for (int i = 24; i >= 0; i--) {
            trend.add(Map.of(
                    "timestamp", now - i * step,
                    "avgValue", Math.round((25.0 + Math.sin(i) * 5.0) * 100.0) / 100.0,
                    "maxValue", Math.round((32.0 + Math.sin(i) * 4.0) * 100.0) / 100.0,
                    "minValue", Math.round((18.0 + Math.sin(i) * 3.0) * 100.0) / 100.0,
                    "sampleCount", 120000 + i * 500
            ));
        }

        return Map.of(
                "productType", productType != null ? productType : "ALL",
                "granularity", granularity,
                "totalSamples", 3000000L,
                "trend", trend
        );
    }

    public List<Map<String, Object>> queryDeviceHealthScores() {
        return List.of(
                Map.of("deviceId", "DEV-LIGHT-001", "healthScore", 96.5, "rulDays", 450, "status", "HEALTHY"),
                Map.of("deviceId", "DEV-TEMP-002", "healthScore", 78.2, "rulDays", 85, "status", "ATTENTION"),
                Map.of("deviceId", "DEV-PRESSURE-003", "healthScore", 92.0, "rulDays", 320, "status", "HEALTHY"),
                Map.of("deviceId", "DEV-VIBRATION-004", "healthScore", 54.0, "rulDays", 14, "status", "CRITICAL")
        );
    }

    public Map<String, Object> queryScreenOverview() {
        return Map.of(
                "totalIngestionQps", 85420,
                "lakeStorageBytes", "4.2 TB",
                "realtimeActiveDevices", 12500,
                "flinkJobLatencyMs", 42,
                "cleanDataRate", "99.85%"
        );
    }
}