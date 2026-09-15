package com.workflowx.project.entity;

/** 项目状态，对应 projects.status ENUM('ACTIVE','ARCHIVED')（V6）。归档代替物理删除（ADR-014）。 */
public enum ProjectStatus {
    ACTIVE,
    ARCHIVED
}
