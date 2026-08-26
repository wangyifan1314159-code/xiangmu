package com.iot;

import com.iot.service.DataService;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
@EnableScheduling
public class IotApplication {

    private final DataService dataService;

    public IotApplication(DataService dataService) {
        this.dataService = dataService;
    }

    public static void main(String[] args) {
        SpringApplication.run(IotApplication.class, args);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @PreDestroy
    public void onShutdown() {
        // 确保 DataService 缓冲区在关闭前刷入数据库
        dataService.flushOnShutdown();
    }
}
