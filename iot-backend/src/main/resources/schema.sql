-- ============================================
-- IoT Cloud Platform - PostgreSQL Schema
-- ============================================

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    phone VARCHAR(20),
    password VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 设备表
CREATE TABLE IF NOT EXISTS devices (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    location VARCHAR(100),
    owner_id BIGINT NOT NULL,
    description VARCHAR(500),
    last_active TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_devices_owner ON devices(owner_id);

-- 传感器表
CREATE TABLE IF NOT EXISTS sensors (
    id VARCHAR(50) PRIMARY KEY,
    device_id BIGINT REFERENCES devices(id),
    name VARCHAR(50) NOT NULL,
    type VARCHAR(30) NOT NULL,
    unit VARCHAR(20),
    value DOUBLE PRECISION DEFAULT 0.0,
    min_val DOUBLE PRECISION DEFAULT 0.0,
    max_val DOUBLE PRECISION DEFAULT 100.0
);

-- 时序数据点表 (TDengine 不可用时的 fallback)
CREATE TABLE IF NOT EXISTS data_points (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(50) NOT NULL,
    sensor_id VARCHAR(50) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    owner_id BIGINT NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_dp_device ON data_points(device_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_dp_device_sensor ON data_points(device_id, sensor_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_dp_owner ON data_points(owner_id);

-- 命令日志表
CREATE TABLE IF NOT EXISTS command_logs (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(50) NOT NULL,
    command VARCHAR(100) NOT NULL,
    params VARCHAR(2000),
    status VARCHAR(20) NOT NULL DEFAULT 'SENT',
    response VARCHAR(500),
    owner_id BIGINT NOT NULL,
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_cmd_owner ON command_logs(owner_id);

-- 告警规则表
CREATE TABLE IF NOT EXISTS alert_rules (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    product_type VARCHAR(50),
    device_id VARCHAR(50),
    sensor_type VARCHAR(50),
    condition_expr VARCHAR(512) NOT NULL,
    level VARCHAR(20) NOT NULL DEFAULT 'WARNING',
    debounce_seconds INT DEFAULT 60,
    enabled BOOLEAN DEFAULT TRUE,
    notify_email BOOLEAN DEFAULT FALSE,
    notify_sms BOOLEAN DEFAULT FALSE,
    notify_push BOOLEAN DEFAULT TRUE,
    description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 告警记录表
CREATE TABLE IF NOT EXISTS alert_records (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(50) NOT NULL,
    device_name VARCHAR(128),
    rule_id BIGINT,
    rule_name VARCHAR(128),
    level VARCHAR(20) NOT NULL DEFAULT 'WARNING',
    title VARCHAR(256) NOT NULL,
    detail TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'TRIGGERED',
    triggered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    acknowledged_at TIMESTAMP,
    resolved_at TIMESTAMP,
    owner_id BIGINT
);
CREATE INDEX IF NOT EXISTS idx_alert_device ON alert_records(device_id, triggered_at DESC);
CREATE INDEX IF NOT EXISTS idx_alert_status ON alert_records(status, triggered_at);
CREATE INDEX IF NOT EXISTS idx_alert_owner ON alert_records(owner_id, triggered_at DESC);

-- 系统设置表
CREATE TABLE IF NOT EXISTS system_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    notify_email BOOLEAN DEFAULT TRUE,
    notify_sms BOOLEAN DEFAULT FALSE,
    notify_push BOOLEAN DEFAULT TRUE,
    alert_threshold INT DEFAULT 80,
    auto_refresh BOOLEAN DEFAULT TRUE,
    refresh_interval INT DEFAULT 5,
    data_retention INT DEFAULT 30,
    theme VARCHAR(20) DEFAULT 'light',
    language VARCHAR(10) DEFAULT 'zh-CN',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
