package com.workflowx.testplan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建测试计划请求（V1.1; ADR-023）。createdBy/projectId 服务端绑定；用例经 /items 接口添加。 */
public record CreateTestPlanRequest(
        @NotBlank(message = "name 不能为空")
        @Size(max = 200, message = "name 最长 200 字符")
        String name) {
}
