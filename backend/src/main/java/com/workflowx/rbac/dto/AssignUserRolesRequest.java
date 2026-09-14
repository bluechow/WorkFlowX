package com.workflowx.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * 用户角色替换请求（P3-03）：以角色编码集合整体替换用户角色（replace 语义，事务内）。
 * 空集合合法（= 清空用户全部角色）；角色编码规范同 CreateRoleRequest。
 */
public record AssignUserRolesRequest(
        @Size(max = 50, message = "角色数量上限 50")
        Set<@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,49}$",
                message = "角色编码只能包含大写字母、数字、下划线，且以字母开头，长度 2-50") String> roleCodes) {

    public AssignUserRolesRequest {
        if (roleCodes == null) {
            roleCodes = Set.of();
        }
    }
}
