package com.workflowx.issue.dto;

import com.workflowx.issue.entity.IssuePriority;
import com.workflowx.issue.entity.IssueSeverity;
import com.workflowx.issue.entity.IssueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建 Issue 请求（P6-02）。
 * 安全边界: 不含 reporterId（自动取当前用户）/issueNo（服务端分配）/projectId（取自路径）。
 */
public record CreateIssueRequest(
        @NotBlank(message = "title 不能为空")
        @Size(max = 200, message = "title 最长 200 字符")
        String title,

        @Size(max = 65535, message = "description 过长")
        String description,

        @NotNull(message = "type 不能为空")
        IssueType type,

        IssuePriority priority,

        /** 仅 type=BUG 时允许设置；其余类型携带即 400 */
        IssueSeverity severity,

        /** 可空；必须为项目成员 */
        Long assigneeId,

        /** 截止日期（可空；V17） */
        java.time.LocalDateTime dueDate,

        /** 标签 id 集合（可空=不绑定；V17；须属于本项目） */
        java.util.List<Long> labelIds) {
}
