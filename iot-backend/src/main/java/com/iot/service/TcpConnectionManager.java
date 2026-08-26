package com.iot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TCP 设备连接注册表 + 指令下发出站通道。
 *
 * <p>设备通过 TCP 长连接接入（JSON 行协议），认证成功后在此注册
 * deviceId → Channel 映射。平台下发指令时优先走 TCP 直连（设备在线），
 * 未在线则回退 MQTT（见 DataService.publishCommand）。
 *
 * <p>同时维护每个设备连接的建立时间，供"连接实例"页面展示。
 */
@Component
@ConditionalOnProperty(name = "app.tcp.enabled", havingValue = "true")
@Slf4j
public class TcpConnectionManager {

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Channel> channels = new ConcurrentHashMap<>();
    /** deviceId → 连接建立时间戳（毫秒） */
    private final ConcurrentHashMap<String, Long> connectedAt = new ConcurrentHashMap<>();
    /** 每条 TCP channel 的连接元数据；用于网关连接聚合展示与权限过滤。 */
    private final ConcurrentHashMap<Channel, ConnectionInfo> connections = new ConcurrentHashMap<>();

    public TcpConnectionManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 注册设备连接；若该设备已有旧连接则关闭旧连接（单设备单连接） */
    public void register(String deviceId, Channel channel) {
        Map<String, Long> owners = new HashMap<>();
        Long ownerId = channel.attr(TcpMessageHandler.ATTR_OWNER_ID).get();
        if (ownerId != null) owners.put(deviceId, ownerId);
        registerConnection(null, deviceId, Set.of(deviceId), owners, channel);
    }

    /** 注册一个承载多个设备的网关连接。 */
    public void registerGateway(String gatewayId, Map<String, Long> deviceOwners, Channel channel) {
        registerConnection(gatewayId, "gateway", deviceOwners.keySet(), deviceOwners, channel);
    }

    private void registerConnection(String gatewayId, String primaryDeviceId, Set<String> deviceIds,
                                    Map<String, Long> owners, Channel channel) {
        Set<Channel> oldChannels = ConcurrentHashMap.newKeySet();
        for (String deviceId : deviceIds) {
            Channel old = channels.put(deviceId, channel);
            if (old != null && old != channel) oldChannels.add(old);
            connectedAt.put(deviceId, System.currentTimeMillis());
        }
        connections.put(channel, new ConnectionInfo(gatewayId, deviceIds, owners, System.currentTimeMillis()));
        for (Channel old : oldChannels) {
            if (old.isActive()) {
            log.info("TCP device {} reconnected, closing old channel {}", primaryDeviceId, old.id().asShortText());
            old.close();
            }
        }
        log.info("TCP {} registered on channel {} ({} devices)",
                gatewayId != null ? "gateway " + gatewayId : "device " + primaryDeviceId,
                channel.id().asShortText(), deviceIds.size());
    }

    /** 连接断开时注销；仅当仍是该 channel 时才移除，避免误删新连接 */
    public void unregister(String deviceId, Channel channel) {
        ConnectionInfo info = connections.remove(channel);
        if (info != null) {
            info.deviceIds().forEach(id -> {
                if (channels.remove(id, channel)) connectedAt.remove(id);
            });
            log.debug("TCP connection {} unregistered ({} devices)", channel.id().asShortText(), info.deviceIds().size());
        } else if (channels.remove(deviceId, channel)) {
            connectedAt.remove(deviceId);
            log.debug("TCP device {} unregistered", deviceId);
        }
    }

    public boolean isOnline(String deviceId) {
        Channel ch = channels.get(deviceId);
        return ch != null && ch.isActive();
    }

    public int onlineCount() {
        return (int) channels.values().stream().filter(Channel::isActive).count();
    }

    /**
     * 获取全部在线连接实例快照（供管理页面展示）。
     * 返回按连接时间排序（新的在前）。
     */
    public List<Map<String, Object>> listConnections() {
        long now = System.currentTimeMillis();
        return connections.entrySet().stream()
                .filter(e -> e.getKey().isActive())
                .map(e -> toView(e.getKey(), e.getValue(), now, null))
                .sorted((a, b) -> Long.compare(
                        ((Number) b.get("connectedAt")).longValue(),
                        ((Number) a.get("connectedAt")).longValue()))
                .toList();
    }

    /** 按归属用户过滤连接实例（设备所有者的 ownerId） */
    public List<Map<String, Object>> listConnectionsByOwner(Long ownerId) {
        if (ownerId == null) return List.of();
        long now = System.currentTimeMillis();
        return connections.entrySet().stream()
                .filter(e -> e.getKey().isActive())
                .filter(e -> e.getValue().owners().containsValue(ownerId))
                .map(e -> toView(e.getKey(), e.getValue(), now, ownerId))
                .sorted((a, b) -> Long.compare(
                        ((Number) b.get("connectedAt")).longValue(),
                        ((Number) a.get("connectedAt")).longValue()))
                .toList();
    }

    /**
     * 强制断开指定设备的 TCP 连接（管理端操作）。
     *
     * @return true = 设备在线且连接已关闭；false = 设备不在线
     */
    public boolean disconnect(String deviceId) {
        Channel ch = channels.get(deviceId);
        if (ch == null || !ch.isActive()) {
            return false;
        }
        log.info("TCP connection for device {} forcibly closed by admin", deviceId);
        ch.close();
        return true;
    }

    private Map<String, Object> toView(Channel channel, ConnectionInfo info, long now, Long ownerId) {
        Set<String> visibleIds = info.deviceIds().stream()
                .filter(id -> ownerId == null || ownerId.equals(info.owners().get(id)))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Map<String, Object> view = new HashMap<>();
        view.put("gatewayId", info.gatewayId());
        view.put("deviceId", visibleIds.stream().findFirst().orElse(null));
        view.put("deviceIds", visibleIds);
        view.put("deviceCount", visibleIds.size());
        view.put("connectionMode", info.gatewayId() == null ? "DEVICE" : "GATEWAY");
        view.put("channelId", channel.id().asShortText());
        view.put("remoteAddress", channel.remoteAddress() != null ? channel.remoteAddress().toString() : "");
        view.put("connectedAt", info.connectedAt());
        view.put("onlineSeconds", Math.max(0, (now - info.connectedAt()) / 1000));
        return view;
    }

    private record ConnectionInfo(String gatewayId, Set<String> deviceIds,
                                  Map<String, Long> owners, long connectedAt) {
        private ConnectionInfo {
            deviceIds = Set.copyOf(deviceIds);
            owners = Collections.unmodifiableMap(new HashMap<>(owners));
        }
    }

    /**
     * 向指定设备下发指令。
     *
     * @return true = 设备 TCP 在线且消息已写入通道；false = 设备不在线或发送失败（调用方回退 MQTT）
     */
    public boolean publish(String deviceId, String command, Object params) {
        Channel ch = channels.get(deviceId);
        if (ch == null || !ch.isActive()) {
            return false;
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "type", "command",
                    "deviceId", deviceId,
                    "command", command,
                    "params", params != null ? params : Map.of(),
                    "timestamp", System.currentTimeMillis()
            ));
            ch.writeAndFlush(payload + "\n").addListener(future -> {
                if (!future.isSuccess()) {
                    log.warn("TCP command send failed for device {}: {}", deviceId, future.cause().getMessage());
                }
            });
            log.info("TCP command published: iot/{}/command -> {}", deviceId, payload);
            return true;
        } catch (Exception e) {
            log.warn("TCP command serialize failed for device {}: {}", deviceId, e.getMessage());
            return false;
        }
    }

    /** 内部辅助：向通道写 JSON 行（供认证回执等使用） */
    public static void writeJson(Channel channel, ObjectMapper mapper, Object payload) {
        try {
            channel.writeAndFlush(mapper.writeValueAsString(payload) + "\n");
        } catch (Exception e) {
            log.warn("TCP write failed: {}", e.getMessage());
        }
    }
}
