package com.workflowx.issue.dto;

import com.workflowx.issue.entity.IssuePriority;
import com.workflowx.issue.entity.IssueSeverity;
import com.workflowx.issue.entity.IssueStatus;
import com.workflowx.issue.entity.IssueType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Issue 分页查询条件（P6-04）。
 * keyword 匹配 title/description；issueNo 为项目内精确序号；分页规范与 api-conventions §5 一致（size 上限 100）。
 */
public record IssuePageQuery(
        String keyword,

        Long issueNo,

        IssueType type,

        IssuePriority priority,

        IssueSeverity severity,

        IssueStatus status,

        Long reporterId,

        Long assigneeId,

        /** 标签筛选（V17） */
        Long labelId,

        /** 截止日期范围（含当日；V17） */
        @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
        java.time.LocalDate dueAfter,

        @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
        java.time.LocalDate dueBefore,

        @Min(value = 1, message = "page 最小为 1") Integer page,

        @Min(value = 1, message = "size 最小为 1")
        @Max(value = 100, message = "size 最大为 100") Integer size) {

    public long pageNum() {
        return page == null ? 1 : page;
    }

    public long pageSize() {
        return size == null ? 20 : size;
    }
}
