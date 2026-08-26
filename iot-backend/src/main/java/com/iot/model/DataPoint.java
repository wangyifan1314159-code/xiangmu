package com.iot.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "data_points", indexes = {
        @Index(name = "idx_dp_device_ts", columnList = "device_id, timestamp"),
        @Index(name = "idx_dp_device_sensor_ts", columnList = "device_id, sensor_id, timestamp")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = 50)
    private String deviceId;

    @Column(name = "sensor_id", nullable = false, length = 50)
    private String sensorId;

    @Column(nullable = false)
    private Double value;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
