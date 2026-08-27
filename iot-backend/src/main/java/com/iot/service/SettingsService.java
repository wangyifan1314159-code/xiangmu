package com.iot.service;

import com.iot.model.SystemSettings;
import com.iot.repository.SystemSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SystemSettingsRepository settingsRepository;

    public SystemSettings getSettings(Long userId) {
        return settingsRepository.findByUserId(userId)
                .orElseGet(() -> createDefault(userId));
    }

    @Transactional
    public SystemSettings updateSettings(Long userId, Map<String, Object> updates) {
        SystemSettings settings = getSettings(userId);

        // 告警通知设置
        if (updates.containsKey("notifications")) {
            Map<String, Object> notif = requireMap(updates.get("notifications"), "notifications");
            if (notif.containsKey("email")) { Boolean v = asBoolean(notif.get("email")); if (v != null) settings.setNotifyEmail(v); }
            if (notif.containsKey("sms")) { Boolean v = asBoolean(notif.get("sms")); if (v != null) settings.setNotifySms(v); }
            if (notif.containsKey("push")) { Boolean v = asBoolean(notif.get("push")); if (v != null) settings.setNotifyPush(v); }
            if (notif.containsKey("alertThreshold")) { Integer v = asBoundedInt(notif.get("alertThreshold"), "alertThreshold", 0, 100); if (v != null) settings.setAlertThreshold(v); }
        }

        // 数据设置
        if (updates.containsKey("data")) {
            Map<String, Object> data = requireMap(updates.get("data"), "data");
            if (data.containsKey("autoRefresh")) { Boolean v = asBoolean(data.get("autoRefresh")); if (v != null) settings.setAutoRefresh(v); }
            if (data.containsKey("refreshInterval")) { Integer v = asBoundedInt(data.get("refreshInterval"), "refreshInterval", 1, 60); if (v != null) settings.setRefreshInterval(v); }
            if (data.containsKey("dataRetention")) { Integer v = asBoundedInt(data.get("dataRetention"), "dataRetention", 7, 365); if (v != null) settings.setDataRetention(v); }
        }

        // 系统设置
        if (updates.containsKey("system")) {
            Map<String, Object> sys = requireMap(updates.get("system"), "system");
            if (sys.containsKey("theme") && sys.get("theme") instanceof String s) settings.setTheme(s);
            if (sys.containsKey("language") && sys.get("language") instanceof String s) settings.setLanguage(s);
        }

        settings.setUpdatedAt(LocalDateTime.now());
        return settingsRepository.save(settings);
    }

    /** 顶层设置项必须是对象，否则抛出明确的 400 而非 ClassCastException 500 */
    private static Map<String, Object> requireMap(Object value, String field) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("设置项 " + field + " 必须是对象");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) value;
        return map;
    }

    /** 类型安全转换：非 Boolean/Number 一律视为无效并跳过，避免 ClassCastException 导致 500 */
    private Boolean asBoolean(Object v) {
        return v instanceof Boolean b ? b : null;
    }

    /** 类型 + 范围校验：非法类型或越界值抛出明确 400（与前端输入控件范围对齐） */
    private static Integer asBoundedInt(Object v, String field, int min, int max) {
        if (!(v instanceof Number n)) {
            throw new IllegalArgumentException("设置项 " + field + " 必须是整数");
        }
        int value = n.intValue();
        if (value < min || value > max) {
            throw new IllegalArgumentException("设置项 " + field + " 必须在 " + min + " 到 " + max + " 之间");
        }
        return value;
    }

    private SystemSettings createDefault(Long userId) {
        return settingsRepository.save(SystemSettings.builder().userId(userId).build());
    }
}
