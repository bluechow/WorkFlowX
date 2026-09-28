package com.workflowx.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 修改密码请求（Phase A-④）：新密码规则与创建用户一致（≥8 位且含字母和数字）；
 * 旧密码校验在 Service 层（错误 → 400），成功后作废当前会话（强制重登）。
 */
public record ChangePasswordRequest(
        @NotBlank(message = "oldPassword 不能为空")
        String oldPassword,

        @NotBlank(message = "newPassword 不能为空")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)\\S{8,64}$", message = "newPassword 至少 8 位且需包含字母和数字")
        String newPassword
) {
}
