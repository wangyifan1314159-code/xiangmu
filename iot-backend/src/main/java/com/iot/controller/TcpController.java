package com.iot.controller;

import com.iot.config.SecurityUtils;
import com.iot.dto.ApiResponse;
import com.iot.service.TcpConnectionManager;
import com.iot.service.TcpListenerManager;
import com.iot.service.TunnelingMachineTcpClientConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * TCP 设备接入通道状态查询（登录用户可见）。
 * GET /api/tcp/status
 * GET /api/tcp/connections      连接实例列表（普通用户仅见自己的设备，管理员见全部）
 * DELETE /api/tcp/connections/{deviceId}   强制断开某设备 TCP 连接（管理员）
 */
@RestController
@RequestMapping("/api/tcp")
public class TcpController {

    // app.tcp.enabled=false 时该 Bean 不存在，连接状态返回 disabled
    @Autowired(required = false)
    private TcpConnectionManager tcpConnectionManager;

    @Autowired(required = false)
    private TcpListenerManager tcpListenerManager;

    @Autowired(required = false)
    private TunnelingMachineTcpClientConnectionManager tunnelingMachineTcpClientConnectionManager;

    private final SecurityUtils securityUtils;

    @Autowired
    public TcpController(SecurityUtils securityUtils) {
        this.securityUtils = securityUtils;
    }

    TcpController(SecurityUtils securityUtils, TcpListenerManager tcpListenerManager) {
        this.securityUtils = securityUtils;
        this.tcpListenerManager = tcpListenerManager;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        if (tcpConnectionManager == null) {
            return ApiResponse.ok(Map.of(
                    "enabled", false,
                    "onlineDevices", 0,
                    "onlineConnections", 0));
        }
        if (securityUtils.hasRole("ADMIN")) {
            int connections = tcpConnectionManager.listConnections().size();
            return ApiResponse.ok(Map.of(
                    "enabled", true,
                    "onlineDevices", tcpConnectionManager.onlineCount(),
                    "onlineConnections", connections));
        }
        // 非管理员仅统计自己设备相关的连接，避免泄露全局在线设备数量
        int ownConnections = tcpConnectionManager.listConnectionsByOwner(securityUtils.getCurrentUserId()).size();
        return ApiResponse.ok(Map.of(
                "enabled", true,
                "onlineDevices", ownConnections,
                "onlineConnections", ownConnections));
    }

    /**
     * 在线连接实例列表。
     * 普通用户仅返回自己设备的连接；管理员返回全部设备连接。
     */
    @GetMapping("/connections")
    public ApiResponse<List<Map<String, Object>>> connections() {
        if (tcpConnectionManager == null) {
            return ApiResponse.ok(List.of());
        }
        if (securityUtils.hasRole("ADMIN")) {
            return ApiResponse.ok(tcpConnectionManager.listConnections());
        }
        return ApiResponse.ok(tcpConnectionManager.listConnectionsByOwner(securityUtils.getCurrentUserId()));
    }

    /** 强制断开设备 TCP 连接（仅管理员） */
    @DeleteMapping("/connections/{deviceId}")
    public ApiResponse<Map<String, Object>> disconnect(@PathVariable String deviceId) {
        if (!securityUtils.hasRole("ADMIN")) {
            throw new RuntimeException("无权执行该操作");
        }
        if (tcpConnectionManager == null) {
            throw new RuntimeException("TCP 通道未启用");
        }
        boolean closed = tcpConnectionManager.disconnect(deviceId);
        if (!closed) {
            throw new RuntimeException("设备 TCP 连接不存在或已离线");
        }
        return ApiResponse.ok("已强制断开设备 TCP 连接", Map.of("deviceId", deviceId));
    }

    @GetMapping("/binary-connections")
    public ApiResponse<List<Map<String, Object>>> binaryConnections() {
        if (tunnelingMachineTcpClientConnectionManager == null) {
            return ApiResponse.ok(List.of());
        }
        if (securityUtils.hasRole("ADMIN")) {
            return ApiResponse.ok(tunnelingMachineTcpClientConnectionManager.listConnections());
        }
        return ApiResponse.ok(tunnelingMachineTcpClientConnectionManager.listConnectionsByOwner(securityUtils.getCurrentUserId()));
    }

    @PostMapping("/binary-connections")
    public ApiResponse<Map<String, Object>> connectBinaryClient(@RequestBody Map<String, Object> request) {
        requireAdmin();
        return ApiResponse.ok("掘进机 TCP 客户端已连接", binaryClientManager().connect(
                requiredHost(request), requiredPort(request), requiredDeviceId(request)));
    }

    @DeleteMapping("/binary-connections/{id}")
    public ApiResponse<Map<String, Object>> disconnectBinaryClient(@PathVariable String id) {
        requireAdmin();
        if (!binaryClientManager().disconnect(id)) {
            throw new RuntimeException("掘进机 TCP 连接不存在或已关闭");
        }
        return ApiResponse.ok("掘进机 TCP 客户端已断开", Map.of("id", id));
    }

    @GetMapping("/listeners")
    public ApiResponse<List<Map<String, Object>>> listeners() {
        requireAdmin();
        return ApiResponse.ok(tcpListenerManager == null ? List.of() : tcpListenerManager.listListeners());
    }

    @PostMapping("/listeners")
    public ApiResponse<Map<String, Object>> startListener(@RequestBody Map<String, Object> request) {
        requireAdmin();
        return ApiResponse.ok("TCP 监听已启动", listenerManager().start(requiredPort(request)));
    }

    @DeleteMapping("/listeners/{port}")
    public ApiResponse<Map<String, Object>> stopListener(@PathVariable int port) {
        requireAdmin();
        if (!listenerManager().stop(port)) {
            throw new RuntimeException("TCP 监听端口未启动");
        }
        return ApiResponse.ok("TCP 监听已停止", Map.of("port", port));
    }

    private void requireAdmin() {
        if (!securityUtils.hasRole("ADMIN")) {
            throw new RuntimeException("无权执行该操作");
        }
    }

    private TcpListenerManager listenerManager() {
        if (tcpListenerManager == null) {
            throw new RuntimeException("TCP 监听通道未启用");
        }
        return tcpListenerManager;
    }

    private TunnelingMachineTcpClientConnectionManager binaryClientManager() {
        if (tunnelingMachineTcpClientConnectionManager == null) {
            throw new RuntimeException("掘进机 TCP 客户端通道未启用");
        }
        return tunnelingMachineTcpClientConnectionManager;
    }

    private static String requiredHost(Map<String, Object> request) {
        Object host = request.get("host");
        if (!(host instanceof String value) || value.isBlank()) {
            throw new IllegalArgumentException("host 不能为空");
        }
        return value;
    }

    private static int requiredPort(Map<String, Object> request) {
        Object port = request.get("port");
        if (!(port instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())) {
            throw new IllegalArgumentException("port 必须是整数");
        }
        int value = number.intValue();
        if (value < 1 || value > 65_535) {
            throw new IllegalArgumentException("port 必须在 1 到 65535 之间");
        }
        return value;
    }

    private static String requiredDeviceId(Map<String, Object> request) {
        Object deviceId = request.get("deviceId");
        if (!(deviceId instanceof String value) || value.isBlank()) {
            throw new IllegalArgumentException("deviceId 不能为空");
        }
        return value;
    }

}
