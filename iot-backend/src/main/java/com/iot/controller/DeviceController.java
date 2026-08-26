package com.iot.controller;

import com.iot.dto.ApiResponse;
import com.iot.dto.DeviceRequest;
import com.iot.model.Device;
import com.iot.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping
    public ApiResponse<List<Device>> getAllDevices() {
        List<Device> devices = deviceService.getAllDevices();
        devices.forEach(d -> d.setApiKey(maskApiKey(d.getApiKey())));
        return ApiResponse.ok(devices);
    }

    @GetMapping("/{deviceId}")
    public ApiResponse<Device> getDevice(@PathVariable String deviceId) {
        Device device = deviceService.getDeviceById(deviceId);
        device.setApiKey(maskApiKey(device.getApiKey()));
        return ApiResponse.ok(device);
    }

    @PostMapping
    public ApiResponse<Device> createDevice(@Valid @RequestBody DeviceRequest request) {
        return ApiResponse.ok("设备添加成功", deviceService.createDevice(request));
    }

    @PutMapping("/{deviceId}")
    public ApiResponse<Device> updateDevice(@PathVariable String deviceId,
                                            @Valid @RequestBody DeviceRequest request) {
        return ApiResponse.ok("设备更新成功", deviceService.updateDevice(deviceId, request));
    }

    @PostMapping("/{deviceId}/api-key/regenerate")
    public ApiResponse<Map<String, String>> regenerateApiKey(@PathVariable String deviceId) {
        Device device = deviceService.regenerateApiKey(deviceId);
        return ApiResponse.ok("API Key 已重新生成", Map.of("apiKey", device.getApiKey()));
    }

    @DeleteMapping("/{deviceId}")
    public ApiResponse<Void> deleteDevice(@PathVariable String deviceId) {
        deviceService.deleteDevice(deviceId);
        return ApiResponse.ok("设备已删除", null);
    }

    @PatchMapping("/{deviceId}/status")
    public ApiResponse<Device> updateStatus(@PathVariable String deviceId,
                                            @RequestBody Map<String, String> body) {
        Device device = deviceService.updateDeviceStatus(deviceId, body.get("status"));
        device.setApiKey(maskApiKey(device.getApiKey()));
        return ApiResponse.ok(device);
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Long>> getStats() {
        Map<String, Long> stats = Map.of(
            "total", deviceService.countAll(),
            "online", deviceService.countByStatus("ONLINE"),
            "offline", deviceService.countByStatus("OFFLINE"),
            "warning", deviceService.countByStatus("WARNING")
        );
        return ApiResponse.ok(stats);
    }

    /** API Key 脱敏：完整 Key 仅在创建或重新生成时返回 */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) return "****";
        return apiKey.substring(0, 4) + "****";
    }
}
