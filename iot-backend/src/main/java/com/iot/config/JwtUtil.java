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
        String effectiveSecret = StringUtils.hasText(secret) ? secret : "";
        byte[] secretBytes = effectiveSecret.getBytes(StandardCharsets.UTF_8);
        boolean devOrTest = isDevOrTestProfile(activeProfiles);

        if (secretBytes.length >= 32) {
            this.key = Keys.hmacShaKeyFor(secretBytes);
        } else if (devOrTest) {
            // 仅 dev/test 环境保留便利：未配置有效密钥时生成随机临时密钥
            // （不可猜测，但重启后已签发的令牌全部失效，需重新登录）
            byte[] randomBytes = new byte[32];
            new SecureRandom().nextBytes(randomBytes);
            this.key = Keys.hmacShaKeyFor(randomBytes);
            log.warn("dev/test 环境未配置 ≥32 字节的 app.jwt.secret（环境变量 APP_JWT_SECRET），已生成随机临时密钥，重启后所有已登录用户需重新登录");
        } else {
            // 非 dev/test 环境：密钥为空或长度不足 32 字节时拒绝启动，
            // 避免多实例令牌互不兼容、每次发布全员掉线
            throw new IllegalStateException(
                    "app.jwt.secret（环境变量 APP_JWT_SECRET）必须配置且长度 ≥32 字节，当前 " + secretBytes.length
                            + " 字节；拒绝以随机密钥启动");
        }
        this.expirationMs = expirationMs;
    }

    /** 仅当显式激活 dev/test profile 时才允许回退到随机临时密钥 */
    private static boolean isDevOrTestProfile(String activeProfiles) {
        if (activeProfiles == null || activeProfiles.isBlank()) {
            return false;
        }
        for (String profile : activeProfiles.split(",")) {
            String p = profile.trim();
            if ("dev".equalsIgnoreCase(p) || "test".equalsIgnoreCase(p)) {
                return true;
            }
        }
        return false;
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
