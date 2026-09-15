package com.workflowx.project.entity;

/**
 * 项目成员角色，对应 project_members.role ENUM('OWNER','MANAGER','MEMBER')（V8，ADR-015）。
 * OWNER 为创建者，唯一，不可移除/降级。
 */
public enum ProjectMemberRole {
    OWNER,
    MANAGER,
    MEMBER
}
