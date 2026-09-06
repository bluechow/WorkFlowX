package com.workflowx.user.dto;

import com.workflowx.user.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 更新用户状态请求（P2-03）。仅支持库内定义的三种状态，非法状态由 Bean Validation 拒绝。
 */
public record UpdateUserStatusRequest(
        @NotNull(message = "status 不能为空")
        UserStatus status) {
}
