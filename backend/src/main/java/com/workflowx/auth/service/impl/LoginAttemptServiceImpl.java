package com.workflowx.auth.service.impl;

import com.workflowx.auth.service.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 登录失败计数实现（P2-13，StringRedisTemplate 原子操作）。
 * Key: auth:fail:{username}，Value: 窗口内失败次数，TTL 900s。
 * 并发安全: 计数用 INCR（Redis 原子），首失败（count==1）与触发锁定（count==MAX）各执行一次
 * EXPIRE 900——两个值各自仅可能被一个并发请求命中，无 GET+SET 竞态；不引入额外中间件。
 * Redis 异常不吞: 计数失败将向上抛出，登录按统一服务错误失败（fail-closed，见 ADR-010）。
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptServiceImpl implements LoginAttemptService {

    public static final String FAIL_KEY_PREFIX = "auth:fail:";

    private final StringRedisTemplate redisTemplate;

    private String key(String username) {
        return FAIL_KEY_PREFIX + username;
    }

    @Override
    public boolean isLocked(String username) {
        if (!StringUtils.hasText(username)) {
            return false;
        }
        String count = redisTemplate.opsForValue().get(key(username));
        return count != null && Long.parseLong(count) >= MAX_ATTEMPTS;
    }

    @Override
    public long recordFailure(String username) {
        Long count = redisTemplate.opsForValue().increment(key(username));
        if (count == null) {
            // INCR 理论上不返回 null；防御性处理，视作计数失败
            throw new IllegalStateException("login attempt counter unavailable");
        }
        if (count == 1) {
            // 首次失败: 建立窗口
            redisTemplate.expire(key(username), Duration.ofSeconds(WINDOW_SECONDS));
        } else if (count == MAX_ATTEMPTS) {
            // 第 5 次失败: 进入锁定，锁定窗口重置为 15 分钟
            redisTemplate.expire(key(username), Duration.ofSeconds(WINDOW_SECONDS));
        }
        return count;
    }

    @Override
    public void clearFailures(String username) {
        redisTemplate.delete(key(username));
    }
}
