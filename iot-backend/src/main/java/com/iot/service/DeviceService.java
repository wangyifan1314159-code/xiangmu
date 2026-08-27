package com.iot.service;

import com.iot.config.SecurityUtils;
import com.iot.dto.DeviceRequest;
import com.iot.dto.SensorDTO;
import com.iot.model.Device;
import com.iot.model.Sensor;
import com.iot.repository.AlertRecordRepository;
import com.iot.repository.DeviceRepository;
import com.iot.repository.DataPointRepository;
import com.iot.repository.CommandLogRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final DataPointRepository dataPointRepository;
    private final CommandLogRepository commandLogRepository;
    private final AlertRecordRepository alertRecordRepository;
    private final SecurityUtils securityUtils;

    /** 启动时给所有缺失 API Key 的旧设备补发 */
    @PostConstruct
    @Transactional
    public void backfillApiKeys() {
        List<Device> all = deviceRepository.findAll();
        int count = 0;
        for (Device d : all) {
            if (d.getApiKey() == null || d.getApiKey().isEmpty()) {
                d.setApiKey(UUID.randomUUID().toString().replace("-", ""));
                deviceRepository.save(d);
                count++;
            }
        }
        if (count > 0) {
            log.info("Backfilled API keys for {} existing devices", count);
        }
    }

    private Long currentUserId() {
        Long id = securityUtils.getCurrentUserId();
        if (id == null) throw new RuntimeException("用户未登录");
        return id;
    }

    public List<Device> getAllDevices() {
        return deviceRepository.findAllByOwnerId(currentUserId());
    }

    public Device getDeviceById(String deviceId) {
        Long uid = currentUserId();
        return deviceRepository.findByDeviceIdAndOwnerId(deviceId, uid)
                .orElseThrow(() -> new RuntimeException("设备不存在或无权限: " + deviceId));
    }

    @Transactional
    public Device createDevice(DeviceRequest request) {
        Long uid = currentUserId();
        String did = (request.getDeviceId() != null && !request.getDeviceId().isBlank())
                ? request.getDeviceId() : "dev_" + UUID.randomUUID().toString().substring(0, 8);
        Device device = Device.builder()
                .deviceId(did)
                .apiKey(UUID.randomUUID().toString().replace("-", ""))
                .name(request.getName())
                .type(request.getType())
                .status(request.getStatus() != null ? request.getStatus() : "OFFLINE")
                .location(request.getLocation())
                .description(request.getDescription())
                .ownerId(uid)
                .sensors(new ArrayList<>())
                .build();

        if (request.getSensors() != null) {
            for (SensorDTO s : request.getSensors()) {
                Sensor sensor = buildSensor(s, device);
                device.addSensor(sensor);
            }
        }

        return deviceRepository.save(device);
    }

    @Transactional
    public Device updateDevice(String deviceId, DeviceRequest request) {
        Device device = getDeviceById(deviceId);
        device.setName(request.getName());
        device.setType(request.getType());
        if (request.getStatus() != null) device.setStatus(request.getStatus());
        device.setLocation(request.getLocation());
        device.setDescription(request.getDescription());

        if (request.getSensors() != null) {
            device.getSensors().clear();
            for (SensorDTO s : request.getSensors()) {
                Sensor sensor = buildSensor(s, device);
                // 不信任客户端传入的 sensor ID：服务端统一重新生成，
                // 避免跨设备主键冲突或 clear+重加 导致的 detach/merge 问题
                sensor.setId("s_" + UUID.randomUUID().toString().substring(0, 6));
                device.addSensor(sensor);
            }
        }

        return deviceRepository.save(device);
    }

    @Transactional
    public Device regenerateApiKey(String deviceId) {
        Device device = getDeviceById(deviceId);
        device.setApiKey(UUID.randomUUID().toString().replace("-", ""));
        return deviceRepository.save(device);
    }

    @Transactional
    public void deleteDevice(String deviceId) {
        // 先做归属校验：设备不存在或非本人所有时抛错，避免静默"删除成功"的假象
        Device device = getDeviceById(deviceId);
        Long ownerId = device.getOwnerId();
        dataPointRepository.deleteByDeviceIdAndOwnerId(deviceId, ownerId);
        commandLogRepository.deleteByDeviceIdAndOwnerId(deviceId, ownerId);
        alertRecordRepository.deleteByDeviceIdAndOwnerId(deviceId, ownerId);
        deviceRepository.deleteByDeviceIdAndOwnerId(deviceId, ownerId);
    }

    private static final java.util.Set<String> VALID_STATUS =
            java.util.Set.of("ONLINE", "OFFLINE", "WARNING");

    @Transactional
    public Device updateDeviceStatus(String deviceId, String status) {
        Device device = getDeviceById(deviceId);
        String normalized = status != null ? status.toUpperCase() : "";
        if (!VALID_STATUS.contains(normalized)) {
            throw new RuntimeException("非法设备状态: " + status + "（仅支持 ONLINE/OFFLINE/WARNING）");
        }
        device.setStatus(normalized);
        device.setLastActive(LocalDateTime.now());
        return deviceRepository.save(device);
    }

    public long countByStatus(String status) {
        return deviceRepository.countByStatusAndOwnerId(status, currentUserId());
    }

    public long countAll() {
        return deviceRepository.countByOwnerId(currentUserId());
    }

    private Sensor buildSensor(SensorDTO dto, Device device) {
        return Sensor.builder()
                .id(dto.getId() != null ? dto.getId() : "s_" + UUID.randomUUID().toString().substring(0, 6))
                .name(dto.getName())
                .type(dto.getType())
                .unit(dto.getUnit())
                .value(dto.getValue() != null ? dto.getValue() : 0.0)
                .minVal(dto.getMinVal() != null ? dto.getMinVal() : 0.0)
                .maxVal(dto.getMaxVal() != null ? dto.getMaxVal() : 100.0)
                .device(device)
                .build();
    }
}
