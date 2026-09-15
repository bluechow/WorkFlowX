package com.workflowx.issue.entity;

/**
 * Issue 状态（Master Prompt §14 / V9 ENUM）。
 * 本阶段仅做枚举合法值更新；流转规则（哪些状态可到哪些状态）属 Phase 7 Workflow。
 */
public enum IssueStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    TESTING,
    CLOSED,
    REOPENED
}
