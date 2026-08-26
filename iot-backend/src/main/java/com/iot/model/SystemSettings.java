package com.iot.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // ---- 告警通知设置 ----
    @Column(name = "notify_email")
    @Builder.Default
    private boolean notifyEmail = true;

    @Column(name = "notify_sms")
    @Builder.Default
    private boolean notifySms = false;

    @Column(name = "notify_push")
    @Builder.Default
    private boolean notifyPush = true;

    @Column(name = "alert_threshold")
    @Builder.Default
    private int alertThreshold = 80;

    // ---- 数据设置 ----
    @Column(name = "auto_refresh")
    @Builder.Default
    private boolean autoRefresh = true;

    @Column(name = "refresh_interval")
    @Builder.Default
    private int refreshInterval = 5; // seconds

    @Column(name = "data_retention")
    @Builder.Default
    private int dataRetention = 30; // days

    // ---- 系统设置 ----
    @Column(length = 20)
    @Builder.Default
    private String theme = "light";

    @Column(name = "language", length = 10)
    @Builder.Default
    private String language = "zh-CN";

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
