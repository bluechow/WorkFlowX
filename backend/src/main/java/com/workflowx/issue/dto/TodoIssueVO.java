package com.workflowx.issue.dto;

import com.workflowx.issue.entity.IssuePriority;
import com.workflowx.issue.entity.IssueSeverity;
import com.workflowx.issue.entity.IssueStatus;
import com.workflowx.issue.entity.IssueType;

/**
 * 「我的待办」条目（Phase A-④）：跨项目聚合指派给我的未完结 Issue，
 * 携带项目标识供前端跳转与展示（issueNo 由前端拼 key-no）。
 */
public record TodoIssueVO(
        Long issueId,
        Long projectId,
        String projectKey,
        String projectName,
        Long issueNo,
        String title,
        IssueType type,
        IssuePriority priority,
        IssueSeverity severity,
        IssueStatus status,
        String updatedAt
) {
}
