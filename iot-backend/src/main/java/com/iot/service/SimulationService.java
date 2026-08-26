package com.iot.service;

import com.iot.model.CommandLog;
import com.iot.model.Device;
import com.iot.model.Sensor;
import com.iot.repository.CommandLogRepository;
import com.iot.repository.DeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SimulationService {

    private final DeviceRepository deviceRepository;
    private final CommandLogRepository commandLogRepository;
    private final DataService dataService;

    public SimulationService(DeviceRepository deviceRepository,
                             CommandLogRepository commandLogRepository,
                             @org.springframework.beans.factory.annotation.Autowired(required = false) DataService dataService) {
        this.deviceRepository = deviceRepository;
        this.commandLogRepository = commandLogRepository;
        this.dataService = dataService;
    }

    private final Random random = new Random();
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> dataSimTask;
    private ScheduledFuture<?> commandSimTask;

    /**
     * Start data upload simulation - devices periodically send sensor data
     */
    public synchronized void startDataSimulation(int intervalSeconds) {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newScheduledThreadPool(2);
        }
        if (dataSimTask != null && !dataSimTask.isCancelled()) {
            return;
        }
        dataSimTask = scheduler.scheduleAtFixedRate(
                this::simulateDataUpload, 0, intervalSeconds, TimeUnit.SECONDS);
        log.info("Data upload simulation started, interval: {}s", intervalSeconds);
    }

    /**
     * Stop data upload simulation
     */
    public synchronized void stopDataSimulation() {
        if (dataSimTask != null) {
            dataSimTask.cancel(false);
            dataSimTask = null;
        }
        log.info("Data upload simulation stopped");
    }

    /**
     * Start command delivery simulation - platform sends commands, devices respond
     */
    public synchronized void startCommandSimulation(int intervalSeconds) {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newScheduledThreadPool(2);
        }
        if (commandSimTask != null && !commandSimTask.isCancelled()) {
            return;
        }
        commandSimTask = scheduler.scheduleAtFixedRate(
                this::simulateCommandDelivery, 5, intervalSeconds, TimeUnit.SECONDS);
        log.info("Command delivery simulation started, interval: {}s", intervalSeconds);
    }

    /**
     * Stop command delivery simulation
     */
    public synchronized void stopCommandSimulation() {
        if (commandSimTask != null) {
            commandSimTask.cancel(false);
            commandSimTask = null;
        }
        log.info("Command delivery simulation stopped");
    }

    /**
     * Stop all simulations
     */
    public synchronized void stopAll() {
        stopDataSimulation();
        stopCommandSimulation();
        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
        }
    }

    /**
     * Get simulation status
     */
    public Map<String, Object> getStatus() {
        return Map.of(
                "dataUploadRunning", dataSimTask != null && !dataSimTask.isCancelled(),
                "commandDeliveryRunning", commandSimTask != null && !commandSimTask.isCancelled()
        );
    }

    /**
     * Simulate one round of data upload: each online device reports sensor values with random variation
     */
    @Transactional
    public void simulateDataUpload() {
        List<Device> onlineDevices = deviceRepository.findAll().stream()
                .filter(d -> "ONLINE".equals(d.getStatus()))
                .toList();

        if (onlineDevices.isEmpty()) return;

        for (Device device : onlineDevices) {
            for (Sensor sensor : device.getSensors()) {
                double range = sensor.getMaxVal() - sensor.getMinVal();
                double variation = (random.nextDouble() - 0.5) * range * 0.05;
                double newValue = sensor.getValue() + variation;
                newValue = Math.max(sensor.getMinVal(), Math.min(sensor.getMaxVal(), newValue));
                sensor.setValue(Math.round(newValue * 100.0) / 100.0);

                // 走统一管线：TDengine → Kafka → Redis → 告警
                if (dataService != null) {
                    dataService.writeDataPoint(
                            device.getDeviceId(), sensor.getId(), sensor.getType(),
                            sensor.getValue(), sensor.getUnit(), device.getOwnerId());
                }
            }
            device.setLastActive(LocalDateTime.now());
            deviceRepository.save(device);
        }
        log.info("Simulated data upload: {} online devices reported", onlineDevices.size());
    }

    /**
     * Simulate command delivery: pick pending commands and generate device responses
     */
    @Transactional
    public void simulateCommandDelivery() {
        List<Device> onlineDevices = deviceRepository.findAll().stream()
                .filter(d -> "ONLINE".equals(d.getStatus()))
                .toList();

        if (onlineDevices.isEmpty()) return;

        Device device = onlineDevices.get(random.nextInt(onlineDevices.size()));
        String[] commands = {"getStatus", "getVersion", "checkHealth", "reportConfig", "syncTime"};
        String command = commands[random.nextInt(commands.length)];

        CommandLog cmdLog = CommandLog.builder()
                .deviceId(device.getDeviceId())
                .command(command)
                .status("EXECUTED")
                .response(generateMockResponse(command, device))
                .ownerId(device.getOwnerId())
                .sentAt(LocalDateTime.now())
                .respondedAt(LocalDateTime.now())
                .build();
        commandLogRepository.save(cmdLog);
        log.info("Simulated command delivery: {} -> {}", device.getDeviceId(), command);
    }

    /**
     * Manually trigger one round of data upload for all online devices
     */
    public Map<String, Object> triggerOnce() {
        simulateDataUpload();
        return Map.of("message", "已触发一次数据上发");
    }

    private String generateMockResponse(String command, Device device) {
        return switch (command) {
            case "getStatus" -> String.format("{\"status\":\"%s\",\"uptime\":%d}", device.getStatus(), random.nextInt(86400));
            case "getVersion" -> "{\"firmware\":\"v2.1.3\",\"hardware\":\"revB\"}";
            case "checkHealth" -> "{\"cpu\":%d%%, \"memory\":%d%%, \"healthy\":true}".formatted(20 + random.nextInt(40), 30 + random.nextInt(30));
            case "reportConfig" -> "{\"interval\":" + (5 + random.nextInt(60)) + ", \"threshold\":" + (20 + random.nextInt(60)) + "}";
            case "syncTime" -> "{\"time\":\"" + LocalDateTime.now() + "\", \"synced\":true}";
            default -> "{\"result\":\"ok\"}";
        };
    }
}
