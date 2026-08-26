package com.iot.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "alert_rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 规则归属用户（多租户隔离，代码层强制校验） */
    @Column(name = "owner_id")
    private Long ownerId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "product_type", length = 50)
    private String productType;

    @Column(name = "device_id", length = 50)
    private String deviceId; // null = 适用于该产品下所有设备

    @Column(name = "sensor_type", length = 50)
    private String sensorType;

    @Column(nullable = false, length = 512)
    private String conditionExpr; // 条件表达式，如 "value > 80"

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String level = "WARNING"; // INFO / WARNING / CRITICAL

    @Column(name = "debounce_seconds")
    @Builder.Default
    private int debounceSeconds = 60;

    @Builder.Default
    private boolean enabled = true;

    @Column(name = "notify_email")
    private boolean notifyEmail;

    @Column(name = "notify_sms")
    private boolean notifySms;

    @Column(name = "notify_push")
    private boolean notifyPush;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
