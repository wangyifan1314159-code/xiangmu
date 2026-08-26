package com.iot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.model.Device;
import com.iot.repository.DeviceRepository;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class TcpMessageHandlerTest {

    private DeviceRepository deviceRepository;
    private DataService dataService;
    private TcpConnectionManager connectionManager;
    private TcpMessageHandler handler;

    @BeforeEach
    void setUp() {
        deviceRepository = mock(DeviceRepository.class);
        dataService = mock(DataService.class);
        connectionManager = mock(TcpConnectionManager.class);
        handler = new TcpMessageHandler(new ObjectMapper(), deviceRepository, dataService, connectionManager);
        ReflectionTestUtils.setField(handler, "gatewayAccessToken", "test-token");
        ReflectionTestUtils.setField(handler, "maxDevicesPerGateway", 256);
    }

    @Test
    void gatewayAuthAuthorizesMultipleDevicesAndTelemetryScope() {
        when(deviceRepository.findByDeviceId("dev-1")).thenReturn(Optional.of(device("dev-1", 1L)));
        when(deviceRepository.findByDeviceId("dev-2")).thenReturn(Optional.of(device("dev-2", 1L)));

        EmbeddedChannel channel = new EmbeddedChannel(handler);
        channel.writeInbound("{\"type\":\"gateway_auth\",\"accessToken\":\"test-token\",\"gatewayId\":\"gw-1\",\"deviceIds\":[\"dev-1\",\"dev-2\"]}");
        String authResult = (String) channel.readOutbound();
        assertTrue(authResult.contains("\"success\":true"));
        verify(connectionManager).registerGateway(eq("gw-1"), argThat(owners -> owners.keySet().containsAll(java.util.Set.of("dev-1", "dev-2"))), any());

        channel.writeInbound("{\"type\":\"telemetry\",\"deviceId\":\"dev-1\",\"sensorId\":\"s-1\",\"value\":12.5}");
        verify(dataService).writeDataPoint("dev-1", "s-1", null, 12.5, null, 1L);

        channel.writeInbound("{\"type\":\"telemetry\",\"deviceId\":\"dev-other\",\"sensorId\":\"s-1\",\"value\":12.5}");
        verifyNoMoreInteractions(dataService);
    }

    @Test
    void gatewayAuthRejectsUnknownDevice() {
        when(deviceRepository.findByDeviceId("missing")).thenReturn(Optional.empty());
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound("{\"type\":\"gateway_auth\",\"accessToken\":\"test-token\",\"gatewayId\":\"gw-1\",\"deviceIds\":[\"missing\"]}");
        String result = (String) channel.readOutbound();

        assertTrue(result.contains("\"success\":false"));
        verifyNoInteractions(connectionManager);
    }

    private Device device(String id, Long ownerId) {
        return Device.builder().deviceId(id).ownerId(ownerId).apiKey("key").build();
    }
}
