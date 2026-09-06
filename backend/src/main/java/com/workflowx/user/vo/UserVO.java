package com.workflowx.user.vo;

import com.workflowx.user.entity.UserStatus;

import java.time.LocalDateTime;

/**
 * 用户视图对象：对外返回的用户信息（Phase 2 各接口统一使用）。
 * 安全约束（P2-01 定稿）：禁止包含 password / passwordHash 任何形态的字段，由 UserVOTest 守护。
 */
public record UserVO(
        Long id,
        String username,
        String email,
        String nickname,
        UserStatus status,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
