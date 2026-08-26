package com.iot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * WebSocket 实时推送服务
 * 向所有已连接的前端客户端推送设备数据更新、告警等
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketPushService {

    private final SimpMessagingTemplate messagingTemplate;

    /** 推送设备传感器数据更新 */
    public void pushDeviceData(String deviceId, String sensorId, double value, String unit) {
        messagingTemplate.convertAndSend("/topic/device/" + deviceId, Map.of(
                "type", "data",
                "deviceId", deviceId,
                "sensorId", sensorId,
                "value", value,
                "unit", unit != null ? unit : "",
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    /** 推送设备状态变更 */
    public void pushDeviceStatus(String deviceId, String status) {
        messagingTemplate.convertAndSend("/topic/device/" + deviceId, Map.of(
                "type", "status",
                "deviceId", deviceId,
                "status", status,
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    /** 推送告警（定向推送给设备归属用户，避免跨用户告警泄露） */
    public void pushAlert(String username, String deviceId, String level, String title) {
        // convertAndSendToUser 自动路由到 /user/{username}/queue/alert
        messagingTemplate.convertAndSendToUser(username, "/queue/alert", Map.of(
                "deviceId", deviceId,
                "level", level,
                "title", title,
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    /** 推送设备列表摘要（设备数量变化时） */
    public void pushDeviceSummary(long total, long online, long offline, long warning) {
        messagingTemplate.convertAndSend("/topic/summary", Map.of(
                "total", total,
                "online", online,
                "offline", offline,
                "warning", warning
        ));
    }
}
