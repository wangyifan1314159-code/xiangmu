package com.iot.service;

import com.iot.config.SecurityUtils;
import com.iot.model.Device;
import com.iot.repository.AlertRecordRepository;
import com.iot.repository.CommandLogRepository;
import com.iot.repository.DataPointRepository;
import com.iot.repository.DeviceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DataPointRepository dataPointRepository;

    @Mock
    private CommandLogRepository commandLogRepository;

    @Mock
    private AlertRecordRepository alertRecordRepository;

    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private DeviceService deviceService;

    @Test
    void regenerateApiKeyReplacesKeyForOwnedDevice() {
        Device device = Device.builder()
                .deviceId("dev_test")
                .ownerId(7L)
                .apiKey("old-key")
                .build();
        when(securityUtils.getCurrentUserId()).thenReturn(7L);
        when(deviceRepository.findByDeviceIdAndOwnerId("dev_test", 7L)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Device updated = deviceService.regenerateApiKey("dev_test");

        assertNotEquals("old-key", updated.getApiKey());
        assertTrue(updated.getApiKey().matches("[0-9a-f]{32}"));
        verify(deviceRepository).save(device);
    }

    @Test
    void deleteDeviceRemovesItsHistory() {
        Device device = Device.builder()
                .deviceId("dev_test")
                .ownerId(7L)
                .build();
        when(securityUtils.getCurrentUserId()).thenReturn(7L);
        when(deviceRepository.findByDeviceIdAndOwnerId("dev_test", 7L)).thenReturn(Optional.of(device));

        deviceService.deleteDevice("dev_test");

        verify(dataPointRepository).deleteByDeviceIdAndOwnerId("dev_test", 7L);
        verify(commandLogRepository).deleteByDeviceIdAndOwnerId("dev_test", 7L);
        verify(alertRecordRepository).deleteByDeviceIdAndOwnerId("dev_test", 7L);
        verify(deviceRepository).deleteByDeviceIdAndOwnerId("dev_test", 7L);
    }
}
