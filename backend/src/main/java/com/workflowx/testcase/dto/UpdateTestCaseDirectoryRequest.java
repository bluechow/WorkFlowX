package com.workflowx.testcase.dto;

import jakarta.validation.constraints.Size;

/** 更新用例目录请求（Phase 20; ADR-022）：name 重命名；parentId 变更=移动（防环校验）。null=不变。 */
public record UpdateTestCaseDirectoryRequest(
        @Size(max = 100, message = "目录名最长 100 字符")
        String name,

        Long parentId) {
}
