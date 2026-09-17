package com.workflowx.issue.dto;

import com.workflowx.issue.entity.IssueStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 状态流转请求（P7-04，ADR-017）。
 * fromStatus 必填: 服务端以此做条件 UPDATE（乐观并发），防止"最后写入覆盖"。
 * toStatus 合法性由正式矩阵校验（非法 → 409）。
 */
public record TransitionIssueStatusRequest(
        @NotNull(message = "fromStatus 不能为空（并发保护需基于当前状态）")
        IssueStatus fromStatus,

        @NotNull(message = "toStatus 不能为空")
        IssueStatus toStatus) {

    /** 非法使用防御: 相同状态流转属于非法 transition（矩阵不包含自环） */
    public boolean isSelfTransition() {
        return fromStatus == toStatus;
    }
}
