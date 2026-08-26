package com.iot.config;
// JWT工具类
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;

@Component
@Slf4j
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${app.jwt.secret:}") String secret,
                   @Value("${app.jwt.expiration-ms}") long expirationMs,
                   @Value("${spring.profiles.active:}") String activeProfiles) {
        if (StringUtils.hasText(secret)) {
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        } else {
            // 生产/deploy profile 下必须显式注入 APP_JWT_SECRET，否则拒绝启动
            // （随机密钥会导致多实例令牌互不兼容、每次发布全员掉线）
            for (String profile : activeProfiles.split(",")) {
                if ("deploy".equalsIgnoreCase(profile.trim()) || "prod".equalsIgnoreCase(profile.trim())) {
                    throw new IllegalStateException(
                            "生产环境必须通过环境变量 APP_JWT_SECRET 注入 ≥32 字节的 JWT 密钥后才能启动");
                }
            }
            // 开发环境未配置时生成随机密钥：
            // 不可猜测，但应用重启后已签发的令牌全部失效（需重新登录）
            byte[] randomBytes = new byte[32];
            new SecureRandom().nextBytes(randomBytes);
            this.key = Keys.hmacShaKeyFor(randomBytes);
            log.warn("未配置 app.jwt.secret（环境变量 APP_JWT_SECRET），已生成随机临时密钥，重启后所有已登录用户需重新登录");
        }
        this.expirationMs = expirationMs;
    }

    public String generateToken(String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
