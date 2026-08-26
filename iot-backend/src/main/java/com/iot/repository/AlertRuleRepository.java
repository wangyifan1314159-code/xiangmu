package com.iot.repository;
import com.iot.model.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {
    List<AlertRule> findByEnabledTrue();  // 查询启用所有的规则

    List<AlertRule> findByDeviceIdAndEnabledTrue(String deviceId);  // 按照设备查询专属规则进行查询

    List<AlertRule> findByProductTypeAndDeviceIdIsNullAndEnabledTrue(String productType); // 按产品类型查询"通用规则"（无绑定具体设备）

    List<AlertRule> findByProductTypeAndEnabledTrue(String productType);  // 按产品类型查询所有规则（不管是否绑定设备）

    long countByEnabledTrue();  // 统计启用规则数量

    // ===== 多租户归属过滤 =====
    List<AlertRule> findAllByOwnerId(Long ownerId);

    Optional<AlertRule> findByIdAndOwnerId(Long id, Long ownerId);

    /** 指定设备且归属指定用户的启用规则 */
    List<AlertRule> findByDeviceIdAndEnabledTrueAndOwnerId(String deviceId, Long ownerId);

    /** 指定产品类型、未绑定设备且归属指定用户的启用规则 */
    List<AlertRule> findByProductTypeAndDeviceIdIsNullAndEnabledTrueAndOwnerId(String productType, Long ownerId);
}
