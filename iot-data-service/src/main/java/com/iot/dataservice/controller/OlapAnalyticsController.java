package com.iot.dataservice.controller;

import com.iot.dataservice.service.OlapAnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/bigdata/analytics")
// 安全说明：本模块未引入 spring-boot-starter-security，当前 /api/bigdata/analytics/** 全部端点
// 均无认证/鉴权，仅用于内网开发调试。metric-summary 已改为显式返回 501 (未接入 Doris 聚合宽表，
// 不再返回伪造数据)；device-health / screen-overview 仍返回硬编码示例数据，接入真实数据源前同样
// 需要替换。对外暴露或接入真实数据源前必须：
//   1) 引入 spring-boot-starter-security 并配置最小权限 (API-Key / JWT / OAuth2)；
//   2) 为查询端点补充租户隔离 (tenantId) 校验，禁止任意来源访问。
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