package com.workflowx.testcase.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflowx.common.exception.BusinessException;
import com.workflowx.common.exception.ForbiddenException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.common.web.PageVO;
import com.workflowx.project.mapper.ProjectMapper;
import com.workflowx.project.mapper.ProjectMemberMapper;
import com.workflowx.testcase.dto.CreateTestCaseRequest;
import com.workflowx.testcase.dto.TestCasePageQuery;
import com.workflowx.testcase.dto.UpdateTestCaseRequest;
import com.workflowx.testcase.entity.TestCase;
import com.workflowx.testcase.entity.TestCaseDirectory;
import com.workflowx.testcase.entity.TestCasePriority;
import com.workflowx.testcase.entity.TestCaseStatus;
import com.workflowx.testcase.entity.TestCaseType;
import com.workflowx.testcase.mapper.TestCaseDirectoryMapper;
import com.workflowx.testcase.mapper.TestCaseMapper;
import com.workflowx.testcase.service.TestCaseService;
import com.workflowx.testcase.vo.TestCaseVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 测试用例实现（Phase 20; ADR-022）。
 * 校验链: 项目成员 403（先于目标查找）→ 用例 404（跨项目拼接不泄露存在性）→ 业务规则 400。
 * 编号: 同事务内行锁递增 projects.testcase_seq（ADR-016 同方案）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestCaseServiceImpl implements TestCaseService {

    private final TestCaseMapper testCaseMapper;
    private final TestCaseDirectoryMapper directoryMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;

    @Override
    @Transactional
    public TestCaseVO create(Long projectId, CreateTestCaseRequest request, Long operatorId) {
        requireProject(projectId);
        requireProjectMembership(projectId, operatorId);
        Long directoryId = validateDirectory(projectId, request.directoryId());
        if (request.status() == TestCaseStatus.DEPRECATED) {
            throw new BusinessException(400, "用例创建时状态不能为 DEPRECATED");
        }

        if (testCaseMapper.incrementTestcaseSeq(projectId) == 0) {
            throw new ResourceNotFoundException("project", projectId);
        }
        long testcaseNo = testCaseMapper.selectTestcaseSeq(projectId);

        TestCase testCase = new TestCase();
        testCase.setProjectId(projectId);
        testCase.setDirectoryId(directoryId);
        testCase.setTestcaseNo(testcaseNo);
        testCase.setTitle(request.title());
        testCase.setPreconditions(request.preconditions());
        testCase.setSteps(request.steps());
        testCase.setExpected(request.expected());
        testCase.setCaseType(request.caseType());
        testCase.setPriority(request.priority() == null ? TestCasePriority.MEDIUM : request.priority());
        testCase.setStatus(request.status() == null ? TestCaseStatus.DRAFT : request.status());
        testCase.setCreatedBy(operatorId);
        testCaseMapper.insert(testCase);
        return TestCaseVO.from(testCaseMapper.selectById(testCase.getId()));
    }

    @Override
    public PageVO<TestCaseVO> page(Long projectId, TestCasePageQuery query, Long operatorId) {
        requireProject(projectId);
        requireProjectMembership(projectId, operatorId);
        LambdaQueryWrapper<TestCase> wrapper = new LambdaQueryWrapper<TestCase>()
                .eq(TestCase::getProjectId, projectId);
        if (StringUtils.hasText(query.keyword())) {
            wrapper.like(TestCase::getTitle, query.keyword());
        }
        if (query.directoryId() != null) {
            if (query.directoryId() == 0L) {
                wrapper.isNull(TestCase::getDirectoryId);
            } else {
                wrapper.eq(TestCase::getDirectoryId, query.directoryId());
            }
        }
        if (query.status() != null) {
            wrapper.eq(TestCase::getStatus, query.status());
        }
        if (query.caseType() != null) {
            wrapper.eq(TestCase::getCaseType, query.caseType());
        }
        if (query.priority() != null) {
            wrapper.eq(TestCase::getPriority, query.priority());
        }
        wrapper.orderByDesc(TestCase::getTestcaseNo);
        IPage<TestCase> result = testCaseMapper.selectPage(new Page<>(query.pageNum(), query.pageSize()), wrapper);
        return PageVO.of(result.convert(TestCaseVO::from));
    }

    @Override
    public TestCaseVO getById(Long projectId, Long testcaseId, Long operatorId) {
        requireProject(projectId);
        requireProjectMembership(projectId, operatorId);
        return TestCaseVO.from(requireCaseInProject(projectId, testcaseId));
    }

    @Override
    public TestCaseVO update(Long projectId, Long testcaseId, UpdateTestCaseRequest request, Long operatorId) {
        requireProject(projectId);
        requireProjectMembership(projectId, operatorId);
        TestCase testCase = requireCaseInProject(projectId, testcaseId);
        if (StringUtils.hasText(request.title())) {
            testCase.setTitle(request.title());
        }
        if (request.preconditions() != null) {
            testCase.setPreconditions(request.preconditions());
        }
        if (request.steps() != null) {
            testCase.setSteps(request.steps());
        }
        if (request.expected() != null) {
            testCase.setExpected(request.expected());
        }
        if (request.caseType() != null) {
            testCase.setCaseType(request.caseType());
        }
        if (request.priority() != null) {
            testCase.setPriority(request.priority());
        }
        if (request.status() != null) {
            testCase.setStatus(request.status());
        }
        if (request.directoryId() != null) {
            testCase.setDirectoryId(validateDirectory(projectId, request.directoryId()));
        }
        testCaseMapper.updateById(testCase);
        return TestCaseVO.from(testCaseMapper.selectById(testcaseId));
    }

    @Override
    public void delete(Long projectId, Long testcaseId, Long operatorId) {
        requireProject(projectId);
        requireProjectMembership(projectId, operatorId);
        requireCaseInProject(projectId, testcaseId);
        testCaseMapper.deleteById(testcaseId);
    }

    // ===== 校验 =====

    private void requireProject(Long projectId) {
        if (projectMapper.selectById(projectId) == null) {
            throw new ResourceNotFoundException("project", projectId);
        }
    }

    /** 数据级: 非项目成员 403（先于目标查找，对齐既有语义）。 */
    private void requireProjectMembership(Long projectId, Long operatorId) {
        if (projectMemberMapper.findMember(projectId, operatorId) == null) {
            throw new ForbiddenException("仅项目成员可操作该项目的用例库");
        }
    }

    private TestCase requireCaseInProject(Long projectId, Long testcaseId) {
        TestCase testCase = testCaseMapper.selectById(testcaseId);
        if (testCase == null || !testCase.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("testcase", testcaseId);
        }
        return testCase;
    }

    /** 目录必须属于本项目；返回规范化 directoryId（null=未分类）。 */
    private Long validateDirectory(Long projectId, Long directoryId) {
        if (directoryId == null || directoryId == 0L) {
            return null;
        }
        TestCaseDirectory directory = directoryMapper.selectById(directoryId);
        if (directory == null || !directory.getProjectId().equals(projectId)) {
            throw new BusinessException(400, "目录不存在或不属于当前项目");
        }
        return directory.getId();
    }
}
