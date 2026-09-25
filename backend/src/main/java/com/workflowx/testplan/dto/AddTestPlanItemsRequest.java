package com.workflowx.testplan.dto;

import jakarta.validation.constraints.NotEmpty;

/** 向计划添加用例请求（V1.1; ADR-023）：用例须属于本项目且未在计划内。 */
public record AddTestPlanItemsRequest(
        @NotEmpty(message = "caseIds 不能为空")
        java.util.List<Long> caseIds) {
}
