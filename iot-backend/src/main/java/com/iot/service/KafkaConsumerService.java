package com.iot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.model.Device;
import com.iot.repository.DeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
    public void onDeviceTelemetry(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String deviceId = null;
        try {
            if (record.value() == null) {
                log.warn("Kafka telemetry message is null (key={} partition={} offset={}), dropping",
                        record.key(), record.partition(), record.offset());
                ack.acknowledge();
                return;
            }
            Map<String, Object> data = objectMapper.readValue(record.value(), Map.class);
            deviceId = (String) data.get("deviceId");
            String sensorId = (String) data.get("sensorId");
            String sensorType = (String) data.get("sensorType");
            Number rawValue = data.get("value") instanceof Number n ? n : null;
            String unit = (String) data.get("unit");

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
            // 失败不 ack：交给容器的默认重试/seek 语义重新投递，避免 at-most-once 静默丢数据
            log.error("Kafka telemetry consumer failed, redelivering: key={} partition={} offset={} deviceId={}",
                    record.key(), record.partition(), record.offset(), deviceId, e);
            throw new RuntimeException("Kafka telemetry processing failed (key=" + record.key() + ")", e);
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
            // 失败不 ack：交给容器默认重试/seek 语义，避免静默丢状态更新
            log.error("Kafka consumer failed to process status, redelivering", e);
            throw new RuntimeException("Kafka status processing failed", e);
        }
    }

    @KafkaListener(topics = "${app.kafka.topics.alert-events}", containerFactory = "kafkaListenerContainerFactory")
    public void onAlertEvent(String message, Acknowledgment ack) {
        try {
            log.info("Kafka alert event received: {}", message);
            ack.acknowledge();
        } catch (Exception e) {
            // 失败不 ack：交给容器默认重试/seek 语义，避免静默丢告警事件
            log.error("Kafka consumer failed to process alert, redelivering", e);
            throw new RuntimeException("Kafka alert processing failed", e);
        }
    }
}
