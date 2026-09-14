package com.workflowx.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建组织请求（P4-01）。code: 大写字母/数字/下划线 2-50（与角色编码同风格）。
 */
public record CreateOrganizationRequest(
        @NotBlank(message = "name 不能为空")
        @Size(max = 100, message = "name 最长 100 字符")
        String name,

        @NotBlank(message = "code 不能为空")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,49}$", message = "code 只能包含大写字母、数字、下划线，且以字母开头，长度 2-50")
        String code,

        @Size(max = 500, message = "description 最长 500 字符")
        String description) {
}
