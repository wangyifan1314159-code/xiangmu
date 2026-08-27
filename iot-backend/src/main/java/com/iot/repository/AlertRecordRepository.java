package com.iot.repository;

import com.iot.model.AlertRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AlertRecordRepository extends JpaRepository<AlertRecord, Long> {
    //    对告警状态进行查询
    Page<AlertRecord> findByDeviceIdOrderByTriggeredAtDesc(String deviceId, Pageable pageable);

    Page<AlertRecord> findByStatusOrderByTriggeredAtDesc(String status, Pageable pageable);

    Page<AlertRecord> findByLevelOrderByTriggeredAtDesc(String level, Pageable pageable);
    // 多条件组合搜索
    @Query("SELECT a FROM AlertRecord a WHERE "
         + "(:deviceId IS NULL OR a.deviceId = :deviceId) "
         + "AND (:status IS NULL OR a.status = :status) "
         + "AND (:level IS NULL OR a.level = :level) "
         + "AND (:ownerId IS NULL OR a.ownerId = :ownerId) "
         + "ORDER BY a.triggeredAt DESC")
    Page<AlertRecord> search(@Param("deviceId") String deviceId,
                             @Param("status") String status,
                             @Param("level") String level,
                             @Param("ownerId") Long ownerId,
                             Pageable pageable);

//    统计类查询
    @Query("SELECT COUNT(a) FROM AlertRecord a WHERE a.status = 'TRIGGERED' AND a.ownerId = :ownerId")
    long countActiveAlerts(@Param("ownerId") Long ownerId);

    @Query("SELECT COUNT(a) FROM AlertRecord a WHERE a.status = :status AND a.ownerId = :ownerId")
    long countByStatus(@Param("status") String status, @Param("ownerId") Long ownerId);

    @Query("SELECT COUNT(a) FROM AlertRecord a WHERE a.ownerId = :ownerId")
    long countByOwnerId(@Param("ownerId") Long ownerId);

    // 删除设备时清理其告警记录（按归属过滤，避免误删他人数据）
    void deleteByDeviceIdAndOwnerId(String deviceId, Long ownerId);

//    最近告警列表，获取最近的20条告警记录，按触发时间倒序排列
    List<AlertRecord> findTop20ByOrderByTriggeredAtDesc();
}
