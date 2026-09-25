package com.workflowx.testplan.dto;

import com.workflowx.testplan.entity.TestPlanItem;
import jakarta.validation.constraints.NotNull;

/** 执行条目请求（V1.1; ADR-023）：result 必填；note 可选；issueId 仅 FAIL/BLOCKED。 */
public record ExecuteItemRequest(
        @NotNull(message = "result 不能为空")
        TestPlanItem.ItemResult result,

        @jakarta.validation.constraints.Size(max = 500, message = "note 最长 500 字符")
        String note,

        Long issueId) {
}
