package com.workflowx.issue.dto;

import jakarta.validation.constraints.NotNull;

/** 创建工作项关联（V17）：targetIssueId 须为同项目工作项且非自身。 */
public record CreateIssueLinkRequest(
        @NotNull(message = "targetIssueId 不能为空")
        Long targetIssueId,

        IssueLinkType linkType) {

    public enum IssueLinkType { RELATES, BLOCKS }
}
