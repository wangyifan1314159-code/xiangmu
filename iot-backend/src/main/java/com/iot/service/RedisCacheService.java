package com.iot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true", matchIfMissing = false)
public class RedisCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    // ========== 设备在线状态 ==========

    public void setDeviceOnline(String deviceId, boolean online) {
        String key = "device:online:" + deviceId;
        if (online) {
            redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(120));
        } else {
            redisTemplate.delete(key);
        }
    }

    public boolean isDeviceOnline(String deviceId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("device:online:" + deviceId));
    }

    // ========== 设备最新数据快照 ==========

    public void setDeviceShadow(String deviceId, Map<String, Object> shadow) {
        redisTemplate.opsForHash().putAll("device:shadow:" + deviceId, shadow);
    }

    public Map<Object, Object> getDeviceShadow(String deviceId) {
        return redisTemplate.opsForHash().entries("device:shadow:" + deviceId);
    }

    // ========== 告警防抖 ==========

    /**
     * @return true = 可以触发告警, false = 防抖窗口内已触发
     */
    public boolean checkAlertDebounce(Long deviceId, Long ruleId, int debounceSeconds) {
        String key = "alert:debounce:" + deviceId + ":" + ruleId;
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(debounceSeconds)));
    }

    // ========== 通用缓存 ==========

    public void setCache(String key, Object value, long ttlSeconds) {
        redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    public <T> T getCache(String key, Class<T> clazz) {
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? (T) value : null;
    }

    public void deleteCache(String key) {
        redisTemplate.delete(key);
    }

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
