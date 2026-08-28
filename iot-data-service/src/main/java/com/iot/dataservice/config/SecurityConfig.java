package com.iot.dataservice.config;
// Spring Security 核心配置：所有 /api/** 端点强制 JWT 认证（HS256，密钥与 iot-backend 共用），
// 本服务为无状态查询服务，不引入会话；本模块无 actuator 依赖与 management 配置，故无匿名放行端点。
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtValidator jwtValidator) throws Exception {
        http
            // 纯令牌校验，无表单/跨站场景，禁用 csrf；同源代理调用，CORS 保持默认（不配置）
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 所有业务查询端点必须携带有效 JWT
                .requestMatchers("/api/**").authenticated()
                // 兜底：其余任何请求一律要求认证（fail-closed）
                .anyRequest().authenticated()
            )
            // 未携带或校验失败的请求统一返回 401 JSON
            .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"未认证\"}");
            }))
            .addFilterBefore(new JwtAuthFilter(jwtValidator), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 从 Authorization: Bearer 头提取令牌，交由 JwtValidator 做 HS256 签名+过期校验；
     * 校验通过即写入 SecurityContext（不加载用户细节），失败则不写入，由 entryPoint 统一返回 401
     */
    static class JwtAuthFilter extends OncePerRequestFilter {

        private final JwtValidator jwtValidator;

        JwtAuthFilter(JwtValidator jwtValidator) {
            this.jwtValidator = jwtValidator;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            // 预检请求无需令牌，直接放行交给后续处理
            if (!HttpMethod.OPTIONS.matches(request.getMethod())) {
                String token = extractToken(request);
                if (StringUtils.hasText(token) && jwtValidator.validateToken(token)) {
                    Authentication authentication =
                            new UsernamePasswordAuthenticationToken(token, null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
            filterChain.doFilter(request, response);
        }

        private String extractToken(HttpServletRequest request) {
            String bearer = request.getHeader("Authorization");
            if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
                return bearer.substring(7);
            }
            return null;
        }
    }
}
