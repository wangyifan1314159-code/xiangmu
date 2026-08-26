-- ============================================
-- IoT Cloud Platform - PostgreSQL Seed Data
-- 注意: DataInitializer.java 也会创建种子数据
-- 此文件仅在首次建表时执行
-- ============================================

-- 设备种子数据 (owner_id=1 对应 admin 用户)
INSERT INTO devices (device_id, name, type, status, location, description, owner_id, last_active, created_at) VALUES
('dev_001', '温湿度传感器-A1', '温湿度传感器', 'ONLINE',   '实验室1区', '用于监测实验室温度和湿度', 1, NOW(), NOW()),
('dev_002', '光照传感器-B2',   '光照传感器',   'ONLINE',   '大棚区A',  '农业大棚光照监测',       1, NOW(), NOW()),
('dev_003', '空气质量监测-C1', '空气质量传感器', 'WARNING', '办公室',   '室内空气质量监测设备',  1, NOW() - INTERVAL '1 hour', NOW()),
('dev_004', '智能电表-D1',     '电能监测',      'OFFLINE',  '配电房',   '配电房电能消耗监测',    1, NOW() - INTERVAL '1 day', NOW())
ON CONFLICT (device_id) DO NOTHING;

-- 传感器种子数据
INSERT INTO sensors (id, device_id, name, type, unit, value, min_val, max_val) VALUES
('s_001', 1, '温度',   'temperature', '°C',      24.5,  -10,  60),
('s_002', 1, '湿度',   'humidity',    '%RH',     65.0,   0,   100),
('s_003', 2, '光照强度', 'light',      'lux',     12500,  0,   100000),
('s_004', 3, 'PM2.5',  'pm25',        'μg/m³',   35.0,   0,   500),
('s_005', 3, 'CO2',    'co2',         'ppm',     800.0,  0,   5000),
('s_006', 3, 'TVOC',   'tvoc',        'mg/m³',   0.5,    0,   10),
('s_007', 4, '电压',   'voltage',     'V',       220.0,  0,   380),
('s_008', 4, '电流',   'current',     'A',       5.2,    0,   50),
('s_009', 4, '功率',   'power',       'W',       1144.0, 0,   10000)
ON CONFLICT (id) DO NOTHING;

-- 时序数据种子
INSERT INTO data_points (device_id, sensor_id, value, owner_id, timestamp) VALUES
('dev_001', 's_001', 23.5, 1, NOW() - INTERVAL '60 minutes'),
('dev_001', 's_001', 23.8, 1, NOW() - INTERVAL '50 minutes'),
('dev_001', 's_001', 24.1, 1, NOW() - INTERVAL '40 minutes'),
('dev_001', 's_001', 24.3, 1, NOW() - INTERVAL '30 minutes'),
('dev_001', 's_001', 24.5, 1, NOW() - INTERVAL '20 minutes'),
('dev_001', 's_001', 24.2, 1, NOW() - INTERVAL '10 minutes'),
('dev_001', 's_001', 24.5, 1, NOW()),
('dev_001', 's_002', 63.0, 1, NOW() - INTERVAL '60 minutes'),
('dev_001', 's_002', 64.0, 1, NOW() - INTERVAL '30 minutes'),
('dev_001', 's_002', 65.0, 1, NOW()),
('dev_002', 's_003', 12000, 1, NOW() - INTERVAL '30 minutes'),
('dev_002', 's_003', 12500, 1, NOW());
