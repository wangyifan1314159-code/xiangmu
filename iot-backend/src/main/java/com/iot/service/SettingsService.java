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
            @SuppressWarnings("unchecked")
            Map<String, Object> notif = (Map<String, Object>) updates.get("notifications");
            if (notif.containsKey("email")) { Boolean v = asBoolean(notif.get("email")); if (v != null) settings.setNotifyEmail(v); }
            if (notif.containsKey("sms")) { Boolean v = asBoolean(notif.get("sms")); if (v != null) settings.setNotifySms(v); }
            if (notif.containsKey("push")) { Boolean v = asBoolean(notif.get("push")); if (v != null) settings.setNotifyPush(v); }
            if (notif.containsKey("alertThreshold")) { Integer v = asInteger(notif.get("alertThreshold")); if (v != null) settings.setAlertThreshold(v); }
        }

        // 数据设置
        if (updates.containsKey("data")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) updates.get("data");
            if (data.containsKey("autoRefresh")) { Boolean v = asBoolean(data.get("autoRefresh")); if (v != null) settings.setAutoRefresh(v); }
            if (data.containsKey("refreshInterval")) { Integer v = asInteger(data.get("refreshInterval")); if (v != null) settings.setRefreshInterval(v); }
            if (data.containsKey("dataRetention")) { Integer v = asInteger(data.get("dataRetention")); if (v != null) settings.setDataRetention(v); }
        }

        // 系统设置
        if (updates.containsKey("system")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sys = (Map<String, Object>) updates.get("system");
            if (sys.containsKey("theme") && sys.get("theme") instanceof String s) settings.setTheme(s);
            if (sys.containsKey("language") && sys.get("language") instanceof String s) settings.setLanguage(s);
        }

        settings.setUpdatedAt(LocalDateTime.now());
        return settingsRepository.save(settings);
    }

    /** 类型安全转换：非 Boolean/Number 一律视为无效并跳过，避免 ClassCastException 导致 500 */
    private Boolean asBoolean(Object v) {
        return v instanceof Boolean b ? b : null;
    }

    private Integer asInteger(Object v) {
        if (v instanceof Number n) return n.intValue();
        return null;
    }

    private SystemSettings createDefault(Long userId) {
        return settingsRepository.save(SystemSettings.builder().userId(userId).build());
    }
}
