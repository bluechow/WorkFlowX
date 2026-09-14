package com.workflowx.rbac.vo;

import com.workflowx.rbac.RbacConstants;
import com.workflowx.rbac.entity.Permission;
import com.workflowx.rbac.entity.PermissionType;

import java.time.LocalDateTime;

/** 权限视图对象（P3-02）。 */
public record PermissionVO(
        Long id,
        String code,
        String name,
        PermissionType type,
        String description,
        boolean system,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static PermissionVO from(Permission permission) {
        return new PermissionVO(
                permission.getId(),
                permission.getCode(),
                permission.getName(),
                permission.getType(),
                permission.getDescription(),
                RbacConstants.isSystemPermission(permission.getCode()),
                permission.getCreatedAt(),
                permission.getUpdatedAt());
    }
}
