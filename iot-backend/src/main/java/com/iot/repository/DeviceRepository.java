package com.iot.repository;

import com.iot.model.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    // 所有查询均按 ownerId 隔离
    List<Device> findAllByOwnerId(Long ownerId);

    Optional<Device> findByDeviceIdAndOwnerId(String deviceId, Long ownerId);

    Optional<Device> findByDeviceId(String deviceId);

    void deleteByDeviceIdAndOwnerId(String deviceId, Long ownerId);

    @Query("SELECT COUNT(d) FROM Device d WHERE d.status = ?1 AND d.ownerId = ?2")
    long countByStatusAndOwnerId(String status, Long ownerId);

    @Query("SELECT COUNT(d) FROM Device d WHERE d.ownerId = ?1")
    long countByOwnerId(Long ownerId);

    Optional<Device> findByApiKey(String apiKey);
}
