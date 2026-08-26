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
public class AlertEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String alertId;
    private String deviceId;
    private String sensorId;
    private String sensorType;
    private String ruleName;
    private String severity; // INFO / WARNING / CRITICAL
    private String message;
    private Double triggerValue;
    private Double threshold;
    private Long timestamp;
    private Long ownerId;
}