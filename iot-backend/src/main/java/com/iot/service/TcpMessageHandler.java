package com.iot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.model.Device;
import com.iot.repository.DeviceRepository;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;

/**
 * TCP 设备接入协议处理器（JSON 行协议，UTF-8，以 \n 结尾，单行一个 JSON）。
 *
 * <p>帧格式（设备 → 平台）：
 * <pre>
 * 1) 鉴权（连接建立后必须先发，30 秒内未完成将被断开）：
 *    {"type":"auth","deviceId":"dev_xxx","apiKey":"<设备API Key>"}
 *    成功回执：{"type":"auth_result","success":true,"deviceId":"dev_xxx"}
 * 2) 遥测上报：
 *    {"type":"telemetry","sensorId":"s_001","value":25.5,"sensorType":"temperature","unit":"C"}
 * 3) 状态上报：
 *    {"type":"status","status":"ONLINE"}
 * 4) 指令执行结果回执（可选）：
 *    {"type":"command_result","command":"on","success":true,"message":"executed"}
 * </pre>
 *
 * <p>平台 → 设备（指令下发，见 TcpConnectionManager.publish）：
 * <pre>
 * {"type":"command","command":"on","params":{...},"timestamp":...}
 * </pre>
 */
@Component
@Slf4j
@ChannelHandler.Sharable
@ConditionalOnProperty(name = "app.tcp.enabled", havingValue = "true")
public class TcpMessageHandler extends SimpleChannelInboundHandler<String> {

    /** 认证通过后绑定到 channel 的设备ID */
    public static final AttributeKey<String> ATTR_DEVICE_ID = AttributeKey.valueOf("tcpDeviceId");
    /** 认证通过后绑定到 channel 的归属用户ID */
    public static final AttributeKey<Long> ATTR_OWNER_ID = AttributeKey.valueOf("tcpOwnerId");
    public static final AttributeKey<String> ATTR_GATEWAY_ID = AttributeKey.valueOf("tcpGatewayId");
    public static final AttributeKey<Set<String>> ATTR_DEVICE_IDS = AttributeKey.valueOf("tcpDeviceIds");
    public static final AttributeKey<Map<String, Long>> ATTR_DEVICE_OWNERS = AttributeKey.valueOf("tcpDeviceOwners");

    private final ObjectMapper objectMapper;
    private final DeviceRepository deviceRepository;
    private final DataService dataService;
    private final TcpConnectionManager connectionManager;

    @Value("${app.tcp.access-token:}")
    private String gatewayAccessToken;

    @Value("${app.tcp.max-devices-per-gateway:256}")
    private int maxDevicesPerGateway;

    /**
     * 已认证设备的读空闲超时（秒）。认证成功后替换 pipeline 中的 IdleStateHandler，
     * 从 auth-timeout 切换为更长的 read-idle 超时，防止正常低频设备被误断。
     * 设备端应每 60 秒发一次 {"type":"heartbeat"}，此值应 > 60，默认 90。
     */
    @Value("${app.tcp.read-idle-seconds:90}")
    private int readIdleSeconds;

    public TcpMessageHandler(ObjectMapper objectMapper,
                             DeviceRepository deviceRepository,
                             DataService dataService,
                             TcpConnectionManager connectionManager) {
        this.objectMapper = objectMapper;
        this.deviceRepository = deviceRepository;
        this.dataService = dataService;
        this.connectionManager = connectionManager;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String line) {
        Channel channel = ctx.channel();
        Map<String, Object> msg;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(line, Map.class);
            msg = parsed;
        } catch (Exception e) {
            sendJson(ctx, Map.of("type", "error", "message", "JSON 解析失败"));
            return;
        }

        String type = msg.get("type") != null ? msg.get("type").toString() : "";
        switch (type) {
            case "auth" -> handleAuth(ctx, msg);
            case "gateway_auth" -> handleGatewayAuth(ctx, msg);
            case "telemetry" -> handleTelemetry(ctx, msg);
            case "status" -> handleStatus(ctx, msg);
            case "command_result" -> handleCommandResult(ctx, msg);
            case "heartbeat" -> handleHeartbeat(ctx, msg);
            default -> sendJson(ctx, Map.of("type", "error", "message", "未知消息类型: " + type));
        }
    }

    // ========== 鉴权 ==========

    private void handleGatewayAuth(ChannelHandlerContext ctx, Map<String, Object> msg) {
        String accessToken = str(msg.get("accessToken"));
        String gatewayId = str(msg.get("gatewayId"));
        Set<String> deviceIds = stringSet(msg.get("deviceIds"));
        if (gatewayAccessToken == null || gatewayAccessToken.isBlank()) {
            rejectGateway(ctx, "平台未配置 TCP 网关 Token");
            return;
        }
        if (accessToken == null || !MessageDigest.isEqual(
                accessToken.getBytes(StandardCharsets.UTF_8),
                gatewayAccessToken.getBytes(StandardCharsets.UTF_8))) {
            rejectGateway(ctx, "网关 Token 不正确");
            return;
        }
        if (gatewayId == null || gatewayId.isBlank()) {
            rejectGateway(ctx, "缺少 gatewayId");
            return;
        }
        if (deviceIds.isEmpty() || deviceIds.size() > maxDevicesPerGateway) {
            rejectGateway(ctx, "deviceIds 不能为空且不能超过 " + maxDevicesPerGateway + " 台");
            return;
        }
        Map<String, Long> owners = new HashMap<>();
        for (String deviceId : deviceIds) {
            Device device = deviceRepository.findByDeviceId(deviceId).orElse(null);
            if (device == null) {
                rejectGateway(ctx, "设备不存在: " + deviceId);
                return;
            }
            owners.put(deviceId, device.getOwnerId());
        }
        ctx.channel().attr(ATTR_GATEWAY_ID).set(gatewayId);
        ctx.channel().attr(ATTR_DEVICE_IDS).set(Set.copyOf(deviceIds));
        ctx.channel().attr(ATTR_DEVICE_OWNERS).set(Map.copyOf(owners));
        ctx.channel().attr(ATTR_DEVICE_ID).set(deviceIds.iterator().next());
        ctx.channel().attr(ATTR_OWNER_ID).set(owners.values().iterator().next());
        connectionManager.registerGateway(gatewayId, owners, ctx.channel());
        for (String deviceId : deviceIds) {
            deviceRepository.findByDeviceId(deviceId).ifPresent(device -> {
                device.setStatus("ONLINE");
                device.setLastActive(LocalDateTime.now());
                deviceRepository.save(device);
            });
        }
        // 网关认证成功后：切换为已认证读超时
        replaceIdleHandler(ctx);
        sendJson(ctx, Map.of("type", "gateway_auth_result", "success", true,
                "gatewayId", gatewayId, "deviceIds", deviceIds, "message", "网关认证成功"));
        log.info("TCP gateway authenticated: {} ({} devices)", gatewayId, deviceIds.size());
    }

    private void rejectGateway(ChannelHandlerContext ctx, String message) {
        sendJson(ctx, Map.of("type", "gateway_auth_result", "success", false, "message", message));
        ctx.close();
    }

    private void handleAuth(ChannelHandlerContext ctx, Map<String, Object> msg) {
        String deviceId = str(msg.get("deviceId"));
        String apiKey = str(msg.get("apiKey"));
        if (deviceId == null || apiKey == null) {
            sendJson(ctx, Map.of("type", "auth_result", "success", false, "message", "缺少 deviceId 或 apiKey"));
            ctx.close();
            return;
        }
        Device device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        if (device == null || device.getApiKey() == null || !MessageDigest.isEqual(
                apiKey.getBytes(StandardCharsets.UTF_8),
                device.getApiKey().getBytes(StandardCharsets.UTF_8))) {
            log.warn("TCP auth failed for device {} (bad credentials)", deviceId);
            sendJson(ctx, Map.of("type", "auth_result", "success", false, "message", "设备ID或API Key不正确"));
            ctx.close();
            return;
        }
        // 认证成功：绑定身份并注册连接
        ctx.channel().attr(ATTR_DEVICE_ID).set(deviceId);
        ctx.channel().attr(ATTR_OWNER_ID).set(device.getOwnerId());
        connectionManager.register(deviceId, ctx.channel());

        // 设备在线状态回写
        try {
            device.setStatus("ONLINE");
            device.setLastActive(LocalDateTime.now());
            deviceRepository.save(device);
        } catch (Exception e) {
            log.warn("TCP auth: update device status failed: {}", e.getMessage());
        }

        // 认证成功后：将 pipeline 中的 IdleStateHandler 替换为已认证读超时
        // auth-timeout（30s）→ read-idle-seconds（90s），低频设备每 60s 发心跳即可保活
        replaceIdleHandler(ctx);

        sendJson(ctx, Map.of(
                "type", "auth_result",
                "success", true,
                "deviceId", deviceId,
                "message", "认证成功"));
        log.info("TCP device authenticated: {}", deviceId);
    }

    // ========== 遥测上报 ==========

    private void handleTelemetry(ChannelHandlerContext ctx, Map<String, Object> msg) {
        Channel channel = ctx.channel();
        String deviceId = resolveDeviceId(channel, msg);
        if (deviceId == null) {
            sendJson(ctx, Map.of("type", "error", "message", "未认证或缺少有效 deviceId"));
            return;
        }
        String sensorId = str(msg.get("sensorId"));
        Double value = msg.get("value") instanceof Number n ? n.doubleValue() : null;
        if (sensorId == null || value == null || !Double.isFinite(value)) {
            sendJson(ctx, Map.of("type", "error", "message", "遥测帧缺少 sensorId 或 value"));
            return;
        }
        String sensorType = str(msg.get("sensorType"));
        String unit = str(msg.get("unit"));
        Map<String, Long> owners = channel.attr(ATTR_DEVICE_OWNERS).get();
        Long ownerId = owners != null ? owners.get(deviceId) : channel.attr(ATTR_OWNER_ID).get();

        // 与 MQTT 通道一致：走统一管线（TDengine → Redis → PostgreSQL → 告警 → Kafka）
        dataService.writeDataPoint(deviceId, sensorId, sensorType, value, unit, ownerId);
        log.debug("TCP telemetry: device={} sensor={} value={}", deviceId, sensorId, value);
    }

    // ========== 状态上报 ==========

    private void handleStatus(ChannelHandlerContext ctx, Map<String, Object> msg) {
        String deviceId = resolveDeviceId(ctx.channel(), msg);
        if (deviceId == null) {
            sendJson(ctx, Map.of("type", "error", "message", "未认证或缺少有效 deviceId"));
            return;
        }
        String status = str(msg.get("status"));
        if (status == null) return;
        String normalizedStatus = status.toUpperCase();
        if (!java.util.Set.of("ONLINE", "OFFLINE", "WARNING").contains(normalizedStatus)) {
            sendJson(ctx, Map.of("type", "error", "message", "非法设备状态"));
            return;
        }
        deviceRepository.findByDeviceId(deviceId).ifPresent(d -> {
            d.setStatus(normalizedStatus);
            d.setLastActive(LocalDateTime.now());
            deviceRepository.save(d);
        });
        log.info("TCP status: {} → {}", deviceId, status);
    }

    // ========== 指令回执 ==========

    private void handleCommandResult(ChannelHandlerContext ctx, Map<String, Object> msg) {
        String deviceId = resolveDeviceId(ctx.channel(), msg);
        if (deviceId == null) return;
        Boolean success = msg.get("success") instanceof Boolean b ? b : null;
        String command = str(msg.get("command"));
        String message = str(msg.get("message"));
        log.info("TCP command result: device={} command={} success={} message={}",
                deviceId, command, success, message);
        // 可选：此处可扩展将回执写入 CommandLog / Redis，当前仅记录日志
    }

    // ========== 心跳 ==========

    /**
     * 处理设备心跳消息，防止中间网络（NAT/防火墙）因长时间无数据包而超时断开连接。
     * 建议设备每 60 秒发一次心跳：{"type":"heartbeat"}
     * 服务端回复：{"type":"heartbeat_ack","timestamp":...}
     */
    private void handleHeartbeat(ChannelHandlerContext ctx, Map<String, Object> msg) {
        String deviceId = resolveDeviceId(ctx.channel(), msg);
        if (deviceId == null) {
            // 未认证设备发心跳：忽略，等待 auth-timeout 关闭
            return;
        }
        // 更新设备最近活跃时间（异步，避免阻塞 Netty I/O 线程）
        try {
            deviceRepository.findByDeviceId(deviceId).ifPresent(device -> {
                device.setLastActive(java.time.LocalDateTime.now());
                deviceRepository.save(device);
            });
        } catch (Exception e) {
            log.debug("TCP heartbeat: update lastActive failed (non-critical): {}", e.getMessage());
        }
        sendJson(ctx, Map.of("type", "heartbeat_ack", "timestamp", System.currentTimeMillis()));
        log.debug("TCP heartbeat: device={}", deviceId);
    }

    // ========== 生命周期 ==========

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        String deviceId = ctx.channel().attr(ATTR_DEVICE_ID).get();
        if (deviceId != null) {
            connectionManager.unregister(deviceId, ctx.channel());
            Set<String> disconnectedIds = ctx.channel().attr(ATTR_DEVICE_IDS).get();
            if (disconnectedIds == null) disconnectedIds = Set.of(deviceId);
            for (String id : disconnectedIds) {
                if (connectionManager.isOnline(id)) continue;
                deviceRepository.findByDeviceId(id).ifPresent(device -> {
                    device.setStatus("OFFLINE");
                    device.setLastActive(LocalDateTime.now());
                    deviceRepository.save(device);
                });
            }
            log.info("TCP connection disconnected: {} ({} devices)",
                    ctx.channel().attr(ATTR_GATEWAY_ID).get(), disconnectedIds.size());
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent event && event.state() == IdleState.READER_IDLE) {
            String deviceId = ctx.channel().attr(ATTR_DEVICE_ID).get();
            if (deviceId == null) {
                // 未认证连接：auth-timeout-seconds 内未完成鉴权，断开
                log.info("TCP connection {} closed: auth timeout", ctx.channel().remoteAddress());
                ctx.close();
            } else {
                // 已认证设备：read-idle-seconds 内无任何数据（遥测/心跳），断开连接
                // channelInactive() 会将设备标记为 OFFLINE
                // 设备应重新建立连接并认证；建议设备端每 60s 发一次 {"type":"heartbeat"}
                log.info("TCP device {} closed: read idle timeout (no data/heartbeat received)", deviceId);
                ctx.close();
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("TCP channel error: {}", cause.getMessage());
        ctx.close();
    }

    private void sendJson(ChannelHandlerContext ctx, Map<String, Object> payload) {
        try {
            ctx.writeAndFlush(objectMapper.writeValueAsString(payload) + "\n");
        } catch (Exception e) {
            log.warn("TCP send failed: {}", e.getMessage());
        }
    }

    /**
     * 认证成功后替换 pipeline 中的 IdleStateHandler，
     * 从"鉴权超时"（auth-timeout-seconds，30s）切换为"已认证读空闲超时"（read-idle-seconds，90s）。
     * <p>
     * 两阶段超时策略：
     * <ul>
     *   <li>认证阶段：30s 内未完成鉴权 → 关闭连接</li>
     *   <li>已认证阶段：90s 内无任何数据（含心跳）→ 关闭连接，设备端应重连</li>
     * </ul>
     * 替换在 Netty EventLoop 线程中执行，保证线程安全。
     */
    private void replaceIdleHandler(ChannelHandlerContext ctx) {
        if (readIdleSeconds <= 0) return; // 0 = 禁用读超时
        ctx.pipeline().replace(
                io.netty.handler.timeout.IdleStateHandler.class,
                "idleStateHandler",
                new io.netty.handler.timeout.IdleStateHandler(
                        readIdleSeconds, 0, 0, java.util.concurrent.TimeUnit.SECONDS));
        log.debug("TCP pipeline idle handler replaced: auth-timeout → read-idle {}s", readIdleSeconds);
    }

    private String str(Object o) {
        return o != null ? o.toString() : null;
    }

    private String resolveDeviceId(Channel channel, Map<String, Object> msg) {
        String requested = str(msg.get("deviceId"));
        Set<String> authorized = channel.attr(ATTR_DEVICE_IDS).get();
        if (authorized != null) return requested != null && authorized.contains(requested) ? requested : null;
        return channel.attr(ATTR_DEVICE_ID).get();
    }

    private Set<String> stringSet(Object value) {
        if (!(value instanceof Iterable<?> values)) return Set.of();
        Set<String> result = new HashSet<>();
        for (Object item : values) {
            String id = str(item);
            if (id == null || id.isBlank() || !result.add(id)) return Set.of();
        }
        return result;
    }
}
