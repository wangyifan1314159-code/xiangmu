package com.iot.service;

import com.iot.model.AlertRecord;
import com.iot.repository.AlertRecordRepository;
import com.iot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MethaneAlertServiceTest {

    private AlertRecordRepository alertRecordRepository;
    private UserRepository userRepository;
    private MethaneAlertService methaneAlertService;

    @BeforeEach
    void setUp() {
        alertRecordRepository = mock(AlertRecordRepository.class);
        userRepository = mock(UserRepository.class);
        methaneAlertService = new MethaneAlertService(alertRecordRepository, userRepository);

        ReflectionTestUtils.setField(methaneAlertService, "thresholdPpm", 10000.0);
        ReflectionTestUtils.setField(methaneAlertService, "consecutiveFrames", 3);
    }

    @Test
    void doesNotAlertOnSingleSpike() {
        methaneAlertService.checkAndAlert("dev_01", "掘进机-01", 1L, 12000.0, "{\"raw\":\"frame1\"}");
        verify(alertRecordRepository, never()).save(any(AlertRecord.class));
    }

    @Test
    void doesNotAlertOnTwoConsecutiveSpikes() {
        methaneAlertService.checkAndAlert("dev_01", "掘进机-01", 1L, 12000.0, "{\"raw\":\"frame1\"}");
        methaneAlertService.checkAndAlert("dev_01", "掘进机-01", 1L, 11500.0, "{\"raw\":\"frame2\"}");
        verify(alertRecordRepository, never()).save(any(AlertRecord.class));
    }

    @Test
    void alertsOnThreeConsecutiveFramesOverThreshold() {
        methaneAlertService.checkAndAlert("dev_01", "掘进机-01", 1L, 12000.0, "{\"raw\":\"frame1\"}");
        methaneAlertService.checkAndAlert("dev_01", "掘进机-01", 1L, 11500.0, "{\"raw\":\"frame2\"}");
        methaneAlertService.checkAndAlert("dev_01", "掘进机-01", 1L, 13000.0, "{\"raw\":\"frame3\"}");

        ArgumentCaptor<AlertRecord> captor = ArgumentCaptor.forClass(AlertRecord.class);
        verify(alertRecordRepository, times(1)).save(captor.capture());

        AlertRecord saved = captor.getValue();
        assertEquals("dev_01", saved.getDeviceId());
        assertEquals("掘进机-01", saved.getDeviceName());
        assertEquals("CRITICAL", saved.getLevel());
        assertEquals("methane", saved.getSensorType());
        assertEquals(13000.0, saved.getSensorValue());
        assertEquals(10000.0, saved.getThresholdValue());
        assertEquals("{\"raw\":\"frame3\"}", saved.getRawFrame());
        assertEquals("TRIGGERED", saved.getStatus());
        assertTrue(saved.getTitle().contains("甲烷浓度超限"));
    }

    @Test
    void resetsCounterWhenNormalFrameArrives() {
        // Frame 1 & 2 over limit
        methaneAlertService.checkAndAlert("dev_01", "掘进机-01", 1L, 12000.0, "{\"raw\":\"frame1\"}");
        methaneAlertService.checkAndAlert("dev_01", "掘进机-01", 1L, 11500.0, "{\"raw\":\"frame2\"}");

        // Frame 3 normal -> resets counter
        methaneAlertService.checkAndAlert("dev_01", "掘进机-01", 1L, 5000.0, "{\"raw\":\"frame3_normal\"}");

        // Frame 4 & 5 over limit (only 2 in a row now) -> should not trigger alert yet
        methaneAlertService.checkAndAlert("dev_01", "掘进机-01", 1L, 12000.0, "{\"raw\":\"frame4\"}");
        methaneAlertService.checkAndAlert("dev_01", "掘进机-01", 1L, 12500.0, "{\"raw\":\"frame5\"}");

        verify(alertRecordRepository, never()).save(any(AlertRecord.class));

        // Frame 6 over limit (3rd consecutive) -> now triggers alert
        methaneAlertService.checkAndAlert("dev_01", "掘进机-01", 1L, 14000.0, "{\"raw\":\"frame6\"}");
        verify(alertRecordRepository, times(1)).save(any(AlertRecord.class));
    }
}
