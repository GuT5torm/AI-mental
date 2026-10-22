package com.example.aispringboot.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 签发与解析。
 *
 * <p>载荷中固定携带 {@code userId} 与 {@code userType}，{@code userType=2} 表示管理员。
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    private final SecretKey key;
    private final long expiration;

    public JwtUtil(@Value("${jwt.secret:}") String secret,
                   @Value("${jwt.expiration:7200000}") long expiration) {
        // 仓库里不落任何可用密钥：未注入 JWT_SECRET 时用启动期随机密钥兜底，
        // 保证能跑起来，但重启即失效，不会形成共享的默认凭证
        if (secret == null || secret.isBlank()) {
            byte[] bytes = new byte[48];
            new SecureRandom().nextBytes(bytes);
            secret = Base64.getEncoder().encodeToString(bytes);
            log.warn("未配置 JWT_SECRET，本次启动使用随机密钥，重启后所有已签发 token 将失效");
        }
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expiration = expiration;
    }

    public String generateToken(Long userId, Integer userType) {
        Map<String, Object> claims = new HashMap<>(4);
        claims.put("userId", userId);
        claims.put("userType", userType == null ? 1 : userType);
        Date now = new Date();
        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(key)
                .compact();
    }

    /**
     * 解析令牌。过期、签名错误、格式错误一律返回 null，由调用方转成 401，
     * 避免把 jjwt 的异常细节透给前端。
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            return null;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public Long getUserId(Claims claims) {
        Object v = claims.get("userId");
        if (v == null) {
            return null;
        }
        return v instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(v));
    }

    public Integer getUserType(Claims claims) {
        Object v = claims.get("userType");
        if (v == null) {
            return 1;
        }
        return v instanceof Number n ? n.intValue() : Integer.valueOf(String.valueOf(v));
    }
}
