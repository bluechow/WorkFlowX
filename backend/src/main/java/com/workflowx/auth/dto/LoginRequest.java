package com.workflowx.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求（P2-08）。
 * 错误信息策略: 用户不存在与密码错误返回统一信息，防止用户枚举（状态检查在密码验证通过之后）。
 */
public record LoginRequest(
        @NotBlank(message = "username 不能为空")
        String username,

        @NotBlank(message = "password 不能为空")
        String password) {
}
