package com.iot.controller;
// 系统健康检查控制器
import com.iot.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @Value("${spring.application.name:iot-platform}")
    private String applicationName;

    @Value("${app.version:2.0.0}")
    private String version;

    @GetMapping("/api/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of(
                "status", "UP",
                "service", applicationName,
                "version", version
        ));
    }

    @GetMapping("/api/health/ready")
    public ApiResponse<Map<String, String>> readiness() {
        return ApiResponse.ok(Map.of("status", "READY"));
    }
}
