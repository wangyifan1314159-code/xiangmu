package com.iot.service;

import com.iot.model.AlertRecord;
import com.iot.repository.AlertRecordRepository;
import com.iot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 甲烷浓度超限告警服务（内置规则，无需用户手工配置 AlertRule）
 *
 * 煤矿安全规程（GB 3836.1）：
 *   CH4 浓度 >= 1.0%（10000 ppm）时触发 CRITICAL 告警
 *
 * 采用 N 帧连续超限防抖策略：
 *   连续 consecutiveFrames 帧超限 → 触发一次告警 → 重置计数
 *   任意一帧未超限 → 计数清零（防止累计计数跨越非超限帧）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MethaneAlertService {

    private final AlertRecordRepository alertRecordRepository;
    private final UserRepository userRepository;

    @Autowired(required = false)
    private WebSocketPushService wsPush;

    /**
     * 甲烷报警阈值（ppm）。
     * 默认值 10000 ppm = 1.0%（煤矿安全规程通用上限）。
     * 可通过 application.yml: app.alert.methane.threshold-ppm 覆盖。
     */
    @Value("${app.alert.methane.threshold-ppm:10000.0}")
    private double thresholdPpm;

    /**
     * 连续超限帧数门槛，达到后才触发告警，防止单帧毛刺误报。
     * 默认 3 帧（约 3 个采样周期）。
     * 可通过 application.yml: app.alert.methane.consecutive-frames 覆盖。
     */
    @Value("${app.alert.methane.consecutive-frames:3}")
    private int consecutiveFrames;

    /** 每个设备的连续超限帧计数（线程安全，支持并发接收帧） */
    private final ConcurrentHashMap<String, AtomicInteger> overLimitCounter = new ConcurrentHashMap<>();

    /**
     * 检查甲烷浓度并在满足连续超限条件时触发告警。
     *
     * 调用者：TunnelingMachineTcpClientConnectionManager.handleFrame()
     *
     * @param deviceId        设备 ID（掘进机）
     * @param deviceName      设备名称（用于告警标题）
     * @param ownerId         设备归属用户 ID（用于权限过滤和 WebSocket 定向推送）
     * @param methaneValuePpm 甲烷浓度（ppm，0-10000）
     * @param rawFrameJson    原始数据帧摘要 JSON（用于追溯，写入 raw_frame 字段）
     */
    public void checkAndAlert(String deviceId, String deviceName, Long ownerId,
                               double methaneValuePpm, String rawFrameJson) {
        if (methaneValuePpm >= thresholdPpm) {
            // ── 超限：计数器 +1 ─────────────────────────────────────────────────
            AtomicInteger counter = overLimitCounter.computeIfAbsent(deviceId, k -> new AtomicInteger(0));
            int count = counter.incrementAndGet();
            log.info("甲烷超限计数: device={}, ppm={}, threshold={}, count={}/{}",
                    deviceId, methaneValuePpm, thresholdPpm, count, consecutiveFrames);

            if (count >= consecutiveFrames) {
                // ── 连续 N 帧超限：触发告警，重置计数防重复 ─────────────────
                counter.set(0);
                triggerAlert(deviceId, deviceName, ownerId, methaneValuePpm, rawFrameJson);
            }
        } else {
            // ── 未超限：清零计数（不允许跨越正常帧累计超限计数） ───────────────
            AtomicInteger counter = overLimitCounter.get(deviceId);
            if (counter != null && counter.get() > 0) {
                counter.set(0);
                log.debug("甲烷浓度恢复正常或单帧回落，清零计数器: device={}, ppm={}", deviceId, methaneValuePpm);
            }
        }
    }

    private void triggerAlert(String deviceId, String deviceName, Long ownerId,
                               double methaneValuePpm, String rawFrameJson) {
        String displayName = (deviceName != null && !deviceName.isBlank()) ? deviceName : deviceId;
        String title = String.format("[CRITICAL] 甲烷浓度超限 - %s: %.0f ppm (阈值: %.0f ppm = %.1f%%)",
                displayName, methaneValuePpm, thresholdPpm, thresholdPpm / 10000.0);

        String detail = String.format(
                "{\"deviceId\":\"%s\",\"deviceName\":\"%s\",\"sensorType\":\"methane\","
                + "\"value\":%.0f,\"threshold\":%.0f,\"level\":\"CRITICAL\",\"triggeredAt\":\"%s\"}",
                deviceId, displayName, methaneValuePpm, thresholdPpm,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        AlertRecord record = AlertRecord.builder()
                .deviceId(deviceId)
                .deviceName(displayName)
                .ruleId(null)            // 内置规则，无 AlertRule 记录
                .ruleName("甲烷超限自动规则")
                .level("CRITICAL")
                .title(title)
                .detail(detail)
                .sensorType("methane")
                .sensorValue(methaneValuePpm)
                .thresholdValue(thresholdPpm)
                .rawFrame(rawFrameJson)
                .ownerId(ownerId)
                .status("TRIGGERED")
                .triggeredAt(LocalDateTime.now())
                .build();

        alertRecordRepository.save(record);
        log.warn("【甲烷异常报警已生成并持久化】device={}, ppm={}, threshold={}, consecutiveFrames={}",
                deviceId, methaneValuePpm, thresholdPpm, consecutiveFrames);

        // WebSocket 实时推送告警给设备归属用户
        if (wsPush != null) {
            if (ownerId != null) {
                userRepository.findById(ownerId)
                        .ifPresent(owner -> wsPush.pushAlert(owner.getUsername(), deviceId, "CRITICAL", title));
            }
            wsPush.pushAlert("admin", deviceId, "CRITICAL", title);
        }
    }
}
