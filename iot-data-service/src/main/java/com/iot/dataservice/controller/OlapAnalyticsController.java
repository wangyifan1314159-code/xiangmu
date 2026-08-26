package com.iot.dataservice.controller;

import com.iot.dataservice.service.OlapAnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/bigdata/analytics")
// 注意：本服务当前仅返回模拟数据（未接入真实 Doris/TDengine），
// 接入真实数据源前必须补充认证/鉴权，禁止放开为任意来源可访问
public class OlapAnalyticsController {

    private final OlapAnalyticsService analyticsService;

    public OlapAnalyticsController(OlapAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * 多维度指标即席聚合 (按时间跨度、产品线、指标)
     */
    @GetMapping("/metric-summary")
    public ResponseEntity<?> getMetricSummary(
            @RequestParam(required = false) String productType,
            @RequestParam(defaultValue = "1h") String granularity,
            @RequestParam(defaultValue = "7d") String timeRange) {
        return ResponseEntity.ok(analyticsService.queryMetricSummary(productType, granularity, timeRange));
    }

    /**
     * 设备全生命周期与健康度评分 (PHM 大数据分析)
     */
    @GetMapping("/device-health")
    public ResponseEntity<?> getDeviceHealthScores() {
        return ResponseEntity.ok(analyticsService.queryDeviceHealthScores());
    }

    /**
     * 实时生产大屏综合指标 (ADS 统一数据服务)
     */
    @GetMapping("/screen-overview")
    public ResponseEntity<?> getScreenOverview() {
        return ResponseEntity.ok(analyticsService.queryScreenOverview());
    }
}