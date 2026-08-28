package com.iot.service;

import com.iot.model.Device;
import com.iot.model.Sensor;
import com.iot.repository.DeviceRepository;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Outbound connector for the documented AA/CRC16 tunneling-machine stream. */
@Component
@Slf4j
public class TunnelingMachineTcpClientConnectionManager implements AutoCloseable {
    private static final long RECONNECT_DELAY_MILLIS = 1_000;
    /** 连接活跃但超过该时长未收到任何有效帧时判定为假连接（如代理/防火墙代答 TCP 握手），快照报 NO_DATA */
    private static final long STALE_FRAME_MILLIS = 30_000;
    private final EventLoopGroup group = new NioEventLoopGroup();
    private final int connectTimeoutMillis;
    private final boolean allowPrivateTargets;
    private final DeviceRepository deviceRepository;
    private final DataService dataService;
    private final MethaneAlertService methaneAlertService;
    private final ExecutorService persistenceExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public TunnelingMachineTcpClientConnectionManager(
            DeviceRepository deviceRepository, DataService dataService,
            MethaneAlertService methaneAlertService,
            @Value("${app.tcp.client-connect-timeout-millis:5000}") int connectTimeoutMillis,
            @Value("${app.tcp.allow-private-targets:true}") boolean allowPrivateTargets) {
        this.deviceRepository = deviceRepository;
        this.dataService = dataService;
        this.methaneAlertService = methaneAlertService;
        this.allowPrivateTargets = allowPrivateTargets;
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public Map<String, Object> connect(String host, int port, String deviceId) {
        if (host == null || host.isBlank() || port < 1 || port > 65_535 || deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("目标地址、端口或设备无效");
        }
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("设备中心中不存在该设备"));
        Session session = new Session(host, port, resolveAllowedIpv4(host, port), device, prepareSensors(device));
        session.bootstrap = createBootstrap(session);
        String id = UUID.randomUUID().toString();
        sessions.put(id, session);
        try {
            bind(id, session, session.bootstrap.connect(session.target()).syncUninterruptibly().channel());
            return session.snapshot(id);
        } catch (RuntimeException error) {
            sessions.remove(id, session);
            throw error;
        }
    }

    public List<Map<String, Object>> listConnections() {
        return sessions.entrySet().stream().map(entry -> entry.getValue().snapshot(entry.getKey())).toList();
    }

    public List<Map<String, Object>> listConnectionsByOwner(Long ownerId) {
        if (ownerId == null) return List.of();
        return sessions.entrySet().stream()
                .filter(entry -> ownerId.equals(entry.getValue().ownerId()))
                .map(entry -> entry.getValue().snapshot(entry.getKey()))
                .toList();
    }

    public boolean disconnect(String id) {
        Session session = sessions.remove(id);
        if (session == null) return false;
        session.stop();
        markOffline(session.deviceId());
        return true;
    }

    private void bind(String id, Session session, Channel channel) {
        session.channel = channel;
        channel.closeFuture().addListener(ignored -> {
            session.clear(channel);
            log.info("掘进机 TCP 会话已关闭: device={}, remote={}, reconnect={}",
                    session.deviceId(), channel.remoteAddress(), !session.stopped.get());
            persistenceExecutor.execute(() -> markOffline(session.deviceId()));
            if (sessions.get(id) == session && !session.stopped.get()) scheduleReconnect(id, session);
        });
    }

    private void scheduleReconnect(String id, Session session) {
        if (!session.reconnectScheduled.compareAndSet(false, true)) return;
        group.next().schedule(() -> {
            session.reconnectScheduled.set(false);
            if (sessions.get(id) != session || session.stopped.get() || session.channel != null) return;
            ChannelFuture future = session.bootstrap.connect(session.target());
            future.addListener(result -> {
                if (result.isSuccess()) bind(id, session, future.channel());
                else if (sessions.get(id) == session && !session.stopped.get()) scheduleReconnect(id, session);
            });
        }, RECONNECT_DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void handleFrame(Session session, TunnelingMachineFrameDecoder.Frame frame) {
        session.validFrames.incrementAndGet();
        session.lastFrameAt = System.currentTimeMillis();
        List<Reading> readings = decode(frame);
        if (readings.isEmpty()) {
            session.unknownFrames.incrementAndGet();
            log.debug("掘进机帧已接收但无展示字段: device={}, function=0x{}",
                    session.deviceId(), Integer.toHexString(frame.function()));
            return;
        }
        log.debug("掘进机帧已解析: device={}, function=0x{}, readings={}",
                session.deviceId(), Integer.toHexString(frame.function()), readings.size());
        readings.forEach(reading -> {
            SensorTarget target = session.sensors.get(reading.key());
            if (target != null) dataService.writeDataPoint(session.deviceId(), target.id(), target.type(), reading.value(), target.unit(), session.ownerId());

            // 任务三：接收并解析出甲烷浓度后立即进行超限判定与告警
            if ("methane".equals(reading.key()) && methaneAlertService != null) {
                String rawFrameJson = String.format(
                        "{\"function\":\"0x%04X\",\"deviceId\":\"%s\",\"deviceName\":\"%s\",\"methane\":%.1f,\"timestamp\":\"%s\",\"rawHex\":\"%s\"}",
                        frame.function(), session.deviceId(), session.deviceName(), reading.value(),
                        java.time.LocalDateTime.now(), bytesToHex(frame.data()));
                methaneAlertService.checkAndAlert(session.deviceId(), session.deviceName(), session.ownerId(), reading.value(), rawFrameJson);
            }
        });
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    static List<Reading> decode(TunnelingMachineFrameDecoder.Frame frame) {
        byte[] b = frame.data();
        List<Reading> result = new ArrayList<>();
        if (frame.function() == 0x0111) {
            String[] keys = {"left_travel", "right_travel", "shovel_lift", "rear_support", "cutter_extend", "cutter_horizontal", "cutter_vertical"};
            for (int i = 0; i < keys.length; i++) result.add(new Reading(keys[i], (double) (b[i] & 0xFF)));
        } else if (frame.function() == 0x0131) {
            String[] keys = {"conveyor_1_forward", "conveyor_1_reverse", "starwheel_forward", "starwheel_reverse", "warning_bell"};
            for (int i = 0; i < keys.length; i++) result.add(new Reading(keys[i], (double) ((b[i] & 0xFF) == 0 ? 1 : 0)));
        } else if (frame.function() == 0x0141) {
            String[] keys = {"conveyor_2_start", "oil_pump_start", "travel_enable", "emergency_stop", "cutter_high_speed", "cutter_low_speed"};
            for (int i = 0; i < keys.length; i++) result.add(new Reading(keys[i], (double) ((b[i] & 0xFF) == 0 ? 1 : 0)));
        } else if (frame.function() == 0x0511) {
            result.add(new Reading("wind_speed", (b[0] & 0xFF) * 0.01));
            result.add(new Reading("temperature", (double) (b[1] & 0xFF)));
            result.add(new Reading("humidity", (double) (b[2] & 0xFF)));
            result.add(new Reading("methane", (double) ((b[4] & 0xFF) * 1000 + (b[5] & 0xFF) * 100 + (b[6] & 0xFF) * 10 + (b[7] & 0xFF))));
        } else if (frame.function() == 0x0521) {
            result.add(new Reading("tilt_x", (double) signed16(b[0], b[1]) / 100));
            result.add(new Reading("tilt_y", (double) signed16(b[2], b[3]) / 100));
            result.add(new Reading("tilt_z", (double) signed16(b[4], b[5]) / 100));
        } else if (frame.function() == 0x0531) {
            String[] keys = {"hydraulic_tank_level", "thrust_cylinder_level", "shield_cylinder_level", "left_thrust_pressure", "right_thrust_pressure", "rotation_pressure", "total_thrust_pressure", "support_pressure"};
            for (int i = 0; i < keys.length; i++) result.add(new Reading(keys[i], (double) (b[i] & 0xFF)));
        }
        return result;
    }

    private Bootstrap createBootstrap(Session session) {
        return new Bootstrap().group(group).channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
                .option(ChannelOption.TCP_NODELAY, true).option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline().addLast(new TunnelingMachineFrameDecoder(
                                ignored -> session.invalidFrames.incrementAndGet()));
                        channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override public void channelRead(io.netty.channel.ChannelHandlerContext context, Object message) {
                                if (message instanceof TunnelingMachineFrameDecoder.Frame frame) {
                                    persistenceExecutor.execute(() -> handleFrame(session, frame));
                                }
                            }
                            @Override public void exceptionCaught(io.netty.channel.ChannelHandlerContext context, Throwable cause) {
                                log.warn("掘进机 TCP I/O 异常: device={}, remote={}, reason={}",
                                        session.deviceId(), context.channel().remoteAddress(), cause.toString());
                                context.close();
                            }
                        });
                    }
                });
    }

    private Map<String, SensorTarget> prepareSensors(Device device) {
        Map<String, SensorTarget> targets = new LinkedHashMap<>();
        Map<String, Sensor> existing = new LinkedHashMap<>();
        device.getSensors().forEach(sensor -> existing.putIfAbsent(sensor.getType(), sensor));
        boolean changed = false;
        for (SensorDefinition definition : DEFINITIONS) {
            Sensor sensor = existing.get(definition.key());
            if (sensor == null) {
                sensor = Sensor.builder().id("s_" + UUID.randomUUID().toString().substring(0, 12))
                        .name(definition.name()).type(definition.key()).unit(definition.unit()).value(0.0)
                        .minVal(definition.min()).maxVal(definition.max()).build();
                device.addSensor(sensor);
                changed = true;
            }
            targets.put(definition.key(), new SensorTarget(sensor.getId(), sensor.getType(), sensor.getUnit()));
        }
        if (changed) deviceRepository.save(device);
        return targets;
    }

    private void markOffline(String deviceId) {
        deviceRepository.findByDeviceId(deviceId).ifPresent(device -> {
            device.setStatus("OFFLINE");
            device.setLastActive(LocalDateTime.now());
            deviceRepository.save(device);
        });
    }

    private InetSocketAddress resolveAllowedIpv4(String host, int port) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) throw new IllegalArgumentException("目标必须是字面 IPv4 地址");
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) try {
            if (parts[i].isEmpty() || (parts[i].length() > 1 && parts[i].startsWith("0"))) throw new NumberFormatException();
            int value = Integer.parseInt(parts[i]);
            if (value < 0 || value > 255) throw new NumberFormatException();
            bytes[i] = (byte) value;
        } catch (NumberFormatException error) { throw new IllegalArgumentException("目标必须是字面 IPv4 地址"); }
        try {
            InetAddress address = InetAddress.getByAddress(bytes);
            if (!(address.isLoopbackAddress() || address.isLinkLocalAddress() || (allowPrivateTargets && address.isSiteLocalAddress()))) {
                throw new IllegalArgumentException("目标必须是允许的 IPv4 地址");
            }
            return new InetSocketAddress(address, port);
        } catch (java.net.UnknownHostException error) { throw new IllegalArgumentException("目标地址无效", error); }
    }

    @Override @PreDestroy public void close() {
        sessions.values().forEach(Session::stop);
        sessions.clear();
        group.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
        persistenceExecutor.shutdown();
    }

    static int signed16(byte high, byte low) { return (short) (((high & 0xFF) << 8) | (low & 0xFF)); }
    record Reading(String key, double value) { }
    private record SensorTarget(String id, String type, String unit) { }
    private record SensorDefinition(String key, String name, String unit, double min, double max) { }
    private static final List<SensorDefinition> DEFINITIONS = List.of(
            definition("left_travel", "左行走前后", "raw", 0, 255), definition("right_travel", "右行走前后", "raw", 0, 255), definition("shovel_lift", "铲板升降", "raw", 0, 255), definition("rear_support", "后支撑升降", "raw", 0, 255), definition("cutter_extend", "截割头伸缩", "raw", 0, 255), definition("cutter_horizontal", "截割头左右", "raw", 0, 255), definition("cutter_vertical", "截割头前后", "raw", 0, 255),
            definition("conveyor_1_forward", "一运正转", "", 0, 1), definition("conveyor_1_reverse", "一运反转", "", 0, 1), definition("starwheel_forward", "星轮正转", "", 0, 1), definition("starwheel_reverse", "星轮反转", "", 0, 1), definition("warning_bell", "警铃", "", 0, 1), definition("conveyor_2_start", "二运启停", "", 0, 1), definition("oil_pump_start", "油泵启停", "", 0, 1), definition("travel_enable", "行走启停", "", 0, 1), definition("emergency_stop", "急停按钮", "", 0, 1), definition("cutter_high_speed", "截割头高速旋转", "", 0, 1), definition("cutter_low_speed", "截割头低速旋转", "", 0, 1),
            definition("wind_speed", "风速", "m/s", 0, 3), definition("temperature", "温度", "C", -40, 125), definition("humidity", "湿度", "%RH", 0, 100), definition("methane", "甲烷", "ppm", 0, 10000), definition("tilt_x", "X 轴倾角", "deg", -180, 180), definition("tilt_y", "Y 轴倾角", "deg", -180, 180), definition("tilt_z", "Z 轴倾角", "deg", -180, 180),
            definition("hydraulic_tank_level", "液压站内部液位", "", 0, 100), definition("thrust_cylinder_level", "推进油缸液位", "", 0, 100), definition("shield_cylinder_level", "盾体油缸液位", "", 0, 100), definition("left_thrust_pressure", "左路推进油压", "MPa", 0, 100), definition("right_thrust_pressure", "右路推进油压", "MPa", 0, 100), definition("rotation_pressure", "旋转油缸油压", "MPa", 0, 100), definition("total_thrust_pressure", "总推进油缸油压", "MPa", 0, 100), definition("support_pressure", "支撑油缸油压", "MPa", 0, 100)
    );
    private static SensorDefinition definition(String key, String name, String unit, double min, double max) { return new SensorDefinition(key, name, unit, min, max); }

    private static final class Session {
        private final String host; private final int port; private final InetSocketAddress target; private final String deviceId; private final String deviceName; private final Long ownerId; private final Map<String, SensorTarget> sensors;
        private final AtomicBoolean stopped = new AtomicBoolean(); private final AtomicBoolean reconnectScheduled = new AtomicBoolean(); private final AtomicLong invalidFrames = new AtomicLong(); private final AtomicLong validFrames = new AtomicLong(); private final AtomicLong unknownFrames = new AtomicLong();
        private Bootstrap bootstrap;
        private volatile Channel channel; private volatile long lastFrameAt;
        private Session(String host, int port, InetSocketAddress target, Device device, Map<String, SensorTarget> sensors) { this.host = host; this.port = port; this.target = target; this.deviceId = device.getDeviceId(); this.deviceName = device.getName(); this.ownerId = device.getOwnerId(); this.sensors = sensors; }
        private InetSocketAddress target() { return target; } private String deviceId() { return deviceId; } private String deviceName() { return deviceName; } private Long ownerId() { return ownerId; }
        private void clear(Channel expected) { if (channel == expected) channel = null; }
        private void stop() { stopped.set(true); if (channel != null) channel.close(); }
        private Map<String, Object> snapshot(String id) { Map<String, Object> value = new LinkedHashMap<>(); value.put("id", id); value.put("host", host); value.put("port", port); value.put("deviceId", deviceId); value.put("deviceName", deviceName); value.put("status", resolveStatus()); value.put("remoteAddress", channel == null || channel.remoteAddress() == null ? "" : channel.remoteAddress().toString()); value.put("lastFrameAt", lastFrameAt); value.put("validFrameCount", validFrames.get()); value.put("invalidFrameCount", invalidFrames.get()); value.put("unknownFrameCount", unknownFrames.get()); return value; }
        // 活性判定: 通道活跃且 30s 内有有效帧才算 CONNECTED; 通道活跃但无帧(代理代答握手的假连接)报 NO_DATA
        private String resolveStatus() {
            if (channel == null || !channel.isActive()) return "RECONNECTING";
            return lastFrameAt > 0 && System.currentTimeMillis() - lastFrameAt <= STALE_FRAME_MILLIS ? "CONNECTED" : "NO_DATA";
        }
    }
}
