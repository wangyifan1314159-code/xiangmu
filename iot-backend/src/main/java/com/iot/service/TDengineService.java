package com.iot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@ConditionalOnProperty(name = "app.tdengine.enabled", havingValue = "true")
@Slf4j
public class TDengineService {

    private final JdbcTemplate tdengineJdbc;
    private final int batchSize;
    private final long batchIntervalMs;

    // 批量写入缓冲区 (高性能无锁并发队列)
    private final Queue<Object[]> writeBuffer = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private long lastFlushTime = System.currentTimeMillis();

    public TDengineService(@Qualifier("tdengineJdbcTemplate") JdbcTemplate tdengineJdbc,
                           @Value("${app.tdengine.batch-size:1000}") int batchSize,
                           @Value("${app.tdengine.batch-interval-ms:5000}") long batchIntervalMs) {
        this.tdengineJdbc = tdengineJdbc;
        this.batchSize = batchSize;
        this.batchIntervalMs = batchIntervalMs;
    }

    @PostConstruct
    public void init() {
        createDatabase();
        createSuperTable();
        log.info("TDengine: database and super table initialized");
    }

    // ========== 初始化 ==========

    private void createDatabase() {
        try {
            tdengineJdbc.execute("CREATE DATABASE IF NOT EXISTS iot_telemetry " +
                    "DURATION 365d " +
                    "CACHEMODEL 'LAST_ROW'");
        } catch (Exception e) {
            log.warn("TDengine create database failed (may already exist): {}", e.getMessage());
        }
    }

    private void createSuperTable() {
        try {
            tdengineJdbc.execute(
                    "CREATE STABLE IF NOT EXISTS iot_telemetry.device_telemetry (" +
                    "  ts          TIMESTAMP, " +
                    "  device_id   NCHAR(64), " +
                    "  sensor_id   NCHAR(64), " +
                    "  sensor_type NCHAR(50), " +
                    "  val       DOUBLE, " +
                    "  unit        NCHAR(20) " +
                    ") TAGS (" +
                    "  device_id_tag   NCHAR(64), " +
                    "  product_type    NCHAR(50) " +
                    ")");
        } catch (Exception e) {
            log.warn("TDengine create super table failed: {}", e.getMessage());
        }
    }

    // ========== 数据写入 ==========

    /**
     * 写入一条时序数据
     */
    public void insert(String deviceId, String sensorId, String sensorType,
                       double value, String unit, LocalDateTime ts) {
        Object[] row = {
                Timestamp.valueOf(ts),
                deviceId,
                sensorId,
                sensorType,
                value,
                unit
        };
        writeBuffer.add(row);

        if (writeBuffer.size() >= batchSize || shouldFlush()) {
            flushBuffer();
        }
    }

    private boolean shouldFlush() {
        return System.currentTimeMillis() - lastFlushTime > batchIntervalMs;
    }

    /**
     * 批量写入 TDengine，自动创建子表
     */
    public synchronized void flushBuffer() {
        if (writeBuffer.isEmpty()) return;

        List<Object[]> batch = new ArrayList<>();
        Object[] rowItem;
        while ((rowItem = writeBuffer.poll()) != null) {
            batch.add(rowItem);
            if (batch.size() >= batchSize * 2) break;
        }
        if (batch.isEmpty()) return;
        lastFlushTime = System.currentTimeMillis();

        // 按 deviceId 分组，批量写子表
        Map<String, List<Object[]>> grouped = new LinkedHashMap<>();
        for (Object[] row : batch) {
            String deviceId = (String) row[1];
            grouped.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(row);
        }

        int totalRows = 0;
        for (Map.Entry<String, List<Object[]>> entry : grouped.entrySet()) {
            String deviceId = entry.getKey();
            String subTable = subTableOf(deviceId);

            // 自动创建子表
            ensureSubTable(subTable, deviceId);

            // 批量 INSERT
            StringBuilder sql = new StringBuilder("INSERT INTO iot_telemetry." + subTable +
                    " (ts, device_id, sensor_id, sensor_type, val, unit) VALUES ");

            List<Object> params = new ArrayList<>();
            for (int i = 0; i < entry.getValue().size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append("(?, ?, ?, ?, ?, ?)");
                Object[] row = entry.getValue().get(i);
                params.add(row[0]); // ts
                params.add(row[1]); // device_id
                params.add(row[2]); // sensor_id
                params.add(row[3]); // sensor_type
                params.add(row[4]); // value
                params.add(row[5]); // unit
            }

            try {
                tdengineJdbc.update(sql.toString(), params.toArray());
                totalRows += entry.getValue().size();
            } catch (Exception e) {
                log.error("TDengine batch insert failed for device {}", deviceId, e);
            }
        }
        log.info("TDengine: flushed {} rows to {} sub-tables", totalRows, grouped.size());
    }

    private void ensureSubTable(String subTable, String deviceId) {
        try {
            tdengineJdbc.execute(
                    "CREATE TABLE IF NOT EXISTS iot_telemetry." + subTable +
                    " USING iot_telemetry.device_telemetry " +
                    " TAGS('" + deviceId + "', 'default')");
        } catch (Exception e) {
            log.debug("TDengine sub-table {} already exists", subTable);
        }
    }

    // ========== 数据查询 ==========

    /**
     * 判断异常是否为 TDengine 子表不存在（新设备无数据，正常情况）
     */
    private boolean isTableNotExist(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        return msg.contains("Table does not exist") || msg.contains("0x80002662");
    }

    // deviceId 会拼接进表名（DDL 无法参数化），拼接前必须通过白名单校验
    private static final java.util.regex.Pattern SAFE_DEVICE_ID =
            java.util.regex.Pattern.compile("^[A-Za-z0-9_-]{1,50}$");

    // TDengine INTERVAL 子句无法参数化，仅放行 数字+时间单位 的白名单格式
    private static final java.util.regex.Pattern SAFE_INTERVAL =
            java.util.regex.Pattern.compile("^\\d+[smhdwy]$");

    private String subTableOf(String deviceId) {
        if (!SAFE_DEVICE_ID.matcher(deviceId).matches()) {
            throw new IllegalArgumentException("非法 deviceId: " + deviceId);
        }
        return "d_" + deviceId.replace("-", "_").replace(".", "_");
    }

    private String requireSafeInterval(String interval) {
        if (interval == null || interval.isEmpty()) return null;
        if (!SAFE_INTERVAL.matcher(interval).matches()) {
            throw new IllegalArgumentException("非法 interval: " + interval);
        }
        return interval;
    }

    /**
     * 查询单设备最新 N 条数据
     */
    public List<Map<String, Object>> queryLatest(String deviceId, String sensorId, int limit) {
        String subTable = subTableOf(deviceId);
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        StringBuilder sql = new StringBuilder(
                "SELECT ts, device_id, sensor_id, sensor_type, val , unit " +
                "FROM iot_telemetry." + subTable);
        List<Object> params = new ArrayList<>();
        if (sensorId != null && !sensorId.isEmpty()) {
            sql.append(" WHERE sensor_id = ?");
            params.add(sensorId);
        }
        sql.append(" ORDER BY ts DESC LIMIT ?");
        params.add(safeLimit);

        try {
            return tdengineJdbc.queryForList(sql.toString(), params.toArray());
        } catch (Exception e) {
            if (isTableNotExist(e)) {
                log.debug("TDengine sub-table {} not exist yet (no data for device {})", subTable, deviceId);
            } else {
                log.warn("TDengine query failed for device {}", deviceId, e);
            }
            return List.of();
        }
    }

    /**
     * 查询单设备时间范围数据，带降采样聚合
     */
    public List<Map<String, Object>> queryRange(String deviceId, String sensorId,
                                                 LocalDateTime from, LocalDateTime to,
                                                 String interval) {
        String subTable = subTableOf(deviceId);
        String safeInterval = requireSafeInterval(interval);

        if (safeInterval != null) {
            // 降采样聚合：INTERVAL 使 ts 成为窗口起始时间，GROUP BY 合法
            StringBuilder sql = new StringBuilder(
                    "SELECT ts, sensor_id, AVG(val) as avg_val, MAX(val) as max_val, MIN(val) as min_val " +
                    "FROM iot_telemetry." + subTable +
                    " WHERE ts >= ? AND ts <= ?");
            List<Object> params = new ArrayList<>();
            params.add(Timestamp.valueOf(from));
            params.add(Timestamp.valueOf(to));

            if (sensorId != null && !sensorId.isEmpty()) {
                sql.append(" AND sensor_id = ?");
                params.add(sensorId);
            }

            sql.append(" INTERVAL(").append(safeInterval).append(") FILL(linear)");
            sql.append(" GROUP BY sensor_id");

            try {
                return tdengineJdbc.queryForList(sql.toString(), params.toArray());
            } catch (Exception e) {
                if (isTableNotExist(e)) {
                    log.debug("TDengine sub-table {} not exist yet (no data for device {})", subTable, deviceId);
                } else {
                    log.warn("TDengine range query failed for device {}", deviceId, e);
                }
                return List.of();
            }
        } else {
            // 无降采样：返回原始时序数据（与 queryLatest 列一致，兼容 mapTdRowToDataPoint）
            StringBuilder sql = new StringBuilder(
                    "SELECT ts, device_id, sensor_id, sensor_type, val, unit " +
                    "FROM iot_telemetry." + subTable +
                    " WHERE ts >= ? AND ts <= ?");
            List<Object> params = new ArrayList<>();
            params.add(Timestamp.valueOf(from));
            params.add(Timestamp.valueOf(to));

            if (sensorId != null && !sensorId.isEmpty()) {
                sql.append(" AND sensor_id = ?");
                params.add(sensorId);
            }

            sql.append(" ORDER BY ts");

            try {
                return tdengineJdbc.queryForList(sql.toString(), params.toArray());
            } catch (Exception e) {
                if (isTableNotExist(e)) {
                    log.debug("TDengine sub-table {} not exist yet (no data for device {})", subTable, deviceId);
                } else {
                    log.warn("TDengine range query failed for device {}", deviceId, e);
                }
                return List.of();
            }
        }
    }

    /**
     * 按产品类型聚合查询（跨设备超表查询）
     */
    public List<Map<String, Object>> queryByProductType(String productType, String sensorType,
                                                         LocalDateTime from, LocalDateTime to,
                                                         String interval) {
        String safeInterval = requireSafeInterval(interval);
        StringBuilder sql = new StringBuilder(
                "SELECT ts, AVG(val) as avg_val, MAX(val) as max_val, MIN(val) as min_val " +
                "FROM iot_telemetry.device_telemetry " +
                "WHERE product_type = ? AND ts >= ? AND ts <= ?");
        List<Object> params = new ArrayList<>();
        params.add(productType);
        params.add(Timestamp.valueOf(from));
        params.add(Timestamp.valueOf(to));

        if (sensorType != null && !sensorType.isEmpty()) {
            sql.append(" AND sensor_type = ?");
            params.add(sensorType);
        }

        if (safeInterval != null) {
            sql.append(" INTERVAL(").append(safeInterval).append(")");
        }

        try {
            return tdengineJdbc.queryForList(sql.toString(), params.toArray());
        } catch (Exception e) {
            log.warn("TDengine aggregate query failed for product {}", productType, e);
            return List.of();
        }
    }

    /**
     * 数据清理（按保留天数）
     */
    public void cleanOldData(int retentionDays) {
        try {
            int safeDays = Math.max(1, Math.min(retentionDays, 3650));
            String sql = "DELETE FROM iot_telemetry.device_telemetry WHERE ts < NOW - " + safeDays + "d";
            int deleted = tdengineJdbc.update(sql);
            log.info("TDengine: deleted {} rows older than {} days", deleted, safeDays);
        } catch (Exception e) {
            log.warn("TDengine clean failed", e);
        }
    }

    /**
     * 应用关闭时刷新缓冲区
     */
    public void flushOnShutdown() {
        flushBuffer();
    }
}
