package com.workflowx.issue.dto;

import com.workflowx.issue.entity.IssueStatus;
import jakarta.validation.constraints.NotNull;

/** Issue 状态更新请求（P6-05）：仅校验枚举合法；流转矩阵属 Phase 7。 */
public record UpdateIssueStatusRequest(
        @NotNull(message = "status 不能为空")
        IssueStatus status) {
}
