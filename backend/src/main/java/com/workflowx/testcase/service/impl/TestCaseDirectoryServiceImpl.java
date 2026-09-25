package com.workflowx.testcase.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.common.exception.BusinessException;
import com.workflowx.common.exception.ForbiddenException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.project.mapper.ProjectMapper;
import com.workflowx.project.mapper.ProjectMemberMapper;
import com.workflowx.testcase.dto.CreateTestCaseDirectoryRequest;
import com.workflowx.testcase.dto.UpdateTestCaseDirectoryRequest;
import com.workflowx.testcase.entity.TestCaseDirectory;
import com.workflowx.testcase.mapper.TestCaseDirectoryMapper;
import com.workflowx.testcase.service.TestCaseDirectoryService;
import com.workflowx.testcase.vo.DirectoryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用例目录实现（Phase 20; ADR-022）。
 * 校验链: 项目成员 403（先于目标查找）→ 目录 404（跨项目拼接不泄露存在性）→ 业务规则 400。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestCaseDirectoryServiceImpl implements TestCaseDirectoryService {

    private final TestCaseDirectoryMapper directoryMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;

    @Override
    public DirectoryVO create(Long projectId, CreateTestCaseDirectoryRequest request, Long operatorId) {
        requireProject(projectId);
        requireProjectMembership(projectId, operatorId);
        Long parentId = null;
        if (request.parentId() != null) {
            parentId = requireDirectoryInProject(projectId, request.parentId()).getId();
        }
        TestCaseDirectory directory = new TestCaseDirectory();
        directory.setProjectId(projectId);
        directory.setParentId(parentId);
        directory.setName(request.name());
        directory.setCreatedBy(operatorId);
        directoryMapper.insert(directory);
        return DirectoryVO.from(directoryMapper.selectById(directory.getId()));
    }

    @Override
    public List<DirectoryVO> list(Long projectId, Long operatorId) {
        requireProject(projectId);
        requireProjectMembership(projectId, operatorId);
        return directoryMapper.selectList(new LambdaQueryWrapper<TestCaseDirectory>()
                        .eq(TestCaseDirectory::getProjectId, projectId)
                        .orderByAsc(TestCaseDirectory::getName))
                .stream().map(DirectoryVO::from).toList();
    }

    @Override
    public DirectoryVO update(Long projectId, Long directoryId, UpdateTestCaseDirectoryRequest request,
                              Long operatorId) {
        requireProject(projectId);
        requireProjectMembership(projectId, operatorId);
        TestCaseDirectory directory = requireDirectoryInProject(projectId, directoryId);
        if (request.name() != null) {
            directory.setName(request.name());
        }
        if (request.parentId() != null) {
            Long newParentId = requireDirectoryInProject(projectId, request.parentId()).getId();
            if (newParentId.equals(directoryId)) {
                throw new BusinessException(400, "不能将目录移动到自身");
            }
            requireNoCycle(projectId, directoryId, newParentId);
            directory.setParentId(newParentId);
        }
        directoryMapper.updateById(directory);
        return DirectoryVO.from(directoryMapper.selectById(directoryId));
    }

    @Override
    public void delete(Long projectId, Long directoryId, Long operatorId) {
        requireProject(projectId);
        requireProjectMembership(projectId, operatorId);
        requireDirectoryInProject(projectId, directoryId);
        // 子目录经自引用 FK 级联删除；目录内用例经 FK SET NULL 退回未分类
        directoryMapper.deleteById(directoryId);
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

    /** 目录必须存在于本项目，跨项目访问 404（不泄露存在性）。 */
    private TestCaseDirectory requireDirectoryInProject(Long projectId, Long directoryId) {
        TestCaseDirectory directory = directoryMapper.selectById(directoryId);
        if (directory == null || !directory.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("testcase-directory", directoryId);
        }
        return directory;
    }

    /** 防环: 从新父向上走，遇到被移动目录即成环。 */
    private void requireNoCycle(Long projectId, Long directoryId, Long newParentId) {
        Long cursor = newParentId;
        int depth = 0;
        while (cursor != null) {
            if (cursor.equals(directoryId)) {
                throw new BusinessException(400, "不能将目录移动到其自身子目录下");
            }
            TestCaseDirectory parent = directoryMapper.selectById(cursor);
            if (parent == null || !parent.getProjectId().equals(projectId)) {
                break;
            }
            cursor = parent.getParentId();
            if (++depth > 100) {
                throw new BusinessException(400, "目录层级异常");
            }
        }
    }
}
