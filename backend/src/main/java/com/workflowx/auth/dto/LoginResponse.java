package com.workflowx.auth.dto;

import java.util.List;

/**
 * 登录响应（P2-08）。
 * 安全约束: 不包含 password / password_hash 任何形态字段；roles 供前端展示与后续权限控制。
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Long userId,
        String username,
        List<String> roles) {
}
