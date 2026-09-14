package com.workflowx.rbac.entity;

/**
 * 权限类型，对应 permissions.type ENUM('MENU','API','BUTTON')（V1 真实定义）。
 * MyBatis-Plus 默认按枚举名映射，与数据库取值一致。
 */
public enum PermissionType {
    MENU,
    API,
    BUTTON
}
