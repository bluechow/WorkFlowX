package com.workflowx.project.service.impl;

import com.workflowx.common.exception.BusinessException;
import com.workflowx.common.exception.ForbiddenException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.org.mapper.OrganizationMemberMapper;
import com.workflowx.project.dto.AddProjectMemberRequest;
import com.workflowx.project.entity.Project;
import com.workflowx.project.entity.ProjectMember;
import com.workflowx.project.entity.ProjectMemberRole;
import com.workflowx.project.mapper.ProjectMapper;
import com.workflowx.project.mapper.ProjectMemberMapper;
import com.workflowx.project.service.ProjectMemberService;
import com.workflowx.project.vo.ProjectMemberVO;
import com.workflowx.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 项目成员领域服务实现（P5-04，ADR-015）。
 * 数据级: 添加/移除的操作者须为项目所属组织成员（含 OWNER）→ 403。
 * 前置: 被添加用户须为该组织成员 → 400；重复 → 409；OWNER 移除 → 400。
 */
@Service
@RequiredArgsConstructor
public class ProjectMemberServiceImpl implements ProjectMemberService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final OrganizationMemberMapper organizationMemberMapper;
    private final UserMapper userMapper;
    private final com.workflowx.audit.service.AuditService auditService;

    @Override
    @Transactional
    public ProjectMemberVO addMember(Long projectId, AddProjectMemberRequest request, Long operatorId) {
        Project project = requireProject(projectId);
        requireProjectOrgMembership(project.getOrgId(), operatorId);
        if (userMapper.selectById(request.userId()) == null) {
            throw new ResourceNotFoundException("user", request.userId());
        }
        // ADR-015 前置: 被添加用户必须已是项目所属组织成员
        if (organizationMemberMapper.findMember(project.getOrgId(), request.userId()) == null) {
            throw new BusinessException(400, "用户须先加入项目所属组织，才能成为项目成员");
        }
        // OWNER 由创建项目时自动产生，不可手动添加（HTTP 层 DTO 正则 422，Service 层 400 双层守护）
        if (ProjectMemberRole.OWNER.name().equals(request.role())) {
            throw new BusinessException(400, "OWNER 角色由创建项目时自动产生，不可手动添加");
        }
        if (projectMemberMapper.existsBinding(projectId, request.userId()) > 0) {
            throw new BusinessException(409, "用户已是项目成员");
        }
        try {
            projectMemberMapper.insert(projectId, request.userId(), request.role());
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "用户已是项目成员");
        }
        auditService.record("PROJECT", "ASSIGN_MEMBER", "project:" + projectId,
                "项目成员 " + request.userId() + " 角色设为 " + request.role(), true, operatorId);
        return ProjectMemberVO.from(requireMember(projectId, request.userId()));
    }

    @Override
    @Transactional
    public void removeMember(Long projectId, Long userId, Long operatorId) {
        Project project = requireProject(projectId);
        requireProjectOrgMembership(project.getOrgId(), operatorId);
        ProjectMember member = requireMember(projectId, userId);
        if (member.getRole() == ProjectMemberRole.OWNER) {
            throw new BusinessException(400, "项目负责人（OWNER）不可移除");
        }
        projectMemberMapper.deleteMember(projectId, userId);
    }

    @Override
    public List<ProjectMemberVO> listMembers(Long projectId) {
        requireProject(projectId);
        return projectMemberMapper.findMembersByProjectId(projectId).stream()
                .map(ProjectMemberVO::from).toList();
    }

    private Project requireProject(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new ResourceNotFoundException("project", id);
        }
        return project;
    }

    private ProjectMember requireMember(Long projectId, Long userId) {
        ProjectMember member = projectMemberMapper.findMember(projectId, userId);
        if (member == null) {
            throw new ResourceNotFoundException("project member", "project=" + projectId + ", user=" + userId);
        }
        return member;
    }

    /** 数据级归属（ADR-014）: 操作者必须是项目所属组织成员 */
    private void requireProjectOrgMembership(Long orgId, Long operatorId) {
        if (organizationMemberMapper.findMember(orgId, operatorId) == null) {
            throw new ForbiddenException("仅项目所属组织的成员可管理项目成员");
        }
    }
}
