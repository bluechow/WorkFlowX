package com.workflowx.testplan.vo;

import com.workflowx.testcase.entity.TestCasePriority;
import com.workflowx.testcase.entity.TestCaseStatus;
import com.workflowx.testcase.entity.TestCaseType;
import com.workflowx.testplan.entity.TestPlanItem;

import java.time.LocalDateTime;

/** 计划条目视图对象（V1.1）：条目 + 关联用例摘要。 */
public record TestPlanItemVO(
        Long id,
        Long planId,
        Long caseId,
        Long testcaseNo,
        String caseTitle,
        TestCaseType caseType,
        TestCasePriority casePriority,
        TestCaseStatus caseStatus,
        TestPlanItem.ItemResult result,
        Long executedBy,
        LocalDateTime executedAt,
        String note,
        Long issueId) {
}
