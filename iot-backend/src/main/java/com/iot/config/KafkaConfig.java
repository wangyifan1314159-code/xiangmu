package com.iot.config;
// 在 Spring Boot 应用启动时，自动创建 Kafka Topic（如果不存在）并完成基础配置管理，同时通过配置开关控制是否启用。
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaConfig {

    @Value("${app.kafka.topics.device-raw}")
    private String deviceRawTopic;

    @Value("${app.kafka.topics.device-telemetry}")
    private String deviceTelemetryTopic;

    @Value("${app.kafka.topics.device-status}")
    private String deviceStatusTopic;

    @Value("${app.kafka.topics.alert-events}")
    private String alertEventsTopic;

    @Bean
    public NewTopic deviceRawTopic() {
        return TopicBuilder.name(deviceRawTopic)
                .partitions(32)
                .replicas(1)
                .config("retention.ms", "86400000") // 24h
                .build();
    }

    @Bean
    public NewTopic deviceTelemetryTopic() {
        return TopicBuilder.name(deviceTelemetryTopic)
                .partitions(16)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic deviceStatusTopic() {
        return TopicBuilder.name(deviceStatusTopic)
                .partitions(8)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic alertEventsTopic() {
        return TopicBuilder.name(alertEventsTopic)
                .partitions(8)
                .replicas(1)
                .build();
    }
}
