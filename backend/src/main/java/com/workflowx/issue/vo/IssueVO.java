package com.workflowx.issue.vo;

import com.workflowx.issue.entity.Issue;
import com.workflowx.issue.entity.IssuePriority;
import com.workflowx.issue.entity.IssueSeverity;
import com.workflowx.issue.entity.IssueStatus;
import com.workflowx.issue.entity.IssueType;

import java.time.LocalDateTime;

/**
 * Issue 视图对象（P6-02）。枚举字段保持类型化（Jackson 序列化为名称字符串，前端无感）。
 * 业务编号 = project.key-issue_no 由前端/调用方拼装（ADR-016，库内不冗余存储）。
 */
public record IssueVO(
        Long id,
        Long projectId,
        Long issueNo,
        String title,
        String description,
        IssueType type,
        IssuePriority priority,
        IssueSeverity severity,
        IssueStatus status,
        Long reporterId,
        Long assigneeId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static IssueVO from(Issue issue) {
        return new IssueVO(issue.getId(), issue.getProjectId(), issue.getIssueNo(),
                issue.getTitle(), issue.getDescription(),
                issue.getType(), issue.getPriority(), issue.getSeverity(), issue.getStatus(),
                issue.getReporterId(), issue.getAssigneeId(),
                issue.getCreatedAt(), issue.getUpdatedAt());
    }
}
