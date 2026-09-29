package com.workflowx.issue.dto;

import com.workflowx.issue.entity.IssuePriority;
import com.workflowx.issue.entity.IssueStatus;
import com.workflowx.issue.entity.IssueType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * 全局工作项查询（V17，Final Edition FP-1）。
 * scope: all=全部 / assigned=指派给我 / todo=待我处理（未完结）/ created=我创建的。
 * 读语义与项目内列表一致（issue:list authority）。
 */
public record WorkItemPageQuery(
        @Pattern(regexp = "all|assigned|todo|created", message = "scope 仅允许 all/assigned/todo/created")
        String scope,

        String keyword,

        IssueType type,

        IssuePriority priority,

        IssueStatus status,

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
