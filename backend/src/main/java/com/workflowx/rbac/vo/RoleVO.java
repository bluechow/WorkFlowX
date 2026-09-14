package com.workflowx.rbac.vo;

import com.workflowx.rbac.RbacConstants;
import com.workflowx.rbac.entity.Role;

import java.time.LocalDateTime;

/** 角色视图对象（P3-02）。 */
public record RoleVO(
        Long id,
        String code,
        String name,
        String description,
        boolean system,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static RoleVO from(Role role) {
        return new RoleVO(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                RbacConstants.isSystemRole(role.getCode()),
                role.getCreatedAt(),
                role.getUpdatedAt());
    }
}
