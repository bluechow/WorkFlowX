package com.workflowx.issue.dto;

import com.workflowx.issue.entity.IssuePriority;
import com.workflowx.issue.entity.IssueSeverity;
import jakarta.validation.constraints.Size;

/**
 * 更新 Issue 请求（P6-02）。
 * 安全边界: 不含 issueNo/projectId/reporterId/status（status 走独立端点，key/project/reporter 不可变）。
 * assigneeId 非空时重新校验项目成员身份；传 null 表示取消分派。
 */
public record UpdateIssueRequest(
        @Size(max = 200, message = "title 最长 200 字符")
        String title,

        @Size(max = 65535, message = "description 过长")
        String description,

        IssuePriority priority,

        IssueSeverity severity,

        Long assigneeId) {
}
