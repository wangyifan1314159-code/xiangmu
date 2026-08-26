package com.iot.config;
// 全局跨域CORS配置类，用于解决跨域请求拦截问题
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    /**
     * 允许的来源白名单：默认前端开发服务器(5173) + 同源部署地址(8080)。
     * 生产同源部署（前端打进 jar 由 8080 直接服务）不需要跨域，此处仅作兜底。
     * 通过 app.cors.allowed-origins 配置（逗号分隔）。
     */
    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:8080,http://127.0.0.1:5173}")
    private List<String> allowedOrigins;

    @Bean
    @org.springframework.context.annotation.Primary
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
