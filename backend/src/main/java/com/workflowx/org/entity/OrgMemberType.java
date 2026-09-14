package com.workflowx.org.entity;

/**
 * 组织成员角色，对应 organization_members.role ENUM('OWNER','ADMIN','MEMBER')（V4 真实定义）。
 * OWNER 每组织恰好一个（创建者），不可移除/降级（ADR-013）。
 */
public enum OrgMemberType {
    OWNER,
    ADMIN,
    MEMBER
}
