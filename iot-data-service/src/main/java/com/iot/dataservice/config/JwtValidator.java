package com.iot.dataservice.config;
// JWT 校验器：仅做 HS256 签名与过期校验，不做用户细节校验。
// 密钥必须与 iot-backend（app.jwt.secret，环境变量 APP_JWT_SECRET）完全一致，否则其签发的令牌无法通过校验。
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class JwtValidator {

    private final SecretKey key;

    public JwtValidator(@Value("${app.jwt.secret:}") String secret) {
        // fail-fast：密钥未配置或长度不足 32 字节（HS256 最低要求）时拒绝启动，
        // 避免以弱密钥或空密钥对外提供"看似已认证"的服务
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException(
                    "app.jwt.secret（环境变量 APP_JWT_SECRET）未配置；本服务所有 /api/** 端点均需校验"
                            + " iot-backend 签发的 JWT，拒绝启动");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret（环境变量 APP_JWT_SECRET）长度不足 32 字节，当前 " + secretBytes.length
                            + " 字节，不满足 HS256 要求；须与 iot-backend 配置同一密钥");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    /**
     * 仅校验签名有效且未过期，通过即认为已认证
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
