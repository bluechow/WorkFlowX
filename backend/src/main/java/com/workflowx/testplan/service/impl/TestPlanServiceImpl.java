package com.workflowx.testplan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflowx.common.exception.BusinessException;
import com.workflowx.common.exception.ForbiddenException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.common.web.PageVO;
import com.workflowx.issue.mapper.IssueMapper;
import com.workflowx.project.mapper.ProjectMapper;
import com.workflowx.project.mapper.ProjectMemberMapper;
import com.workflowx.testcase.entity.TestCase;
import com.workflowx.testcase.mapper.TestCaseMapper;
import com.workflowx.testplan.dto.AddTestPlanItemsRequest;
import com.workflowx.testplan.dto.CreateTestPlanRequest;
import com.workflowx.testplan.dto.ExecuteItemRequest;
import com.workflowx.testplan.dto.UpdateTestPlanRequest;
import com.workflowx.testplan.entity.TestPlan;
import com.workflowx.testplan.entity.TestPlanItem;
import com.workflowx.testplan.mapper.TestPlanItemMapper;
import com.workflowx.testplan.mapper.TestPlanMapper;
import com.workflowx.testplan.service.TestPlanService;
import com.workflowx.testplan.vo.TestPlanItemVO;
import com.workflowx.testplan.vo.TestPlanVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 测试计划实现（V1.1; ADR-023）。
 * 校验链: 项目成员 403（先于目标查找）→ 计划/条目 404（跨项目不泄露）→ 业务规则 400。
 * 统计: 从条目表实时 COUNT，无冗余字段。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestPlanServiceImpl implements TestPlanService {

    /** 单次批量添加用例上限（P2a 加固） */
    private static final int MAX_ADD_ITEMS = 200;

    private final TestPlanMapper testPlanMapper;
    private final TestPlanItemMapper testPlanItemMapper;
    private final com.workflowx.testcase.mapper.TestCaseMapper testCaseMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final IssueMapper issueMapper;

    @Override
    public TestPlanVO create(Long projectId, CreateTestPlanRequest request, Long operatorId) {
        requireProject(projectId);
        requireProjectMembership(projectId, operatorId);
        TestPlan plan = new TestPlan();
        plan.setProjectId(projectId);
        plan.setName(request.name());
        plan.setStatus(TestPlan.TestPlanStatus.NOT_STARTED);
        plan.setCreatedBy(operatorId);
        testPlanMapper.insert(plan);
        return toVO(plan, 0);
    }

    @Override
    public PageVO<TestPlanVO> page(Long projectId, long page, long size, Long operatorId) {
        requireProject(projectId);
        requireProjectMembership(projectId, operatorId);
        IPage<TestPlan> result = testPlanMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<TestPlan>()
                        .eq(TestPlan::getProjectId, projectId)
                        .orderByDesc(TestPlan::getCreatedAt)
                        .orderByDesc(TestPlan::getId));
        // P2a：分页 N+1 → 一次分组聚合（本页计划的分结果计数）
        List<Long> planIds = result.getRecords().stream().map(TestPlan::getId).toList();
        Map<String, Long> statsByPlan = planIds.isEmpty() ? Map.of()
                : testPlanItemMapper.selectList(new LambdaQueryWrapper<TestPlanItem>()
                                .in(TestPlanItem::getPlanId, planIds)
                                .select(TestPlanItem::getPlanId, TestPlanItem::getResult))
                        .stream()
                        .collect(Collectors.groupingBy(i -> i.getPlanId() + ":" + i.getResult().name(),
                                Collectors.counting()));
        List<TestPlanVO> plans = result.getRecords().stream()
                .map(p -> {
                    long[] stats = planStats(statsByPlan, p.getId());
                    return toVO(p, stats[0] + stats[1] + stats[2] + stats[3], stats);
                })
                .toList();
        return new PageVO<>(plans, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /** 由分组计数字典构造单计划的分结果计数（缺省 0） */
    private long[] planStats(Map<String, Long> statsByPlan, Long planId) {
        return new long[]{
                statsByPlan.getOrDefault(planId + ":PASS", 0L),
                statsByPlan.getOrDefault(planId + ":FAIL", 0L),
                statsByPlan.getOrDefault(planId + ":BLOCKED", 0L),
                statsByPlan.getOrDefault(planId + ":PENDING", 0L),
        };
    }

    @Override
    public TestPlanVO get(Long projectId, Long planId, Long operatorId) {
        requireProject(projectId);
        requireProjectMembership(projectId, operatorId);
        TestPlan plan = requirePlanInProject(projectId, planId);
        return toVO(plan, countByResult(planId, null));
    }

    @Override
    public List<TestPlanItemVO> items(Long projectId, Long planId, Long operatorId) {
        requireProject(projectId);
        requireProjectMembership(projectId, operatorId);
        requirePlanInProject(projectId, planId);
        return itemVOs(planId);
    }

    @Override
    public TestPlanVO update(Long projectId, Long planId, UpdateTestPlanRequest request, Long operatorId) {
        requireProject(projectId);
        requireProjectMembership(projectId, operatorId);
        TestPlan plan = requirePlanInProject(projectId, planId);
        if (request.name() != null) {
            plan.setName(request.name());
        }
        if (request.status() != null) {
            plan.setStatus(parseStatus(request.status()));
        }
        testPlanMapper.updateById(plan);
        return toVO(plan, countByResult(planId, null));
    }

    @Override
    public void delete(Long projectId, Long planId, Long operatorId) {
        requireProject(projectId);
        requireProjectMembership(projectId, operatorId);
        requirePlanInProject(projectId, planId);
        testPlanMapper.deleteById(planId);
    }

    @Override
    @Transactional
    public int addItems(Long projectId, Long planId, AddTestPlanItemsRequest request, Long operatorId) {
        requireProject(projectId);
        requireProjectMembership(projectId, operatorId);
        TestPlan plan = requirePlanInProject(projectId, planId);
        if (plan.getStatus() == TestPlan.TestPlanStatus.COMPLETED) {
            throw new BusinessException(400, "计划已完成，不能再添加用例");
        }
        // P2a：单次批量上限（防超大请求）
        if (request.caseIds().size() > MAX_ADD_ITEMS) {
            throw new BusinessException(413, "单次最多添加 " + MAX_ADD_ITEMS + " 条用例，请分批操作");
        }
        List<Long> caseIds = request.caseIds().stream().distinct().toList();
        // P2a：逐条查询 → 批量查询（一次取回全部用例做归属校验）
        var caseById = caseIds.isEmpty() ? Map.<Long, TestCase>of()
                : testCaseMapper.selectBatchIds(caseIds).stream()
                        .collect(Collectors.toMap(TestCase::getId, c -> c));
        // P2a：逐条 exists → 一次取回已存在的绑定集合
        var existingCaseIds = testPlanItemMapper.selectList(new LambdaQueryWrapper<TestPlanItem>()
                        .eq(TestPlanItem::getPlanId, planId)
                        .in(TestPlanItem::getCaseId, caseIds))
                .stream().map(TestPlanItem::getCaseId).collect(Collectors.toSet());
        int added = 0;
        for (Long caseId : caseIds) {
            TestCase testCase = caseById.get(caseId);
            if (testCase == null || !testCase.getProjectId().equals(projectId)) {
                throw new BusinessException(400, "用例不存在或不属于当前项目: " + caseId);
            }
            if (existingCaseIds.contains(caseId)) {
                continue;
            }
            TestPlanItem item = new TestPlanItem();
            item.setPlanId(planId);
            item.setCaseId(caseId);
            item.setResult(TestPlanItem.ItemResult.PENDING);
            testPlanItemMapper.insert(item);
            added++;
        }
        return added;
    }

    @Override
    public void removeItem(Long projectId, Long planId, Long itemId, Long operatorId) {
        requireProject(projectId);
        requireProjectMembership(projectId, operatorId);
        requirePlanInProject(projectId, planId);
        TestPlanItem item = testPlanItemMapper.selectById(itemId);
        if (item == null || !item.getPlanId().equals(planId)) {
            throw new ResourceNotFoundException("testplan-item", itemId);
        }
        testPlanItemMapper.deleteById(itemId);
    }

    @Override
    public TestPlanItemVO execute(Long projectId, Long planId, Long itemId,
                                  ExecuteItemRequest request, Long operatorId) {
        requireProject(projectId);
        requireProjectMembership(projectId, operatorId);
        TestPlan plan = requirePlanInProject(projectId, planId);
        if (plan.getStatus() == TestPlan.TestPlanStatus.COMPLETED) {
            throw new BusinessException(400, "计划已完成，不能再执行");
        }
        TestPlanItem item = testPlanItemMapper.selectById(itemId);
        if (item == null || !item.getPlanId().equals(planId)) {
            throw new ResourceNotFoundException("testplan-item", itemId);
        }
        if (request.issueId() != null
                && request.result() != TestPlanItem.ItemResult.FAIL
                && request.result() != TestPlanItem.ItemResult.BLOCKED) {
            throw new BusinessException(400, "仅 FAIL/BLOCKED 结果可关联 Bug");
        }
        if (request.issueId() != null) {
            var issue = issueMapper.selectById(request.issueId());
            if (issue == null || !issue.getProjectId().equals(projectId)) {
                throw new BusinessException(400, "关联的 Issue 不存在或不属于当前项目");
            }
        }
        item.setResult(request.result());
        item.setNote(request.note());
        item.setIssueId(request.result() == TestPlanItem.ItemResult.FAIL
                || request.result() == TestPlanItem.ItemResult.BLOCKED
                ? request.issueId() : null);
        item.setExecutedBy(operatorId);
        item.setExecutedAt(LocalDateTime.now());
        testPlanItemMapper.updateById(item);
        // 首次执行自动 NOT_STARTED → RUNNING
        if (plan.getStatus() == TestPlan.TestPlanStatus.NOT_STARTED) {
            plan.setStatus(TestPlan.TestPlanStatus.RUNNING);
            testPlanMapper.updateById(plan);
        }
        return itemVO(item, testCaseMapper.selectById(item.getCaseId()));
    }

    // ===== 内部 =====

    private TestPlanVO toVO(TestPlan plan, long total) {
        // 兼容路径（单计划详情等低频调用）
        return toVO(plan, total, new long[]{
                countByResult(plan.getId(), TestPlanItem.ItemResult.PASS),
                countByResult(plan.getId(), TestPlanItem.ItemResult.FAIL),
                countByResult(plan.getId(), TestPlanItem.ItemResult.BLOCKED),
                countByResult(plan.getId(), TestPlanItem.ItemResult.PENDING)});
    }

    /** P2a：分页聚合路径——stats = [PASS, FAIL, BLOCKED, PENDING]（一次分组查询的结果） */
    private TestPlanVO toVO(TestPlan plan, long total, long[] stats) {
        return new TestPlanVO(plan.getId(), plan.getProjectId(), plan.getName(), plan.getStatus(),
                plan.getCreatedBy(), plan.getCreatedAt(),
                total, stats[0], stats[1], stats[2], stats[3]);
    }

    private long countByResult(Long planId, TestPlanItem.ItemResult result) {
        LambdaQueryWrapper<TestPlanItem> wrapper = new LambdaQueryWrapper<TestPlanItem>()
                .eq(TestPlanItem::getPlanId, planId);
        if (result != null) {
            wrapper.eq(TestPlanItem::getResult, result);
        }
        return testPlanItemMapper.selectCount(wrapper);
    }

    private List<TestPlanItemVO> itemVOs(Long planId) {
        List<TestPlanItem> items = testPlanItemMapper.selectList(
                new LambdaQueryWrapper<TestPlanItem>()
                        .eq(TestPlanItem::getPlanId, planId)
                        .orderByAsc(TestPlanItem::getId));
        if (items.isEmpty()) {
            return List.of();
        }
        Map<Long, TestCase> caseById = testCaseMapper.selectBatchIds(
                        items.stream().map(TestPlanItem::getCaseId).distinct().toList())
                .stream().collect(Collectors.toMap(TestCase::getId, Function.identity()));
        return items.stream().map(item -> itemVO(item, caseById.get(item.getCaseId()))).toList();
    }

    private TestPlanItemVO itemVO(TestPlanItem item, TestCase testCase) {
        return new TestPlanItemVO(item.getId(), item.getPlanId(), item.getCaseId(),
                testCase == null ? null : testCase.getTestcaseNo(),
                testCase == null ? null : testCase.getTitle(),
                testCase == null ? null : testCase.getCaseType(),
                testCase == null ? null : testCase.getPriority(),
                testCase == null ? null : testCase.getStatus(),
                item.getResult(), item.getExecutedBy(), item.getExecutedAt(),
                item.getNote(), item.getIssueId());
    }

    private void requireProject(Long projectId) {
        if (projectMapper.selectById(projectId) == null) {
            throw new ResourceNotFoundException("project", projectId);
        }
    }

    /** 数据级: 非项目成员 403（先于目标查找，对齐既有语义）。 */
    private void requireProjectMembership(Long projectId, Long operatorId) {
        if (projectMemberMapper.findMember(projectId, operatorId) == null) {
            throw new ForbiddenException("仅项目成员可操作该项目的测试计划");
        }
    }

    private TestPlan requirePlanInProject(Long projectId, Long planId) {
        TestPlan plan = testPlanMapper.selectById(planId);
        if (plan == null || !plan.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("testplan", planId);
        }
        return plan;
    }

    private TestPlan.TestPlanStatus parseStatus(String status) {
        try {
            return TestPlan.TestPlanStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "非法计划状态: " + status);
        }
    }
}
