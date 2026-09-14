package com.workflowx.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建角色请求（P3-02）。code 规范: 大写字母开头的大写/数字/下划线，2-50 位。
 */
public record CreateRoleRequest(
        @NotBlank(message = "code 不能为空")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,49}$", message = "code 只能包含大写字母、数字、下划线，且以字母开头，长度 2-50")
        String code,

        @NotBlank(message = "name 不能为空")
        @Size(max = 50, message = "name 最长 50 字符")
        String name,

        @Size(max = 200, message = "description 最长 200 字符")
        String description) {
}
