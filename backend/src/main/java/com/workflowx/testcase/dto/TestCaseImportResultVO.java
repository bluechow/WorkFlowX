package com.workflowx.testcase.dto;

import java.util.List;

/**
 * 用例 Excel 导入结果（Phase A-⑦）：行级失败不阻断，逐行给出「第 N 行：原因」。
 */
public record TestCaseImportResultVO(
        int totalRows,
        int successCount,
        int failureCount,
        List<RowError> failures
) {

    public record RowError(int rowNumber, String message) {
    }
}
