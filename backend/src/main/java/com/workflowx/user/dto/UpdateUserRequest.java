package com.workflowx.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新用户基本信息请求（P2-03）。
 * 安全边界：本 DTO 不包含 id/username/password 字段——普通更新接口不允许修改
 * 用户名与密码（密码修改后续单独设计），由 UserVOTest 风格的 DTO 守护测试保证。
 */
public record UpdateUserRequest(
        @NotBlank(message = "email 不能为空")
        @Email(message = "email 格式不合法")
        @Size(max = 100, message = "email 最长 100 字符")
        String email,

        @Size(max = 50, message = "nickname 最长 50 字符")
        String nickname) {
}
