package com.workflowx.testcase.vo;

import com.workflowx.testcase.entity.TestCase;
import com.workflowx.testcase.entity.TestCasePriority;
import com.workflowx.testcase.entity.TestCaseStatus;
import com.workflowx.testcase.entity.TestCaseType;

import java.time.LocalDateTime;

/** 测试用例视图对象（Phase 20）。业务编号 = project.key + "-TC-" + testcaseNo 由前端拼装（ADR-016 模式）。 */
public record TestCaseVO(
        Long id,
        Long projectId,
        Long directoryId,
        Long testcaseNo,
        String title,
        String preconditions,
        String steps,
        String expected,
        TestCaseType type,
        TestCasePriority priority,
        TestCaseStatus status,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static TestCaseVO from(TestCase testCase) {
        return new TestCaseVO(testCase.getId(), testCase.getProjectId(), testCase.getDirectoryId(),
                testCase.getTestcaseNo(), testCase.getTitle(), testCase.getPreconditions(),
                testCase.getSteps(), testCase.getExpected(),
                testCase.getCaseType(),
                testCase.getPriority(),
                testCase.getStatus(),
                testCase.getCreatedBy(), testCase.getCreatedAt(), testCase.getUpdatedAt());
    }
}
