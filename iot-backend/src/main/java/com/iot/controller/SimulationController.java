package com.iot.controller;

import com.iot.config.SecurityUtils;
import com.iot.dto.ApiResponse;
import com.iot.model.CommandLog;
import com.iot.repository.CommandLogRepository;
import com.iot.service.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;
    private final CommandLogRepository commandLogRepository;
    private final SecurityUtils securityUtils;

    /**
     *  开始数据上传模拟（设备 -> 平台）
     */
    @PostMapping("/upload/start")
    public ApiResponse<Map<String, Object>> startUpload(@RequestParam(defaultValue = "5") int interval) {
        simulationService.startDataSimulation(interval);
        return ApiResponse.ok("数据上发模拟已启动", Map.of("intervalSeconds", interval));
    }

    /**
     * 停止数据上传模拟
     */
    @PostMapping("/upload/stop")
    public ApiResponse<Void> stopUpload() {
        simulationService.stopDataSimulation();
        return ApiResponse.ok("数据上发模拟已停止", null);
    }

    /**
     * 启动命令传递模拟（平台->设备）
     */
    @PostMapping("/delivery/start")
    public ApiResponse<Map<String, Object>> startDelivery(@RequestParam(defaultValue = "10") int interval) {
        simulationService.startCommandSimulation(interval);
        return ApiResponse.ok("命令下报模拟已启动", Map.of("intervalSeconds", interval));
    }

    /**
     * 停止命令传递模拟
     */
    @PostMapping("/delivery/stop")
    public ApiResponse<Void> stopDelivery() {
        simulationService.stopCommandSimulation();
        return ApiResponse.ok("命令下报模拟已停止", null);
    }

    /**
     * 停止所有的情况
     */
    @PostMapping("/stop")
    public ApiResponse<Void> stopAll() {
        simulationService.stopAll();
        return ApiResponse.ok("所有模拟已停止", null);
    }

    /**
     * 获取模拟状态
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getStatus() {
        return ApiResponse.ok(simulationService.getStatus());
    }

    /**
     * 手动触发一轮数据上传
     */
    @PostMapping("/upload/once")
    public ApiResponse<Map<String, Object>> triggerUploadOnce() {
        return ApiResponse.ok("已触发一次数据上发", simulationService.triggerOnce());
    }

    /**
     * 手动触发一轮命令传递
     */
    @PostMapping("/delivery/once")
    public ApiResponse<Map<String, Object>> triggerDeliveryOnce() {
        simulationService.simulateCommandDelivery();
        return ApiResponse.ok("已触发一次命令下报", null);
    }

    /**
     * 获取命令的历史记录
     */
    @GetMapping("/commands")
    public ApiResponse<List<CommandLog>> getCommandLogs(
            @RequestParam(required = false) String deviceId) {
        Long uid = securityUtils.getCurrentUserId();
        if (uid == null) return ApiResponse.ok(List.of());
        if (deviceId != null && !deviceId.isEmpty()) {
            return ApiResponse.ok(commandLogRepository.findByDeviceIdAndOwnerIdOrderBySentAtDesc(deviceId, uid));
        }
        return ApiResponse.ok(commandLogRepository.findTop20ByOwnerIdOrderBySentAtDesc(uid));
    }
}
