package com.iot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.model.Device;
import com.iot.repository.DeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Kafka 消费者 — 从 Kafka 接收数据并走完整数据管线：
 * Device → HTTP/MQTT → Kafka → Consumer → DataService.fromKafka()
 *                                         → TDengine / PostgreSQL / Redis / Alerts
 */
@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@Slf4j
public class KafkaConsumerService {

    private final ObjectMapper objectMapper;
    private final DataService dataService;
    private final DeviceRepository deviceRepository;

    @Value("${app.kafka.topics.device-telemetry}")
    private String telemetryTopic;

    @Value("${app.kafka.topics.device-status}")
    private String statusTopic;

    public KafkaConsumerService(ObjectMapper objectMapper, DataService dataService,
                                 DeviceRepository deviceRepository) {
        this.objectMapper = objectMapper;
        this.dataService = dataService;
        this.deviceRepository = deviceRepository;
    }

    @SuppressWarnings("unchecked")
    @KafkaListener(topics = "${app.kafka.topics.device-telemetry}", containerFactory = "kafkaListenerContainerFactory")
    public void onDeviceTelemetry(String message, Acknowledgment ack) {
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            String deviceId = (String) data.get("deviceId");
            String sensorId = (String) data.get("sensorId");
            String sensorType = (String) data.get("sensorType");
            Number rawValue = data.get("value") instanceof Number n ? n : null;
            String unit = (String) data.get("unit");
            Number rawTimestamp = data.get("timestamp") instanceof Number n ? n : null;

            if (deviceId == null || sensorId == null || rawValue == null) {
                log.warn("Kafka telemetry message missing fields: deviceId={} sensorId={}", deviceId, sensorId);
                ack.acknowledge();
                return;
            }

            Long ownerId = deviceRepository.findByDeviceId(deviceId)
                    .map(Device::getOwnerId).orElse(null);
            if (ownerId == null) {
                // 设备未注册：丢弃，防止伪造任意 deviceId 注入脏数据
                log.warn("Kafka telemetry dropped: unknown deviceId={}", deviceId);
                ack.acknowledge();
                return;
            }

            // 走完整管线：TDengine → Redis → PostgreSQL → Alerts（不重复发 Kafka）
            dataService.fromKafka(deviceId, sensorId, sensorType,
                    rawValue.doubleValue(), unit, ownerId);

            log.debug("Kafka consumer processed: device={} sensor={} value={}", deviceId, sensorId, rawValue);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Kafka consumer failed to process telemetry", e);
            // 仍然 ack，避免消息堆积阻塞消费
            ack.acknowledge();
        }
    }

    @SuppressWarnings("unchecked")
    @KafkaListener(topics = "${app.kafka.topics.device-status}", containerFactory = "kafkaListenerContainerFactory")
    public void onDeviceStatus(String message, Acknowledgment ack) {
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            String deviceId = (String) data.get("deviceId");
            String status = (String) data.get("status");

            if (deviceId != null && status != null) {
                String normalized = status.toUpperCase();
                if (normalized.equals("ONLINE") || normalized.equals("OFFLINE") || normalized.equals("WARNING")) {
                    deviceRepository.findByDeviceId(deviceId).ifPresent(d -> {
                        d.setStatus(normalized);
                        d.setLastActive(java.time.LocalDateTime.now());
                        deviceRepository.save(d);
                    });
                    log.info("Kafka consumer: device {} status → {}", deviceId, status);
                } else {
                    log.warn("Kafka status dropped: invalid status={} device={}", status, deviceId);
                }
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Kafka consumer failed to process status", e);
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "${app.kafka.topics.alert-events}", containerFactory = "kafkaListenerContainerFactory")
    public void onAlertEvent(String message, Acknowledgment ack) {
        try {
            log.info("Kafka alert event received: {}", message);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Kafka consumer failed to process alert", e);
            ack.acknowledge();
        }
    }
}
