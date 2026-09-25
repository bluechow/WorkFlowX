package com.workflowx.testplan.dto;

import jakarta.validation.constraints.Size;

/** 更新测试计划请求（V1.1; ADR-023）：name/status 可选，null=不变。 */
public record UpdateTestPlanRequest(
        @Size(max = 200, message = "name 最长 200 字符")
        String name,

        /** NOT_STARTED / RUNNING / COMPLETED */
        String status) {
}
