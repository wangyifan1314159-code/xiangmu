package com.iot.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "devices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", unique = true, nullable = false, length = 50)
    private String deviceId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "OFFLINE";

    @Column(length = 100)
    private String location;

    @Column(length = 500)
    private String description;

    @Column(name = "last_active")
    @Builder.Default
    private LocalDateTime lastActive = LocalDateTime.now();

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "api_key", unique = true, length = 64)
    private String apiKey;

    @OneToMany(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<Sensor> sensors = new ArrayList<>();

    public void addSensor(Sensor sensor) {
        sensors.add(sensor);
        sensor.setDevice(this);
    }

    public void removeSensor(Sensor sensor) {
        sensors.remove(sensor);
        sensor.setDevice(null);
    }
}
