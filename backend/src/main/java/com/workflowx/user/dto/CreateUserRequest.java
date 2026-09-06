package com.workflowx.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建用户请求（P2-03）。
 * 格式级校验由 Bean Validation 承担；唯一性/业务校验由 UserService 承担。
 * password 规则: ≥8 位且同时包含字母和数字（P2-04 基线），最长 64（BCrypt 72 字节上限内）。
 */
public record CreateUserRequest(
        @NotBlank(message = "username 不能为空")
        @Pattern(regexp = "^[a-zA-Z0-9_]{3,32}$", message = "username 只能包含字母、数字、下划线，长度 3-32")
        String username,

        @NotBlank(message = "email 不能为空")
        @Email(message = "email 格式不合法")
        @Size(max = 100, message = "email 最长 100 字符")
        String email,

        @NotBlank(message = "password 不能为空")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)\\S{8,64}$", message = "password 至少 8 位且需包含字母和数字")
        String password,

        @Size(max = 50, message = "nickname 最长 50 字符")
        String nickname) {
}
