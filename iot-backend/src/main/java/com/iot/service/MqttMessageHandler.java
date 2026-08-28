package com.iot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.model.Device;
import com.iot.repository.DeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * MQTT 消息处理器：解析设备上报的遥测和状态数据，
 * 转发到 DataService 统一管线（TDengine → Kafka → Redis → 告警）
 */
@Service
@ConditionalOnProperty(name = "app.mqtt.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class MqttMessageHandler {

    private final ObjectMapper objectMapper;
    private final DataService dataService;
    private final DeviceRepository deviceRepository;

    /**
     * 命令信道认证 token（app.mqtt.command-token）：
     * 非空时命令消息 payload 必须携带匹配的 commandToken/token 字段，验证命令来源为平台自身；
     * 留空（未配置）时拒绝所有 MQTT 命令消息（fail-closed）
     */
    @Value("${app.mqtt.command-token:}")
    private String commandToken;

    public MqttMessageHandler(ObjectMapper objectMapper, DataService dataService,
                              DeviceRepository deviceRepository) {
        this.objectMapper = objectMapper;
        this.dataService = dataService;
        this.deviceRepository = deviceRepository;
    }

    /**
     * 处理 MQTT 消息，根据 topic 类型分发
     * @param topic   MQTT topic (如 iot/dev_xxx/telemetry)
     * @param payload JSON 字符串
     */
    @SuppressWarnings("unchecked")
    public void handleMessage(String topic, String payload) {
        try {
            String[] parts = topic.split("/");
            if (parts.length < 3) return;
            String deviceId = parts[1];  // iot/{deviceId}/telemetry
            String type = parts[2];       // telemetry | status

            Map<String, Object> data = objectMapper.readValue(payload, Map.class);

            // 设备必须已注册：MQTT 通道无登录上下文，未知设备的上报/命令一律丢弃，
            // 防止伪造任意 deviceId 注入脏数据或向不存在的执行器下发命令
            Optional<Device> deviceOpt = deviceRepository.findByDeviceId(deviceId);
            if (deviceOpt.isEmpty()) {
                log.warn("MQTT message dropped: unknown deviceId={} topic={}", deviceId, topic);
                return;
            }
            Device device = deviceOpt.get();

            switch (type) {
                case "telemetry" -> {
                    String sensorId = (String) data.get("sensorId");
                    Double value = data.get("value") instanceof Number n ? n.doubleValue() : null;
                    if (sensorId != null && value != null) {
                        dataService.writeDataPoint(deviceId, sensorId, null, value, null, device.getOwnerId());
                        log.debug("MQTT telemetry: {} sensor={} value={}", deviceId, sensorId, value);
                    }
                }
                case "status" -> {
                    String status = (String) data.get("status");
                    if (status != null && isValidStatus(status)) {
                        device.setStatus(status.toUpperCase());
                        deviceRepository.save(device);
                        log.info("MQTT status: {} → {}", deviceId, status);
                    } else {
                        log.warn("MQTT status dropped: invalid status={} device={}", status, deviceId);
                    }
                }
                case "command" -> {
                    // 命令信道认证：防止能连上 broker 的任意客户端伪造平台命令下发执行器
                    // fail-closed：未配置 token 时一律丢弃；配置了 token 时 payload 必须携带匹配的 commandToken/token 字段
                    if (commandToken == null || commandToken.isBlank()) {
                        log.warn("MQTT command dropped: command-token not configured, deviceId={}", deviceId);
                        return;
                    }
                    String command = (String) data.get("command");
                    String actuator = (String) data.get("actuator");
                    Object token = data.get("commandToken") != null ? data.get("commandToken") : data.get("token");
                    if (command != null && actuator != null) {
                        if (!commandToken.equals(token)) {
                            log.warn("MQTT command dropped: invalid or missing command token, deviceId={}", deviceId);
                            return;
                        }
                        // 设备已注册校验、命令来源 token 校验均通过后，走内部受信通道（MQTT 回调线程无登录上下文）
                        dataService.sendCommandFromSystem(deviceId, command,
                                java.util.Map.of("actuator", actuator));
                        log.info("MQTT command: {} → {} {}", deviceId, actuator, command);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("MQTT message parse failed: topic={}, error={}", topic, e.getMessage());
        }
    }

    private boolean isValidStatus(String status) {
        if (status == null) return false;
        return switch (status.toUpperCase()) {
            case "ONLINE", "OFFLINE", "WARNING" -> true;
            default -> false;
        };
    }
}
