package com.iot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.device-raw}")
    private String deviceRawTopic;

    @Value("${app.kafka.topics.device-telemetry}")
    private String deviceTelemetryTopic;

    @Value("${app.kafka.topics.device-status}")
    private String deviceStatusTopic;

    @Value("${app.kafka.topics.alert-events}")
    private String alertEventsTopic;

    public void sendDeviceRaw(String deviceId, Map<String, Object> data) {
        send(deviceRawTopic, deviceId, data);
    }

    public void sendDeviceTelemetry(String deviceId, Map<String, Object> data) {
        send(deviceTelemetryTopic, deviceId, data);
    }

    public void sendDeviceStatus(String deviceId, String status) {
        send(deviceStatusTopic, deviceId, Map.of("deviceId", deviceId, "status", status, "timestamp", System.currentTimeMillis()));
    }

    public void sendAlertEvent(String alertJson) {
        send(alertEventsTopic, null, alertJson);
    }

    private void send(String topic, String key, Object payload) {
        try {
            String value = payload instanceof String ? (String) payload : objectMapper.writeValueAsString(payload);
            CompletableFuture<SendResult<String, String>> future = key != null
                    ? kafkaTemplate.send(topic, key, value)
                    : kafkaTemplate.send(topic, value);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Kafka send failed: topic={}, key={}", topic, key, ex);
                } else {
                    log.debug("Kafka send ok: topic={}, partition={}, offset={}",
                            topic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                }
            });
        } catch (JsonProcessingException e) {
            log.error("Kafka serialization error: topic={}", topic, e);
        }
    }
}
