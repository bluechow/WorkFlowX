package com.workflowx.testcase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建用例目录请求（Phase 20; ADR-022）。parentId 为空=根目录；不接受 createdBy（服务端绑定）。 */
public record CreateTestCaseDirectoryRequest(
        @NotBlank(message = "目录名不能为空")
        @Size(max = 100, message = "目录名最长 100 字符")
        String name,

        Long parentId) {
}
