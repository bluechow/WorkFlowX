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
        List<TestPlanVO> plans = result.getRecords().stream()
                .map(p -> toVO(p, countByResult(p.getId(), null)))
                .toList();
        return new PageVO<>(plans, result.getTotal(), result.getCurrent(), result.getSize());
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
        int added = 0;
        for (Long caseId : request.caseIds().stream().distinct().toList()) {
            TestCase testCase = testCaseMapper.selectById(caseId);
            if (testCase == null || !testCase.getProjectId().equals(projectId)) {
                throw new BusinessException(400, "用例不存在或不属于当前项目: " + caseId);
            }
            Long exists = testPlanItemMapper.selectCount(new LambdaQueryWrapper<TestPlanItem>()
                    .eq(TestPlanItem::getPlanId, planId)
                    .eq(TestPlanItem::getCaseId, caseId));
            if (exists > 0) {
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
        return new TestPlanVO(plan.getId(), plan.getProjectId(), plan.getName(), plan.getStatus(),
                plan.getCreatedBy(), plan.getCreatedAt(),
                total,
                countByResult(plan.getId(), TestPlanItem.ItemResult.PASS),
                countByResult(plan.getId(), TestPlanItem.ItemResult.FAIL),
                countByResult(plan.getId(), TestPlanItem.ItemResult.BLOCKED),
                countByResult(plan.getId(), TestPlanItem.ItemResult.PENDING));
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
