package com.workflowx.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.issue.entity.Issue;
import com.workflowx.issue.mapper.IssueMapper;
import com.workflowx.project.mapper.ProjectMapper;
import com.workflowx.quality.dto.QualityDTOs.DirectoryStatVO;
import com.workflowx.quality.dto.QualityDTOs.HeatmapVO;
import com.workflowx.quality.dto.QualityDTOs.TraceCaseVO;
import com.workflowx.quality.dto.QualityDTOs.TraceDirectoryVO;
import com.workflowx.quality.dto.QualityDTOs.TraceabilityVO;
import com.workflowx.quality.service.QualityService;
import com.workflowx.testcase.entity.TestCase;
import com.workflowx.testcase.entity.TestCaseDirectory;
import com.workflowx.testcase.mapper.TestCaseDirectoryMapper;
import com.workflowx.testcase.mapper.TestCaseMapper;
import com.workflowx.testplan.entity.TestPlanItem;
import com.workflowx.testplan.mapper.TestPlanItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 质量分析实现：对现有用例库/计划执行/Bug 数据做内存聚合（单项目规模下
 * 一次查询 + Java 分组优于多 SQL；数据量上万时可优化为 GROUP BY SQL）。
 *
 * 风险评级规则（应用创新点：测试盲区可视化）：
 * - CRITICAL：零用例（完全无测试保护的盲区）
 * - HIGH：覆盖率 < 50%，或失败率 > 30%
 * - MEDIUM：覆盖率 50%~80%，或存在未执行用例
 * - LOW：覆盖率 ≥ 80% 且通过率 ≥ 80%
 */
@Service
@RequiredArgsConstructor
public class QualityServiceImpl implements QualityService {

    private final ProjectMapper projectMapper;
    private final TestCaseDirectoryMapper directoryMapper;
    private final TestCaseMapper testCaseMapper;
    private final TestPlanItemMapper planItemMapper;
    private final IssueMapper issueMapper;

    @Override
    public HeatmapVO heatmap(Long projectId) {
        requireProject(projectId);
        var dirs = directoriesOf(projectId);
        var cases = casesOf(projectId);
        var executionByCase = latestExecutionByCase(cases);
        var bugsById = linkedBugs(executionByCase);

        List<DirectoryStatVO> stats = new ArrayList<>();
        for (TestCaseDirectory dir : dirs) {
            List<TestCase> dirCases = cases.stream()
                    .filter(c -> dir.getId().equals(c.getDirectoryId())).toList();
            stats.add(buildStat(dir, dirCases, executionByCase, bugsById));
        }
        // 未分类用例（directory_id = null）
        List<TestCase> unclassified = cases.stream()
                .filter(c -> c.getDirectoryId() == null).toList();
        if (!unclassified.isEmpty()) {
            stats.add(buildStat(null, unclassified, executionByCase, bugsById));
        }

        int totalCases = cases.size();
        int totalExecuted = (int) executionByCase.values().stream()
                .filter(i -> i.getResult() != TestPlanItem.ItemResult.PENDING).count();
        int totalBugs = (int) executionByCase.values().stream()
                .filter(i -> i.getIssueId() != null).count();

        stats.sort(Comparator.comparingInt(DirectoryStatVO::caseCount).reversed());
        return new HeatmapVO(
                stats.size(), totalCases, totalExecuted, totalBugs,
                totalCases == 0 ? 0 : Math.round(totalExecuted * 1000.0 / totalCases) / 10.0,
                stats);
    }

    @Override
    public TraceabilityVO traceability(Long projectId) {
        requireProject(projectId);
        var dirs = directoriesOf(projectId);
        var cases = casesOf(projectId);
        var executionByCase = latestExecutionByCase(cases);
        var bugsById = linkedBugs(executionByCase);

        List<TraceDirectoryVO> rows = new ArrayList<>();
        for (TestCaseDirectory dir : dirs) {
            List<TestCase> dirCases = cases.stream()
                    .filter(c -> dir.getId().equals(c.getDirectoryId()))
                    .sorted(Comparator.comparingLong(TestCase::getTestcaseNo)).toList();
            rows.add(buildTraceDir(dir.getName(), dirCases, executionByCase, bugsById));
        }
        List<TestCase> unclassified = cases.stream()
                .filter(c -> c.getDirectoryId() == null).toList();
        if (!unclassified.isEmpty()) {
            rows.add(buildTraceDir("未分类", unclassified, executionByCase, bugsById));
        }

        int total = rows.size();
        int covered = (int) rows.stream().filter(r -> r.caseCount() > 0).count();
        return new TraceabilityVO(total, covered, total - covered, rows);
    }

    // ===== 私有方法 =====

    private DirectoryStatVO buildStat(TestCaseDirectory dir, List<TestCase> dirCases,
            Map<Long, TestPlanItem> executionByCase, Map<Long, Issue> bugsById) {
        int caseCount = dirCases.size();
        int pass = 0, fail = 0, blocked = 0, pending = 0, bugCount = 0;
        for (TestCase c : dirCases) {
            TestPlanItem item = executionByCase.get(c.getId());
            if (item == null || item.getResult() == TestPlanItem.ItemResult.PENDING) {
                pending++;
            } else {
                switch (item.getResult()) {
                    case PASS -> pass++;
                    case FAIL -> {
                        fail++;
                        if (item.getIssueId() != null) bugCount++;
                    }
                    case BLOCKED -> blocked++;
                    default -> pending++;
                }
            }
        }
        int executed = pass + fail + blocked;
        double coverage = caseCount == 0 ? 0 : Math.round(executed * 1000.0 / caseCount) / 10.0;
        double passRate = executed == 0 ? 0 : Math.round(pass * 1000.0 / executed) / 10.0;
        String risk = riskLevel(caseCount, coverage, passRate, fail);
        return new DirectoryStatVO(
                dir == null ? null : dir.getId(),
                dir == null ? "未分类" : dir.getName(),
                caseCount, executed, pass, fail, blocked, pending, bugCount,
                coverage, passRate, risk);
    }

    private TraceDirectoryVO buildTraceDir(String dirName, List<TestCase> dirCases,
            Map<Long, TestPlanItem> executionByCase, Map<Long, Issue> bugsById) {
        List<TraceCaseVO> caseRows = dirCases.stream().map(c -> {
            TestPlanItem item = executionByCase.get(c.getId());
            Issue bug = (item != null && item.getIssueId() != null) ? bugsById.get(item.getIssueId()) : null;
            return new TraceCaseVO(
                    c.getId(), c.getTestcaseNo(), c.getTitle(),
                    c.getCaseType() == null ? null : c.getCaseType().name(),
                    c.getPriority() == null ? null : c.getPriority().name(),
                    c.getStatus() == null ? null : c.getStatus().name(),
                    item == null ? null : item.getResult().name(),
                    item == null || item.getExecutedAt() == null ? null : item.getExecutedAt().toString(),
                    bug == null ? null : bug.getId(),
                    bug == null ? null : bug.getIssueNo(),
                    bug == null ? null : bug.getTitle(),
                    bug == null ? null : bug.getStatus().name());
        }).toList();
        int covered = (int) caseRows.stream()
                .filter(r -> r.latestResult() != null && !"PENDING".equals(r.latestResult())).count();
        return new TraceDirectoryVO(null, dirName, dirCases.size(), covered, dirCases.size() - covered, caseRows);
    }

    /** 风险评级规则（创新点核心逻辑：测试盲区自动识别） */
    private String riskLevel(int caseCount, double coverage, double passRate, int failCount) {
        if (caseCount == 0) {
            return "CRITICAL";
        }
        if (coverage < 50 || (failCount > 0 && passRate < 70)) {
            return "HIGH";
        }
        if (coverage < 80 || passRate < 80) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private List<TestCaseDirectory> directoriesOf(Long projectId) {
        return directoryMapper.selectList(
                new LambdaQueryWrapper<TestCaseDirectory>()
                        .eq(TestCaseDirectory::getProjectId, projectId)
                        .orderByAsc(TestCaseDirectory::getName));
    }

    private List<TestCase> casesOf(Long projectId) {
        return testCaseMapper.selectList(
                new LambdaQueryWrapper<TestCase>()
                        .eq(TestCase::getProjectId, projectId)
                        .orderByAsc(TestCase::getTestcaseNo));
    }

    /** 每个用例取最新一次执行记录（同用例多计划取最新 plan_item） */
    private Map<Long, TestPlanItem> latestExecutionByCase(List<TestCase> cases) {
        if (cases.isEmpty()) {
            return Map.of();
        }
        var caseIds = cases.stream().map(TestCase::getId).toList();
        var items = planItemMapper.selectList(
                new LambdaQueryWrapper<TestPlanItem>()
                        .in(TestPlanItem::getCaseId, caseIds)
                        .orderByDesc(TestPlanItem::getId));
        Map<Long, TestPlanItem> latest = new HashMap<>();
        for (TestPlanItem item : items) {
            latest.putIfAbsent(item.getCaseId(), item); // orderByDesc(ID) → 首次遇到即最新
        }
        return latest;
    }

    private Map<Long, Issue> linkedBugs(Map<Long, TestPlanItem> executionByCase) {
        var bugIds = executionByCase.values().stream()
                .map(TestPlanItem::getIssueId)
                .filter(id -> id != null).distinct().toList();
        if (bugIds.isEmpty()) {
            return Map.of();
        }
        return issueMapper.selectBatchIds(bugIds).stream()
                .collect(Collectors.toMap(Issue::getId, i -> i));
    }

    private void requireProject(Long projectId) {
        if (projectMapper.selectById(projectId) == null) {
            throw new ResourceNotFoundException("project", projectId);
        }
    }
}
