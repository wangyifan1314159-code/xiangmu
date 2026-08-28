package com.iot.dataservice.controller;

import com.iot.dataservice.service.OlapAnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/bigdata/analytics")
// 安全说明：本模块已引入 spring-boot-starter-security，/api/bigdata/analytics/** 全部端点
// 强制校验 iot-backend 签发的 JWT（HS256，环境变量 APP_JWT_SECRET 与 iot-backend 共用同一密钥，
// 见 com.iot.dataservice.config.SecurityConfig / JwtValidator；未携带或校验失败统一返回 401）。
// metric-summary 已改为显式返回 501 (未接入 Doris 聚合宽表，不再返回伪造数据)；
// device-health / screen-overview 仍返回硬编码示例数据，接入真实数据源前同样需要替换。
// 后续待办：为查询端点补充租户隔离 (tenantId) 校验。
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