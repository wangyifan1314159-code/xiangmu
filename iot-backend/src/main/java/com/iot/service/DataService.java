package com.iot.service;

import com.iot.config.SecurityUtils;
import com.iot.model.CommandLog;
import com.iot.model.DataPoint;
import com.iot.model.Device;
import com.iot.model.Sensor;
import com.iot.repository.CommandLogRepository;
import com.iot.repository.DataPointRepository;
import com.iot.repository.DeviceRepository;
import com.iot.repository.SensorRepository;
import com.iot.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class DataService {

    private final DataPointRepository dataPointRepository;
    private final AlertService alertService;
    private final SecurityUtils securityUtils;

    // 防 Kafka 消费 → 再生产的循环
    private static final ThreadLocal<Boolean> FROM_KAFKA = ThreadLocal.withInitial(() -> false);
    private final DeviceRepository deviceRepository;
    private final SensorRepository sensorRepository;
    private final CommandLogRepository commandLogRepository;
    private final UserRepository userRepository;

    public DataService(DataPointRepository dataPointRepository,
                       AlertService alertService,
                       SecurityUtils securityUtils,
                       DeviceRepository deviceRepository,
                       SensorRepository sensorRepository,
                       CommandLogRepository commandLogRepository,
                       UserRepository userRepository) {
        this.dataPointRepository = dataPointRepository;
        this.alertService = alertService;
        this.securityUtils = securityUtils;
        this.deviceRepository = deviceRepository;
        this.sensorRepository = sensorRepository;
        this.commandLogRepository = commandLogRepository;
        this.userRepository = userRepository;
    }

    // 以下为可选组件，开发环境可能不可用
    @Autowired(required = false)
    private RedisCacheService redis;

    @Autowired(required = false)
    private TDengineService tdengine;

    @Autowired(required = false)
    private KafkaProducerService kafkaProducer;

    @Autowired(required = false)
    private WebSocketPushService wsPush;

    @Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private org.eclipse.paho.client.mqttv3.MqttClient mqttClient;

    @Autowired(required = false)
    private TcpConnectionManager tcpConnectionManager;

    private final com.fasterxml.jackson.databind.ObjectMapper commandMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @Value("${app.mqtt.enabled:false}")
    private boolean mqttEnabled;

    // PostgreSQL 批量写入缓冲区（并发安全：HTTP 与 MQTT 线程可能同时写入）
    private final Queue<DataPoint> writeBuffer = new ConcurrentLinkedQueue<>();
    private static final int BATCH_SIZE = 500;

    // 非关键副作用（传感器状态回写、告警评估）异步执行，避免拖慢数据写入主链路
    private final ExecutorService sideEffectPool;
    {
        sideEffectPool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "data-sideeffect");
            t.setDaemon(true);
            return t;
        });
    }

    @Value("${app.data.retention-days:90}")
    private int retentionDays;

    private Long currentUserId() {
        Long id = securityUtils.getCurrentUserId();
        if (id == null) throw new RuntimeException("用户未登录");
        return id;
    }

    /**
     * 设备归属校验：所有按 deviceId 的数据读/写/命令下发前必须调用。
     * - 普通用户/管理员：设备必须归属当前用户
     * - 设备 API Key（ROLE_DEVICE）：仅允许访问密钥对应的那个设备
     * 报错文案不区分"不存在"与"无权"，避免探测他人设备
     */
    private Device requireOwnedDevice(String deviceId) {
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new RuntimeException("设备不存在或无权访问: " + deviceId));
        if (securityUtils.hasRole("DEVICE")) {
            String authDeviceId = securityUtils.getAuthenticatedDeviceId();
            if (!deviceId.equals(authDeviceId)) {
                throw new RuntimeException("设备不存在或无权访问: " + deviceId);
            }
            return device;
        }
        if (!device.getOwnerId().equals(currentUserId())) {
            throw new RuntimeException("设备不存在或无权访问: " + deviceId);
        }
        return device;
    }

    // ========== 数据查询 ==========

    /**
     * 查询设备最新 N 条数据
     */
    public List<DataPoint> getDeviceData(String deviceId, String sensorId, int limit) {
        return getDeviceData(deviceId, sensorId, null, null, limit);
    }

    /**
     * 查询设备数据，支持时间范围过滤
     * 查询顺序: Redis 缓存 → TDengine → PostgreSQL
     */
    public List<DataPoint> getDeviceData(String deviceId, String sensorId,
                                          LocalDateTime from, LocalDateTime to, int limit) {
        // 0. 归属校验（含 API Key 设备身份）
        requireOwnedDevice(deviceId);

        // 0.5 限制单次最大返回条数，防止恶意超大 limit 拉全表拖垮 PG/TDengine
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        limit = safeLimit;

        // 1. 无时间范围时先查 Redis 缓存（仅缓存最新数据）
        if (from == null && to == null) {
            List<DataPoint> cached = getCachedData(deviceId, sensorId, limit);
            if (cached != null) return cached;
        }

        // 2. TDengine 查询（时间范围 + 聚合）
        if (tdengine != null) {
            try {
                List<Map<String, Object>> tdData;
                if (from != null && to != null) {
                    tdData = tdengine.queryRange(deviceId, sensorId, from, to, null);
                } else {
                    tdData = tdengine.queryLatest(deviceId, sensorId, limit);
                }
                if (!tdData.isEmpty()) {
                    List<DataPoint> result = tdData.stream()
                            .map(this::mapTdRowToDataPoint).toList();
                    safeSetCache(deviceId, sensorId, result);
                    return result;
                }
            } catch (Exception e) {
                log.warn("TDengine query failed, falling back to PostgreSQL: {}", e.getMessage());
            }
        }

        // 3. PostgreSQL fallback（按用户过滤；带 LIMIT，避免全量加载历史数据）
        Long uid = currentUserId();
        List<DataPoint> data;
        if (sensorId != null && !sensorId.isEmpty()) {
            data = dataPointRepository.findByDeviceIdAndSensorIdAndOwnerIdOrderByTimestampDesc(
                    deviceId, sensorId, uid, from != null || to != null ? Limit.of(10000) : Limit.of(limit));
        } else {
            data = dataPointRepository.findByDeviceIdAndOwnerIdOrderByTimestampDesc(
                    deviceId, uid, from != null || to != null ? Limit.of(10000) : Limit.of(limit));
        }

        // 时间范围过滤
        if (from != null) {
            data = data.stream().filter(dp -> !dp.getTimestamp().isBefore(from)).toList();
        }
        if (to != null) {
            data = data.stream().filter(dp -> !dp.getTimestamp().isAfter(to)).toList();
        }

        List<DataPoint> result = data.size() > limit ? data.subList(0, limit) : data;
        if (from == null && to == null) {
            safeSetCache(deviceId, sensorId, result);
        }
        return result;
    }

    /**
     * 时间范围 + 降采样查询（仅 TDengine）
     */
    public List<Map<String, Object>> queryHistory(String deviceId, String sensorId,
                                                   LocalDateTime from, LocalDateTime to,
                                                   String interval) {
        requireOwnedDevice(deviceId);
        if (tdengine != null) {
            try {
                return tdengine.queryRange(deviceId, sensorId, from, to, interval);
            } catch (Exception e) {
                log.warn("TDengine history query failed: {}", e.getMessage());
            }
        }
        // PostgreSQL fallback（按用户过滤）
        List<DataPoint> data = dataPointRepository.findByDeviceIdAndSensorIdAndOwnerIdOrderByTimestampDesc(
                deviceId, sensorId, currentUserId(), Limit.of(10000));
        return data.stream()
                .filter(dp -> !dp.getTimestamp().isBefore(from) && !dp.getTimestamp().isAfter(to))
                .map(dp -> Map.<String, Object>of(
                        "ts", dp.getTimestamp().toString(),
                        "value", dp.getValue(),
                        "sensor_id", dp.getSensorId()))
                .toList();
    }

    public List<Map<String, Object>> queryByProduct(String productType, String sensorType,
                                                     LocalDateTime from, LocalDateTime to,
                                                     String interval) {
        if (tdengine != null) {
            return tdengine.queryByProductType(productType, sensorType, from, to, interval);
        }
        return List.of();
    }

    // ========== 数据写入 ==========

    @Transactional
    public DataPoint addDataPoint(String deviceId, String sensorId, Double value) {
        return addDataPoint(deviceId, sensorId, null, value, null);
    }

    @Transactional
    public DataPoint addDataPoint(String deviceId, String sensorId,
                                                String sensorType, Double value, String unit) {
        // 数据上报前校验设备归属（API Key 设备只能写自身，普通用户只能写自己的设备）
        Device device = requireOwnedDevice(deviceId);
        Long uid = device.getOwnerId();
        // 传感器类型未提供时自动查补
        if (sensorType == null) {
            sensorType = sensorRepository.findById(sensorId).map(Sensor::getType).orElse(null);
        }
        if (unit == null) {
            unit = sensorRepository.findById(sensorId).map(Sensor::getUnit).orElse(null);
        }
        return writeDataPoint(deviceId, sensorId, sensorType, value, unit, uid);
    }

    /** 供 Kafka Consumer 调用：标记来自 Kafka，避免循环重发 */
    public DataPoint fromKafka(String deviceId, String sensorId, String sensorType,
                                Double value, String unit, Long ownerId) {
        FROM_KAFKA.set(true);
        try {
            return writeDataPoint(deviceId, sensorId, sensorType, value, unit, ownerId);
        } finally {
            FROM_KAFKA.remove();
        }
    }

    /** 内部写入方法：允许直接指定 ownerId，供后台任务/仿真使用 */
    DataPoint writeDataPoint(String deviceId, String sensorId, String sensorType,
                             Double value, String unit, Long ownerId) {
        LocalDateTime now = LocalDateTime.now();
        DataPoint dp = DataPoint.builder()
                .deviceId(deviceId)
                .sensorId(sensorId)
                .value(value)
                .ownerId(ownerId)
                .timestamp(now)
                .build();

        if (tdengine != null) {
            try {
                // 线程设备的真实 product_type，使 TDengine 子表标签正确、可按产品类型聚合查询
                String productType = deviceRepository.findByDeviceId(deviceId)
                        .map(Device::getType).orElse(null);
                tdengine.insert(deviceId, sensorId,
                        sensorType != null ? sensorType : "unknown",
                        value, unit != null ? unit : "", now, productType);
            } catch (Exception e) {
                log.warn("TDengine write failed: {}", e.getMessage());
            }
        }
        writeBuffer.add(dp);
        flushPgBuffer();

        // 更新 Sensor 实体的实时值（前端显示用）
        // WebSocket 实时推送：定向推送给设备归属用户，避免全局广播造成跨用户数据泄露
        if (wsPush != null) {
            String ownerUsername = ownerId != null
                    ? userRepository.findById(ownerId).map(u -> u.getUsername()).orElse(null)
                    : null;
            wsPush.pushDeviceData(ownerUsername, deviceId, sensorId, value, unit);
        }

        sendKafkaTelemetry(deviceId, sensorId, sensorType, value, unit);
        safeUpdateShadow(deviceId, sensorId, value);
        safeDeleteCache(deviceId, sensorId);

        // 传感器状态回写 + 告警评估含多次同步 DB 查询，移入异步线程避免阻塞写入主链路
        sideEffectPool.execute(() -> {
            try {
                updateSensorValue(deviceId, sensorId, value);
            } catch (Exception e) {
                log.debug("Update sensor value failed (non-critical): {}", e.getMessage());
            }
            try {
                // 传入设备产品类型，使"按产品类型"配置的告警规则可以命中
                String deviceType = deviceRepository.findByDeviceId(deviceId)
                        .map(Device::getType).orElse(null);
                alertService.evaluate(deviceId, sensorType != null ? sensorType : sensorId,
                        value, deviceType);
            } catch (Exception e) {
                log.error("Alert eval failed: device={} sensor={}", deviceId, sensorId, e);
            }
        });

        return dp;
    }

    private void updateSensorValue(String deviceId, String sensorId, double value) {
        try {
            sensorRepository.findById(sensorId).ifPresent(sensor -> {
                sensor.setValue(value);
                sensorRepository.save(sensor);
            });
            deviceRepository.findByDeviceId(deviceId).ifPresent(device -> {
                device.setLastActive(LocalDateTime.now());
                device.setStatus("ONLINE");
                deviceRepository.save(device);
            });
        } catch (Exception e) {
            log.debug("Update sensor value failed (non-critical): {}", e.getMessage());
        }
    }

    private void sendKafkaTelemetry(String deviceId, String sensorId, String sensorType,
                                     Double value, String unit) {
        if (kafkaProducer == null || Boolean.TRUE.equals(FROM_KAFKA.get())) return;
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("deviceId", deviceId);
            msg.put("sensorId", sensorId);
            msg.put("sensorType", sensorType != null ? sensorType : "unknown");
            msg.put("value", value);
            msg.put("unit", unit != null ? unit : "");
            msg.put("timestamp", System.currentTimeMillis());
            kafkaProducer.sendDeviceTelemetry(deviceId, msg);
        } catch (Exception e) {
            log.debug("Kafka telemetry send failed (non-critical): {}", e.getMessage());
        }
    }

    // ========== 命令下发 ==========

    @Transactional
    public String sendCommand(String deviceId, String command, Object params) {
        // 命令下发前校验设备归属，防止控制他人执行器
        requireOwnedDevice(deviceId);
        return doSendCommand(deviceId, command, params);
    }

    /**
     * 内部受信通道（MQTT/Kafka 消费）使用的命令下发：调用线程无登录上下文，跳过归属校验。
     * 仅限服务端内部消息处理器调用，禁止暴露给 Controller——REST 路径必须走 sendCommand。
     */
    @Transactional
    public String sendCommandFromSystem(String deviceId, String command, Object params) {
        return doSendCommand(deviceId, command, params);
    }

    private String doSendCommand(String deviceId, String command, Object params) {
        // 如果是控制类命令，同步更新执行器状态
        String actuatorName = extractActuatorName(params);
        String result = applyActuatorCommand(deviceId, actuatorName, command);

        // 先实际下发，再据实记录：仅当命令确实被 TCP/MQTT 发布成功才记 SENT，
        // 设备离线且 MQTT 未启用等 no-op 场景记 FAILED，避免"假成功"日志。
        boolean published = publishCommand(deviceId, command, params);

        saveCommandLog(deviceId, command, params, published ? "SENT" : "FAILED");

        if (redis != null) {
            safeRedis(() -> redis.setCache("cmd:" + UUID.randomUUID(),
                    Map.of("deviceId", deviceId, "command", command, "params", params,
                            "status", published ? "SENT" : "FAILED"), 300));
        }
        return result;
    }

    /** 指令日志落库：@Transactional 由调用方保证（sendCommand / sendCommandFromSystem） */
    private void saveCommandLog(String deviceId, String command, Object params, String status) {
        try {
            Device device = deviceRepository.findByDeviceId(deviceId).orElse(null);
            if (device == null) return;
            CommandLog cmdLog = CommandLog.builder()
                    .deviceId(deviceId)
                    .command(command)
                    .params(params != null ? commandMapper.writeValueAsString(params) : null)
                    .status(status)
                    .ownerId(device.getOwnerId())
                    .sentAt(LocalDateTime.now())
                    .build();
            commandLogRepository.save(cmdLog);
        } catch (Exception e) {
            log.debug("Command log save failed (non-critical): {}", e.getMessage());
        }
    }

    /** 返回命令是否被任一通道实际发布（false 表示设备离线 / MQTT 未启用等 no-op） */
    private boolean publishCommand(String deviceId, String command, Object params) {
        // 1. TCP 通道优先：设备长连接在线时直连下发（低延迟、可回执）
        if (tcpConnectionManager != null) {
            try {
                if (tcpConnectionManager.publish(deviceId, command, params)) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("TCP command publish failed (non-critical): {}", e.getMessage());
            }
        }

        // 2. MQTT 兜底：TCP 不在线时走 MQTT（broker 缓存/重连语义）
        try {
            if (!mqttEnabled || mqttClient == null || !mqttClient.isConnected()) {
                return false;
            }
            String payload = commandMapper.writeValueAsString(
                    Map.of("deviceId", deviceId, "command", command, "params", params,
                            "timestamp", System.currentTimeMillis()));
            mqttClient.publish("iot/" + deviceId + "/command",
                    new org.eclipse.paho.client.mqttv3.MqttMessage(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            log.info("MQTT command published: iot/{}/command -> {}", deviceId, payload);
            return true;
        } catch (Exception e) {
            log.warn("MQTT command publish failed (non-critical): {}", e.getMessage());
            return false;
        }
    }

    private String extractActuatorName(Object params) {
        if (params instanceof Map) {
            Object actuator = ((Map<?, ?>) params).get("actuator");
            if (actuator != null) return actuator.toString();
        }
        return null;
    }

    private String applyActuatorCommand(String deviceId, String actuatorName, String command) {
        if (actuatorName == null) {
            return "命令 [" + command + "] 已发送至设备 " + deviceId;
        }
        // 查找匹配的执行器
        try {
            var deviceOpt = deviceRepository.findByDeviceId(deviceId);
            if (deviceOpt.isEmpty()) {
                return "命令 [" + command + "] 已发送至设备 " + deviceId + "（设备不存在）";
            }
            Device device = deviceOpt.get();
            for (Sensor sensor : device.getSensors()) {
                if (actuatorName.equals(sensor.getName())) {
                    double newValue;
                    switch (command.toLowerCase()) {
                        case "on":
                        case "1":
                            newValue = 1.0;
                            break;
                        case "off":
                        case "0":
                            newValue = 0.0;
                            break;
                        case "toggle":
                            newValue = sensor.getValue() > 0.5 ? 0.0 : 1.0;
                            break;
                        default:
                            return "命令 [" + command + "] 已发送至设备 " + deviceId;
                    }
                    sensor.setValue(newValue);
                    sensorRepository.save(sensor);
                    device.setLastActive(LocalDateTime.now());
                    device.setStatus("ONLINE");
                    deviceRepository.save(device);

                    // 写入 TDengine 时序记录，供历史数据查询
                    writeDataPoint(deviceId, sensor.getId(), sensor.getType(),
                            newValue, sensor.getUnit(), device.getOwnerId());

                    String state = newValue > 0.5 ? "ON" : "OFF";
                    return "命令 [" + command + "] → " + actuatorName + " 已" + state;
                }
            }
        } catch (Exception e) {
            log.warn("Actuator command apply failed: {}", e.getMessage());
        }
        return "命令 [" + command + "] 已发送至设备 " + deviceId;
    }

    // ========== 数据清理 ==========

    /**
     * 每天凌晨 3:30 清理超过保留期的历史数据，防止 data_points 表无限增长拖慢写入/查询
     */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void scheduledCleanup() {
        try {
            LocalDateTime before = LocalDateTime.now().minusDays(retentionDays);
            dataPointRepository.deleteByTimestampBefore(before);
            log.info("Scheduled cleanup removed data_points older than {} days", retentionDays);
        } catch (Exception e) {
            log.warn("Scheduled data cleanup failed: {}", e.getMessage());
        }
    }

    public void cleanOldData(int retentionDays) {
        if (tdengine != null) {
            try {
                tdengine.cleanOldData(retentionDays);
            } catch (Exception e) {
                log.warn("TDengine clean failed: {}", e.getMessage());
            }
        }
        LocalDateTime before = LocalDateTime.now().minusDays(retentionDays);
        dataPointRepository.deleteByTimestampBeforeAndOwnerId(before, currentUserId());
    }

    // ========== 生命周期 ==========

    public void flushOnShutdown() {
        sideEffectPool.shutdown();
        try {
            if (!sideEffectPool.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS)) {
                sideEffectPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            sideEffectPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        if (tdengine != null) {
            try {
                tdengine.flushOnShutdown();
            } catch (Exception e) {
                log.warn("TDengine shutdown flush failed: {}", e.getMessage());
            }
        }
        flushPgBuffer();
    }

    // ========== Redis 安全调用 ==========

    private void safeRedis(Runnable action) {
        if (redis == null) return;
        try {
            action.run();
        } catch (Exception e) {
            log.debug("Redis operation failed (non-critical): {}", e.getMessage());
        }
    }

    private List<DataPoint> getCachedData(String deviceId, String sensorId, int limit) {
        if (redis == null) return null;
        try {
            String key = "data:recent:" + deviceId + (sensorId != null ? ":" + sensorId : "");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cached = redis.getCache(key, List.class);
            if (cached != null && !cached.isEmpty() && cached.size() >= limit) {
                return cached.stream().map(this::mapToDataPoint).limit(limit).toList();
            }
        } catch (Exception e) {
            log.debug("Redis cache miss: {}", e.getMessage());
        }
        return null;
    }

    private void safeSetCache(String deviceId, String sensorId, List<DataPoint> data) {
        safeRedis(() -> {
            String key = "data:recent:" + deviceId + (sensorId != null ? ":" + sensorId : "");
            redis.setCache(key, data, 30);
        });
    }

    private void safeUpdateShadow(String deviceId, String sensorId, Double value) {
        safeRedis(() -> {
            Map<String, Object> shadow = new HashMap<>();
            shadow.put("deviceId", deviceId);
            shadow.put("sensorId", sensorId);
            shadow.put("value", value);
            shadow.put("updatedAt", System.currentTimeMillis());
            redis.setDeviceShadow(deviceId, shadow);
            redis.setDeviceOnline(deviceId, true);
        });
    }

    private void safeDeleteCache(String deviceId, String sensorId) {
        safeRedis(() -> {
            redis.deleteCache("data:recent:" + deviceId);
            redis.deleteCache("data:recent:" + deviceId + ":" + sensorId);
        });
    }

    // ========== 缓冲区 ==========

    /**
     * 定时刷盘：每秒把缓冲区落盘 PostgreSQL，
     * 避免低频上报时数据长时间停留在内存导致查询"看不到刚上报的数据"
     */
    @Scheduled(fixedDelay = 1000)
    public void scheduledFlush() {
        try {
            flushPgBuffer();
        } catch (Exception e) {
            log.warn("Scheduled PG flush failed: {}", e.getMessage());
        }
    }

    private void flushPgBuffer() {
        if (writeBuffer.isEmpty()) return;
        List<DataPoint> batch = new ArrayList<>();
        DataPoint dp;
        while ((dp = writeBuffer.poll()) != null) {
            batch.add(dp);
        }
        if (batch.isEmpty()) return;
        dataPointRepository.saveAll(batch);
        log.debug("Flushed {} data points to PostgreSQL", batch.size());
    }

    // ========== 映射工具 ==========

    private DataPoint mapToDataPoint(Map<String, Object> map) {
        DataPoint dp = new DataPoint();
        dp.setId(map.get("id") != null ? ((Number) map.get("id")).longValue() : null);
        dp.setDeviceId((String) map.get("deviceId"));
        dp.setSensorId((String) map.get("sensorId"));
        dp.setValue(map.get("value") != null ? ((Number) map.get("value")).doubleValue() : 0.0);
        if (map.get("timestamp") instanceof String) {
            dp.setTimestamp(LocalDateTime.parse((String) map.get("timestamp")));
        }
        return dp;
    }

    private DataPoint mapTdRowToDataPoint(Map<String, Object> row) {
        DataPoint dp = new DataPoint();
        dp.setDeviceId((String) row.get("device_id"));
        dp.setSensorId((String) row.get("sensor_id"));
        dp.setValue(row.get("val") != null ? ((Number) row.get("val")).doubleValue() : 0.0);
        Object ts = row.get("ts");
        if (ts instanceof Timestamp) {
            dp.setTimestamp(((Timestamp) ts).toLocalDateTime());
        }
        return dp;
    }
}
