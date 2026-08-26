package com.iot.config;
// MQTT 配置类
import com.iot.service.MqttMessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@ConditionalOnProperty(name = "app.mqtt.enabled", havingValue = "true")
@Slf4j
public class MqttConfig {

    @Value("${app.mqtt.broker-url}")
    private String brokerUrl;

    @Value("${app.mqtt.client-id}")
    private String clientId;

    @Value("${app.mqtt.username:}")
    private String username;

    @Value("${app.mqtt.password:}")
    private String password;

    @Value("${app.mqtt.topics.telemetry:iot/+/telemetry}")
    private String telemetryTopic;

    @Value("${app.mqtt.topics.status:iot/+/status}")
    private String statusTopic;

    @Value("${app.mqtt.topics.command:iot/+/command}")
    private String commandTopic;

    private final MqttMessageHandler handler;

    /**
     * MQTT 消息处理线程池：Paho 回调只做入队，
     * 避免数据库慢查询阻塞 Paho 单回调线程导致消息堆积延时
     */
    private final ExecutorService mqttExecutor;
    private final AtomicInteger workerSeq = new AtomicInteger();

    {
        mqttExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "mqtt-handler-" + workerSeq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    public MqttConfig(MqttMessageHandler handler) {
        this.handler = handler;
    }

    @PreDestroy
    public void shutdownExecutor() {
        mqttExecutor.shutdown();
    }

    @Bean
    public MqttClient mqttClient() {
        log.info("MQTT creating client, brokerUrl={}, clientId={}", brokerUrl, clientId);

        final MqttClient client;
        try {
            client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
        } catch (MqttException e) {
            log.error("MQTT client creation failed, MQTT disabled. brokerUrl={}, error={}",
                    brokerUrl, e.getMessage());
            return null;
        }

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);

        if (username != null && !username.isBlank()) {
            options.setUserName(username);
            options.setPassword(password != null ? password.toCharArray() : new char[0]);
        }

        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                log.info("MQTT connect complete, reconnect={}, serverURI={}", reconnect, serverURI);
                subscribeTopics(client);
            }

            @Override
            public void connectionLost(Throwable cause) {
                log.warn("MQTT connection lost: {}", cause == null ? "unknown" : cause.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                mqttExecutor.submit(() -> handler.handleMessage(topic, payload));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });

        // Paho 的 automaticReconnect 只在首次连接成功后生效；
        // 首次连接失败时由后台调度任务持续重试，成功后自动停止
        ScheduledExecutorService retryScheduler =
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "mqtt-initial-connect-retry");
                    t.setDaemon(true);
                    return t;
                });
        AtomicInteger attempts = new AtomicInteger();
        retryScheduler.scheduleWithFixedDelay(() -> {
            if (client.isConnected()) {
                retryScheduler.shutdownNow();
                return;
            }
            try {
                client.connect(options);
                log.info("MQTT connected to {} after {} attempt(s)", brokerUrl, attempts.get() + 1);
                retryScheduler.shutdownNow();
            } catch (MqttException e) {
                int n = attempts.incrementAndGet();
                if (n == 1 || n % 10 == 0) {
                    log.warn("MQTT initial connect failed (attempt #{}), will retry in 15s. "
                            + "brokerUrl={}, reasonCode={}, message={}",
                            n, brokerUrl, e.getReasonCode(), e.getMessage());
                }
            }
        }, 0, 15, TimeUnit.SECONDS);

        // 立即返回未连接的客户端，调用方通过 isConnected() 判断可用性，
        // 避免启动阶段 broker 未就绪导致 MQTT 通道整个进程生命周期失效
        return client;
    }

    private void subscribeTopics(MqttClient client) {
        try {
            if (client.isConnected()) {
                client.subscribe(telemetryTopic, 1);
                client.subscribe(statusTopic, 1);
                client.subscribe(commandTopic, 1);
                log.info("MQTT subscribed topics: {}, {}, {}", telemetryTopic, statusTopic, commandTopic);
            } else {
                log.warn("MQTT subscribe skipped because client is not connected");
            }
        } catch (MqttException e) {
            log.warn("MQTT subscribe failed, reasonCode={}, message={}",
                    e.getReasonCode(), e.getMessage());
        }
    }
}