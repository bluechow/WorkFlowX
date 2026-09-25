package com.workflowx.rbac;

import java.util.Set;

/**
 * RBAC 系统常量（ADR-012）：系统角色与系统权限受代码守护，不加数据库标记列。
 * 与 V3__rbac_permissions.sql 种子保持一致，由 RbacConstantsTest 守护同步。
 */
public final class RbacConstants {

    /** 系统角色: 禁止删除、code 不可修改 */
    public static final Set<String> SYSTEM_ROLE_CODES = Set.of("ADMIN", "MEMBER");

    /** 系统权限: 禁止删除（与 V3/V5/V7/V8/V10/V11/V12/V14/V15 种子一致，54 项） */
    public static final Set<String> SYSTEM_PERMISSION_CODES = Set.of(
            "user:list", "user:get", "user:create", "user:update", "user:status", "user:assign_role",
            "role:list", "role:get", "role:create", "role:update", "role:delete", "role:assign_permission",
            "permission:list", "permission:get",
            "org:list", "org:get", "org:create", "org:update", "org:delete", "org:assign_member",
            "department:list", "department:get", "department:create", "department:update", "department:delete",
            "project:list", "project:get", "project:create", "project:update", "project:delete",
            "project:assign_member",
            "issue:list", "issue:get", "issue:create", "issue:update", "issue:assign",
            "issue:transition",
            "comment:list", "comment:get", "comment:create", "comment:update", "comment:delete",
            "attachment:list", "attachment:get", "attachment:upload", "attachment:delete",
            "audit:list", "audit:get", "dashboard:view",
            "testcase:list", "testcase:get", "testcase:create", "testcase:update", "testcase:delete");

    private RbacConstants() {
    }

    public static boolean isSystemRole(String code) {
        return SYSTEM_ROLE_CODES.contains(code);
    }

    public static boolean isSystemPermission(String code) {
        return SYSTEM_PERMISSION_CODES.contains(code);
    }
}
