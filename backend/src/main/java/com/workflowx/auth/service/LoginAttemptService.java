package com.workflowx.auth.service;

/**
 * 登录失败计数/锁定服务（P2-13）。
 * 规则（ADR-010）: 同一 username 15 分钟窗口内累计 5 次认证失败 → 锁定（锁定窗口重置为 15 分钟）；
 * 计数对"不存在的 username"同样生效——否则 429 只出现在真实用户名上，会造成账号枚举泄漏。
 * 全部基于 Redis 原子操作（INCR + EXPIRE），异常不吞（fail-closed）。
 */
public interface LoginAttemptService {

    /** 15 分钟窗口内允许的最大失败次数 */
    int MAX_ATTEMPTS = 5;

    /** 窗口/锁定时长（秒） */
    long WINDOW_SECONDS = 900;

    /** 该 username 当前是否处于锁定状态（失败次数 ≥ MAX_ATTEMPTS） */
    boolean isLocked(String username);

    /**
     * 记录一次认证失败（原子 INCR）。
     *
     * @return 窗口内累计失败次数；达到 MAX_ATTEMPTS 时锁定窗口重置为 15 分钟
     */
    long recordFailure(String username);

    /** 清除失败计数（登录成功后调用） */
    void clearFailures(String username);
}
