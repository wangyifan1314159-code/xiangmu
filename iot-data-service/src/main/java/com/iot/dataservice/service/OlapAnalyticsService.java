package com.iot.dataservice.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class OlapAnalyticsService {

    public Map<String, Object> queryMetricSummary(String productType, String granularity, String timeRange) {
        // 尚未接入真实 Doris DWS/ADS 聚合宽表，granularity/timeRange 无法被正确下推，
        // 且当前没有可用的表结构/SQL。为避免向调用方返回伪造的汇总数据，
        // 这里显式返回 501 Not Implemented，而不是硬编码模拟值。
        throw new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "metric-summary 查询尚未接入 Doris 聚合宽表 (productType=" + productType
                        + ", granularity=" + granularity + ", timeRange=" + timeRange + ")");
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