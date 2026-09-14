package com.workflowx.rbac.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新角色请求（P3-02）。安全边界: 不含 code——角色编码不可修改（ADR-012）。
 */
public record UpdateRoleRequest(
        @Size(max = 50, message = "name 最长 50 字符")
        String name,

        @Size(max = 200, message = "description 最长 200 字符")
        String description) {
}
