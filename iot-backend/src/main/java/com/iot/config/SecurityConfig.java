package com.iot.config;
// Spring Security 的核心配置类，作用是：定义整个系统的安全策略（认证 + 授权 + 请求过滤链 + 无状态会话模型），同时集成 JWT 和设备 API Key 双认证机制。
import com.iot.repository.DeviceRepository;
import com.iot.repository.UserRepository;
import com.iot.service.AuthService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final AuthService authService;
    private final CorsConfigurationSource corsConfigurationSource;
    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;

    public SecurityConfig(JwtUtil jwtUtil, @Lazy AuthService authService,
                          @org.springframework.beans.factory.annotation.Qualifier("corsConfigurationSource") CorsConfigurationSource corsConfigurationSource,
                          DeviceRepository deviceRepository,
                          UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.authService = authService;
        this.corsConfigurationSource = corsConfigurationSource;
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter() {
        return new JwtAuthFilter(jwtUtil, authService);
    }

    @Bean
    public DeviceApiKeyFilter deviceApiKeyFilter() {
        return new DeviceApiKeyFilter(deviceRepository, userRepository);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 认证与健康检查入口匿名放行
                .requestMatchers("/api/auth/login", "/api/auth/register",
                                 "/api/auth/send-code", "/api/auth/login-by-phone").permitAll()
                .requestMatchers("/api/health/**").permitAll()
                // 全局仿真会写入所有用户的设备，仅管理员可用
                .requestMatchers("/api/simulation/**").hasRole("ADMIN")
                // 数据面：用户/管理员，以及设备 API Key（ROLE_DEVICE 仅允许此路径）
                .requestMatchers("/api/data/**").hasAnyRole("USER", "ADMIN", "DEVICE")
                // 其余 API 面向登录用户
                .requestMatchers("/api/**").hasAnyRole("USER", "ADMIN")
                // 监控端点：健康检查匿名可访问（部署探针），其余仅管理员
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                // WebSocket 握手放行，认证在 STOMP CONNECT 帧校验（见 WebSocketConfig）
                .requestMatchers("/ws/**").permitAll()
                // SPA 页面与静态资源（GET 回退到 index.html 由 SpaWebConfig 处理）
                .requestMatchers(HttpMethod.GET, "/**").permitAll()
                // 默认拒绝
                .anyRequest().authenticated()
            )
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(ct -> {})
                .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            )
            .addFilterBefore(deviceApiKeyFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
