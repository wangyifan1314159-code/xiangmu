package com.iot.dto;

import lombok.Data;

@Data
public class SensorDTO {
    private String id;
    private String name;
    private String type;
    private String unit;
    private Double value;
    private Double minVal;
    private Double maxVal;
}
