package com.iot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class DeviceRequest {
    private String deviceId;

    @NotBlank(message = "设备名称不能为空")
    private String name;

    @NotBlank(message = "设备类型不能为空")
    private String type;

    private String status = "OFFLINE";

    private String location;

    private String description;

    private List<SensorDTO> sensors;
}
