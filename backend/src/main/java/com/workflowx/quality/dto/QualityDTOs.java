package com.workflowx.quality.dto;

import java.util.List;

/** 质量分析 DTO 集（创新点：覆盖率热力图 + 需求-测试追溯矩阵） */

public final class QualityDTOs {

    private QualityDTOs() {
    }

    // ===== 热力图 =====

    public record DirectoryStatVO(
            Long directoryId,
            String directoryName,
            int caseCount,
            int executedCount,
            int passCount,
            int failCount,
            int blockedCount,
            int pendingCount,
            int bugCount,
            /** 覆盖率 = 已执行用例 / 总用例 × 100 */
            double coverageRate,
            /** 通过率 = PASS / 已执行 × 100 */
            double passRate,
            /** 风险等级：CRITICAL(零用例)/HIGH(低覆盖或高失败)/MEDIUM/LOW */
            String riskLevel) {
    }

    public record HeatmapVO(
            int totalDirectories,
            int totalCases,
            int totalExecuted,
            int totalBugs,
            double overallCoverage,
            List<DirectoryStatVO> directories) {
    }

    // ===== 追溯矩阵 =====

    public record TraceCaseVO(
            Long caseId,
            Long testcaseNo,
            String title,
            String caseType,
            String priority,
            String caseStatus,
            /** 最新执行结果（null=从未执行） */
            String latestResult,
            String latestExecutedAt,
            Long linkedBugId,
            Long linkedBugNo,
            String linkedBugTitle,
            String linkedBugStatus) {
    }

    public record TraceDirectoryVO(
            Long directoryId,
            String directoryName,
            int caseCount,
            /** 已有用例的功能模块数（即 caseCount > 0） */
            int coveredCount,
            /** 无用例的模块数 */
            int gapCount,
            List<TraceCaseVO> cases) {
    }

    public record TraceabilityVO(
            int totalModules,
            int coveredModules,
            int gapModules,
            List<TraceDirectoryVO> directories) {
    }
}
