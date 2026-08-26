package com.iot.controller;

import com.iot.dto.ApiResponse;
import com.iot.model.AlertRecord;
import com.iot.model.AlertRule;
import com.iot.service.AlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    // ========== 告警规则 ==========

    @GetMapping("/rules")
    public ApiResponse<List<AlertRule>> getAllRules() {
        return ApiResponse.ok(alertService.getAllRules());
    }

    @GetMapping("/rules/{id}")
    public ApiResponse<AlertRule> getRule(@PathVariable Long id) {
        return ApiResponse.ok(alertService.getRule(id));
    }

    @PostMapping("/rules")
    public ApiResponse<AlertRule> createRule(@Valid @RequestBody AlertRule rule) {
        return ApiResponse.ok("告警规则创建成功", alertService.createRule(rule));
    }

    @PutMapping("/rules/{id}")
    public ApiResponse<AlertRule> updateRule(@PathVariable Long id,
                                              @Valid @RequestBody AlertRule rule) {
        return ApiResponse.ok("告警规则更新成功", alertService.updateRule(id, rule));
    }

    @DeleteMapping("/rules/{id}")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        alertService.deleteRule(id);
        return ApiResponse.ok("告警规则已删除", null);
    }

    @PatchMapping("/rules/{id}/toggle")
    public ApiResponse<AlertRule> toggleRule(@PathVariable Long id,
                                              @RequestParam boolean enabled) {
        return ApiResponse.ok(alertService.toggleRule(id, enabled));
    }

    // ========== 告警记录 ==========

    @GetMapping("/records")
    public ApiResponse<Page<AlertRecord>> getRecords(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(alertService.getAlertRecords(
                deviceId, status, level, PageRequest.of(page, size)));
    }

    @GetMapping("/records/{id}")
    public ApiResponse<AlertRecord> getRecord(@PathVariable Long id) {
        return ApiResponse.ok(alertService.getAlertRecord(id));
    }

    @PatchMapping("/records/{id}/acknowledge")
    public ApiResponse<AlertRecord> acknowledge(@PathVariable Long id) {
        return ApiResponse.ok("告警已确认", alertService.acknowledgeAlert(id));
    }

    @PatchMapping("/records/{id}/resolve")
    public ApiResponse<AlertRecord> resolve(@PathVariable Long id) {
        return ApiResponse.ok("告警已解决", alertService.resolveAlert(id));
    }

    @DeleteMapping("/records/{id}")
    public ApiResponse<Void> deleteRecord(@PathVariable Long id) {
        alertService.deleteAlert(id);
        return ApiResponse.ok("告警记录已删除", null);
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Long>> getStats() {
        return ApiResponse.ok(alertService.getAlertStats());
    }
}
