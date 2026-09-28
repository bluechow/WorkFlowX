package com.workflowx.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改个人资料请求（Phase A-④）：仅允许改自己的 email/nickname。
 * username/roles/status 不可自改——分别由唯一键与 ADMIN 权限边界保护。
 */
public record UpdateProfileRequest(
        @NotBlank(message = "email 不能为空")
        @Email(message = "email 格式不合法")
        @Size(max = 100, message = "email 最长 100 字符")
        String email,

        @Size(max = 50, message = "nickname 最长 50 字符")
        String nickname
) {
}
