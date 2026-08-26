package com.iot.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceMetricAgg implements Serializable {
    private static final long serialVersionUID = 1L;

    private String deviceId;
    private String sensorId;
    private String sensorType;
    private String windowGranularity; // 1m / 5m / 1h / 1d
    private Long windowStartTime;
    private Long windowEndTime;
    private Double avgValue;
    private Double maxValue;
    private Double minValue;
    private Long sampleCount;
    private Long ownerId;
}