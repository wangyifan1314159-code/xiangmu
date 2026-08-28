package com.iot.repository;

import com.iot.model.Sensor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensorRepository extends JpaRepository<Sensor, String> {

    /**
     * sensorId 归属校验：sensor 必须挂在该设备下。
     * SQL 关联查询实现，可在无事务的异步线程安全调用（不触发 LAZY 的 device 关联初始化）
     */
    boolean existsByIdAndDevice_DeviceId(String id, String deviceId);
}
