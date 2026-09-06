package com.workflowx.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JWT 服务实现（P2-06，jjwt 0.12.x）。
 * 安全要点:
 * - 算法固定 signWith(key, HS256)，解析固定 verifyWith(同一 SecretKey)——alg=none / 算法混淆天然被拒绝
 * - 密钥仅来自 JwtProperties（环境变量/配置），绝不硬编码
 * - payload 只含 userId/username/roles/jti/时间，无任何敏感字段
 */
@Service
@RequiredArgsConstructor
public class JwtServiceImpl implements JwtService {

    private final JwtProperties properties;

    private volatile SecretKey key;

    private SecretKey key() {
        if (key == null) {
            synchronized (this) {
                if (key == null) {
                    if (!StringUtils.hasText(properties.getSecret())) {
                        throw new IllegalStateException("jwt.secret 未配置（生产环境必须通过环境变量提供）");
                    }
                    // 密钥长度不足 32 字节时 hmacShaKeyFor 抛 WeakKeyException，启动即失败
                    key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        return key;
    }

    @Override
    public String generateToken(Long userId, String username, Collection<String> roles) {
        Instant now = Instant.now();
        return generateToken(userId, username, roles, now, now.plusSeconds(properties.getExpireHours() * 3600L));
    }

    /** 供测试与特殊场景使用的完整时间控制重载（生产代码使用上方委托） */
    public String generateToken(Long userId, String username, Collection<String> roles, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("roles", roles == null ? List.of() : List.copyOf(roles))
                .issuer(properties.getIssuer())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key(), Jwts.SIG.HS256)
                .compact();
    }

    @Override
    public JwtPayload parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key())
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String subject = claims.getSubject();
        String username = claims.get("username", String.class);
        List<String> roles = claims.get("roles", List.class);
        Date issuedAt = claims.getIssuedAt();
        Date expiresAt = claims.getExpiration();
        if (!StringUtils.hasText(subject) || !StringUtils.hasText(username)
                || roles == null || issuedAt == null || expiresAt == null) {
            // 缺失必要 claims 视为无效 token（与签名失败同等拒绝）
            throw new JwtException("token 缺失必要 claims");
        }
        return new JwtPayload(
                Long.valueOf(subject),
                username,
                List.copyOf(roles),
                claims.getId(),
                issuedAt.toInstant(),
                expiresAt.toInstant());
    }

    @Override
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
