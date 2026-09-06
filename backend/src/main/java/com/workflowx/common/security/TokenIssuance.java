package com.workflowx.common.security;

import java.time.Instant;

/**
 * Token 签发结果（P2-07）：登录流程需要 jti 用于 Redis 会话、expiresIn 用于响应。
 */
public record TokenIssuance(
        String accessToken,
        String jti,
        Instant issuedAt,
        Instant expiresAt) {
}
