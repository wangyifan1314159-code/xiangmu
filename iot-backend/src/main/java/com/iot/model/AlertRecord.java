package com.iot.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 将java对象与数据库表alert_records建立映射关系
@Entity
@Table(name = "alert_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = 50)
    private String deviceId;

    @Column(name = "device_name", length = 128)
    private String deviceName;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "rule_name", length = 128)
    private String ruleName;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String level = "WARNING"; // INFO / WARNING / CRITICAL

    @Column(nullable = false, length = 256)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String detail; // JSON 格式的详细信息

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "TRIGGERED"; // TRIGGERED / ACKNOWLEDGED / RESOLVED

    @Column(name = "triggered_at")
    @Builder.Default
    private LocalDateTime triggeredAt = LocalDateTime.now();

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "owner_id")
    private Long ownerId;
}
