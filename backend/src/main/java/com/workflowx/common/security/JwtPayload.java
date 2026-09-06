package com.workflowx.common.security;

import java.time.Instant;
import java.util.List;

/**
 * 已验证 JWT 的载荷视图（P2-06）。
 * 只承载非敏感信息（userId/username/roles/jti/时间），密码等敏感字段从不进入 JWT。
 * 该记录同时是后续 Spring Security Authentication 的 principal 载体。
 */
public record JwtPayload(
        Long userId,
        String username,
        List<String> roles,
        String jti,
        Instant issuedAt,
        Instant expiresAt) {
}
