package com.workflowx.testcase.dto;

import com.workflowx.testcase.entity.TestCasePriority;
import com.workflowx.testcase.entity.TestCaseStatus;
import com.workflowx.testcase.entity.TestCaseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建测试用例请求（Phase 20; ADR-022）。
 * 安全边界: 不含 createdBy（服务端绑定）/testcaseNo（服务端行锁分配）/projectId（取自路径）。
 * status 仅接受 DRAFT/ACTIVE（DEPRECATED 经更新流转）。
 */
public record CreateTestCaseRequest(
        @NotBlank(message = "title 不能为空")
        @Size(max = 200, message = "title 最长 200 字符")
        String title,

        @Size(max = 65535, message = "preconditions 过长")
        String preconditions,

        @Size(max = 65535, message = "steps 过长")
        String steps,

        @Size(max = 65535, message = "expected 过长")
        String expected,

        @NotNull(message = "caseType 不能为空")
        TestCaseType caseType,

        TestCasePriority priority,

        /** 可空；默认 DRAFT；DEPRECATED 不允许在创建时指定 */
        TestCaseStatus status,

        /** 可空；NULL=未分类；必须属于本项目 */
        Long directoryId) {
}
