package com.iot.controller;

import com.iot.dto.ApiResponse;
import com.iot.dto.CommandRequest;
import com.iot.model.DataPoint;
import com.iot.service.DataService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
public class DataController {

    private final DataService dataService;

    /**
     * 查询设备数据，支持时间范围过滤
     * GET /api/data/{deviceId}?sensorId=s_001&from=2024-01-01T00:00:00&to=2024-01-02T00:00:00&limit=200
     */
    @GetMapping("/{deviceId}")
    public ApiResponse<List<DataPoint>> getDeviceData(
            @PathVariable String deviceId,
            @RequestParam(required = false) String sensorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "200") int limit) {
        validateRange(from, to);
        return ApiResponse.ok(dataService.getDeviceData(deviceId, sensorId, from, to, limit));
    }

    /**
     * 历史数据聚合查询
     * GET /api/data/{deviceId}/history?sensorId=s_001&from=...&to=...&interval=5m
     */
    @GetMapping("/{deviceId}/history")
    public ApiResponse<List<Map<String, Object>>> getHistoryData(
            @PathVariable String deviceId,
            @RequestParam(required = false) String sensorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String interval) {
        validateRange(from, to);
        return ApiResponse.ok(dataService.queryHistory(deviceId, sensorId, from, to, interval));
    }

    @PostMapping("/{deviceId}")
    public ApiResponse<DataPoint> addDataPoint(
            @PathVariable String deviceId,
            @RequestBody Map<String, Object> body) {
        String sensorId = body.get("sensorId") instanceof String s ? s : null;
        Object raw = body.get("value");
        Number rawValue = raw instanceof Number n ? n : null;
        if (sensorId == null || sensorId.isBlank()) {
            throw new RuntimeException("sensorId 不能为空");
        }
        if (rawValue == null || !Double.isFinite(rawValue.doubleValue())) {
            throw new RuntimeException("value 不能为空且须为数字");
        }
        return ApiResponse.ok(dataService.addDataPoint(deviceId, sensorId, rawValue.doubleValue()));
    }

    private void validateRange(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new RuntimeException("from 不能晚于 to");
        }
    }

    /**
     * 获取设备最新 N 条数据（轻量级，仅返回 value + 时间）
     * GET /api/data/{deviceId}/latest?sensorId=s_001&limit=10
     * <p>支持 X-Api-Key 认证</p>
     */
    @GetMapping("/{deviceId}/latest")
    public ApiResponse<List<Map<String, Object>>> getLatestData(
            @PathVariable String deviceId,
            @RequestParam(required = false) String sensorId,
            @RequestParam(defaultValue = "10") int limit) {
        List<DataPoint> data = dataService.getDeviceData(deviceId, sensorId, null, null, limit);
        List<Map<String, Object>> result = data.stream().map(dp -> {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("value", dp.getValue());
            item.put("sensorId", dp.getSensorId());
            item.put("timestamp", dp.getTimestamp() != null ? dp.getTimestamp().toString() : null);
            return item;
        }).toList();
        return ApiResponse.ok(result);
    }

    @PostMapping("/{deviceId}/command")
    public ApiResponse<Map<String, String>> sendCommand(
            @PathVariable String deviceId,
            @RequestBody CommandRequest request) {
        String result = dataService.sendCommand(deviceId, request.getCommand(), request.getParams());
        return ApiResponse.ok(Map.of("message", result, "deviceId", deviceId, "command", request.getCommand()));
    }
}
