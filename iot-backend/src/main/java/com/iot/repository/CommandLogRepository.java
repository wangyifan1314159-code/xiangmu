package com.iot.repository;

import com.iot.model.CommandLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
// 记录和查询用户/系统向设备下发控制指令的历史日志
public interface CommandLogRepository extends JpaRepository<CommandLog, Long> {
// 查询某设备下某用户的命令历史
    List<CommandLog> findByDeviceIdAndOwnerIdOrderBySentAtDesc(String deviceId, Long ownerId);
// 查询某用户最近的 20 条命令记录
    List<CommandLog> findTop20ByOwnerIdOrderBySentAtDesc(Long ownerId);

    void deleteByDeviceIdAndOwnerId(String deviceId, Long ownerId);
}
