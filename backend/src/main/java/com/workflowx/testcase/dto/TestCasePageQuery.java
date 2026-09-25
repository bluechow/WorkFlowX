package com.workflowx.testcase.dto;

import com.workflowx.testcase.entity.TestCasePriority;
import com.workflowx.testcase.entity.TestCaseStatus;
import com.workflowx.testcase.entity.TestCaseType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 用例分页查询（Phase 20）。
 * directoryId 语义: null=全部分类；0=未分类（directory IS NULL）；&gt;0=精确目录。
 */
public record TestCasePageQuery(
        String keyword,

        Long directoryId,

        TestCaseStatus status,

        TestCaseType caseType,

        TestCasePriority priority,

        @Min(value = 1, message = "page 最小为 1") Integer page,

        @Min(value = 1, message = "size 最小为 1")
        @Max(value = 100, message = "size 最大为 100") Integer size) {

    public long pageNum() {
        return page == null ? 1 : page;
    }

    public long pageSize() {
        return size == null ? 20 : size;
    }
}
