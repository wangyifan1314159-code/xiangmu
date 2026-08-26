package com.iot.repository;

import com.iot.model.SystemSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemSettingsRepository extends JpaRepository<SystemSettings, Long> {
    Optional<SystemSettings> findByUserId(Long userId);
}
