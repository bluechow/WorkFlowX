package com.workflowx.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Set;

/**
 * 角色权限绑定请求（P3-02）：以权限编码集合整体替换角色当前权限（replace 语义，事务内）。
 * 空集合合法（= 清空角色权限）；null 视为空集合。集合内编码须符合 {resource}:{action} 规范。
 */
public record AssignRolePermissionsRequest(
        Set<@NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{1,49}:[a-z][a-z0-9_]{1,49}$",
                message = "权限编码必须为 {resource}:{action} 两段式小写编码") String> permissionCodes) {

    public AssignRolePermissionsRequest {
        if (permissionCodes == null) {
            permissionCodes = Set.of();
        }
    }
}
