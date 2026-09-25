package com.workflowx.testcase.dto;

import com.workflowx.testcase.entity.TestCasePriority;
import com.workflowx.testcase.entity.TestCaseStatus;
import com.workflowx.testcase.entity.TestCaseType;
import jakarta.validation.constraints.Size;

/** 更新测试用例请求（Phase 20; ADR-022）：null=不变；directoryId 变更须属于本项目。 */
public record UpdateTestCaseRequest(
        @Size(max = 200, message = "title 最长 200 字符")
        String title,

        @Size(max = 65535, message = "preconditions 过长")
        String preconditions,

        @Size(max = 65535, message = "steps 过长")
        String steps,

        @Size(max = 65535, message = "expected 过长")
        String expected,

        TestCaseType caseType,

        TestCasePriority priority,

        TestCaseStatus status,

        Long directoryId) {
}
