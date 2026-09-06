package com.workflowx.user.entity;

/**
 * 用户状态，对应 users.status ENUM('ACTIVE','DISABLED','LOCKED')（V1 真实定义）。
 * MyBatis-Plus 默认按枚举名映射，与数据库取值一致，无需 @EnumValue。
 */
public enum UserStatus {
    ACTIVE,
    DISABLED,
    LOCKED
}
