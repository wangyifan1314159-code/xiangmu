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
public class TelemetryEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String deviceId;
    private String sensorId;
    private String sensorType;
    private Double value;
    private String unit;
    private Long timestamp; // 毫秒时间戳
    private Long ownerId;
    private String productType;
    private String tenantId;

    // 数据质量标记 (NORMAL / OUT_OF_RANGE / FLATLINE / JITTER)
    @Builder.Default
    private String qualityFlag = "NORMAL";
}