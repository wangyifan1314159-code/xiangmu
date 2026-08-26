package com.iot.repository;

import com.iot.model.DataPoint;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataPointRepository extends JpaRepository<DataPoint, Long> {

    List<DataPoint> findByDeviceIdAndOwnerIdOrderByTimestampDesc(String deviceId, Long ownerId, Limit limit);

    List<DataPoint> findByDeviceIdAndSensorIdAndOwnerIdOrderByTimestampDesc(String deviceId, String sensorId, Long ownerId, Limit limit);

    List<DataPoint> findByOwnerIdOrderByTimestampDesc(Long ownerId);

    void deleteByTimestampBeforeAndOwnerId(java.time.LocalDateTime before, Long ownerId);

    void deleteByTimestampBefore(java.time.LocalDateTime before);

    void deleteByDeviceIdAndOwnerId(String deviceId, Long ownerId);
}
