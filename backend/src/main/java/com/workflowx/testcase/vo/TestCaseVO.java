package com.workflowx.testcase.vo;

import com.workflowx.testcase.entity.TestCase;

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
        TestCaseTypeValues type,
        TestCasePriorityValues priority,
        TestCaseStatusValues status,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /** VO 层类型别名（避免与 entity 枚举同名混淆，序列化为名称字符串） */
    public enum TestCaseTypeValues { FUNCTIONAL, REGRESSION, SMOKE, SECURITY, PERFORMANCE }

    public enum TestCasePriorityValues { LOW, MEDIUM, HIGH, CRITICAL }

    public enum TestCaseStatusValues { DRAFT, ACTIVE, DEPRECATED }

    public static TestCaseVO from(TestCase testCase) {
        return new TestCaseVO(testCase.getId(), testCase.getProjectId(), testCase.getDirectoryId(),
                testCase.getTestcaseNo(), testCase.getTitle(), testCase.getPreconditions(),
                testCase.getSteps(), testCase.getExpected(),
                TestCaseTypeValues.valueOf(testCase.getCaseType().name()),
                TestCasePriorityValues.valueOf(testCase.getPriority().name()),
                TestCaseStatusValues.valueOf(testCase.getStatus().name()),
                testCase.getCreatedBy(), testCase.getCreatedAt(), testCase.getUpdatedAt());
    }
}
