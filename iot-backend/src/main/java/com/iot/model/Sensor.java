package com.iot.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sensors")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sensor {

    @Id
    @Column(length = 50)
    private String id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(length = 20)
    private String unit;

    @Column(nullable = false)
    @Builder.Default
    private Double value = 0.0;

    @Column(name = "min_val")
    @Builder.Default
    private Double minVal = 0.0;

    @Column(name = "max_val")
    @Builder.Default
    private Double maxVal = 100.0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    @JsonIgnore
    private Device device;
}
