package com.workflowx.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建权限请求（P3-02）。code 规范: {resource}:{action} 小写 snake_case（ADR-012）。
 */
public record CreatePermissionRequest(
        @NotBlank(message = "code 不能为空")
        @Pattern(regexp = "^[a-z][a-z0-9_]{1,49}:[a-z][a-z0-9_]{1,49}$",
                message = "code 必须为 {resource}:{action} 两段式小写编码，每段以字母开头、长度 2-50")
        String code,

        @NotBlank(message = "name 不能为空")
        @Size(max = 50, message = "name 最长 50 字符")
        String name,

        @Size(max = 200, message = "description 最长 200 字符")
        String description) {
}
