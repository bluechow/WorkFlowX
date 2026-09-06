package com.workflowx.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 登录会话服务实现（P2-07，StringRedisTemplate）。
 * TTL 与 JWT 有效期同源（JwtProperties.expireHours），保证会话不会先于/晚于 token 过期。
 * Redis 异常不由本服务吞掉：调用方（Filter）按 fail-closed 处理。
 */
@Service
@RequiredArgsConstructor
public class AuthSessionServiceImpl implements AuthSessionService {

    public static final String SESSION_KEY_PREFIX = "auth:session:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    private String key(Long userId) {
        return SESSION_KEY_PREFIX + userId;
    }

    private Duration ttl() {
        return Duration.ofHours(jwtProperties.getExpireHours());
    }

    @Override
    public void createSession(Long userId, String jti) {
        // 单次带 TTL 的原子写入，杜绝"有值无过期"
        redisTemplate.opsForValue().set(key(userId), jti, ttl());
    }

    @Override
    public String getSessionJti(Long userId) {
        return redisTemplate.opsForValue().get(key(userId));
    }

    @Override
    public boolean isCurrentSession(Long userId, String jti) {
        if (!StringUtils.hasText(jti)) {
            return false;
        }
        return jti.equals(getSessionJti(userId));
    }

    @Override
    public void deleteSession(Long userId) {
        redisTemplate.delete(key(userId));
    }
}
