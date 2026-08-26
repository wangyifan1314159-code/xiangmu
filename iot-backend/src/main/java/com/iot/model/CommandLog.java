package com.iot.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "command_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommandLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = 50)
    private String deviceId;

    @Column(nullable = false, length = 100)
    private String command;

    @Column(length = 2000)
    private String params;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "SENT";

    @Column(length = 500)
    private String response;

    @Column(name = "sent_at")
    @Builder.Default
    private LocalDateTime sentAt = LocalDateTime.now();

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;
}
