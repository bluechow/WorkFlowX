package com.workflowx.issue.vo;

import com.workflowx.issue.entity.Issue;

import java.time.LocalDateTime;

/** 全局工作项条目（V17）：跨项目视图，携带项目标识供跳转。 */
public record WorkItemVO(
        Long id,
        Long projectId,
        String projectKey,
        String projectName,
        Long issueNo,
        String title,
        String type,
        String priority,
        String severity,
        String status,
        Long reporterId,
        Long assigneeId,
        LocalDateTime dueDate,
        LocalDateTime updatedAt) {

    public static WorkItemVO of(Issue i, String projectKey, String projectName) {
        return new WorkItemVO(i.getId(), i.getProjectId(), projectKey, projectName,
                i.getIssueNo(), i.getTitle(),
                i.getType() == null ? null : i.getType().name(),
                i.getPriority() == null ? null : i.getPriority().name(),
                i.getSeverity() == null ? null : i.getSeverity().name(),
                i.getStatus() == null ? null : i.getStatus().name(),
                i.getReporterId(), i.getAssigneeId(), i.getDueDate(), i.getUpdatedAt());
    }
}
