package com.workflowx.auth.dto;

import java.util.List;

/**
 * 在线会话条目（Phase A-⑤）：Redis 会话键 + 用户信息聚合。
 * expiresInSeconds 为会话 TTL 剩余值（与 token 有效期同源）。
 */
public record SessionVO(
        Long userId,
        String username,
        String nickname,
        List<String> roles,
        Long expiresInSeconds,
        boolean self
) {
}
