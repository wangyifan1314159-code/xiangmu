package com.iot.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(name = "app.tdengine.enabled", havingValue = "true")
public class TDengineConfig {

    @Value("${app.tdengine.url}")
    private String url;

    @Value("${app.tdengine.username}")
    private String username;

    @Value("${app.tdengine.password}")
    private String password;

    @Value("${app.tdengine.max-pool-size:10}")
    private int maxPoolSize;

    @Bean
    public JdbcTemplate tdengineJdbcTemplate() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.taosdata.jdbc.TSDBDriver");
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(0);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(2000);
        config.setInitializationFailTimeout(-1);
        config.setConnectionTestQuery("SELECT SERVER_VERSION()");
        HikariDataSource dataSource = new HikariDataSource(config);
        return new JdbcTemplate(dataSource);
    }
}
