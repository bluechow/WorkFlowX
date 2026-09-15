package com.workflowx.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflowx.common.exception.ForbiddenException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.common.web.PageVO;
import com.workflowx.org.mapper.OrganizationMemberMapper;
import com.workflowx.org.mapper.OrganizationMapper;
import com.workflowx.project.dto.CreateProjectRequest;
import com.workflowx.project.dto.UpdateProjectRequest;
import com.workflowx.project.entity.Project;
import com.workflowx.project.entity.ProjectStatus;
import com.workflowx.project.entity.ProjectMemberRole;
import com.workflowx.project.mapper.ProjectMemberMapper;
import com.workflowx.project.mapper.ProjectMapper;
import com.workflowx.project.service.ProjectService;
import com.workflowx.project.vo.ProjectVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 项目领域服务实现（P5-01）。
 * 数据级归属（ADR-014）: 写操作校验操作者是目标组织成员（任意角色含 OWNER）；
 * 读操作（get/page）仅要求 project:* authority，不做归属限制（企业内可见性由后续 project_members 细化）。
 */
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final OrganizationMapper organizationMapper;
    private final OrganizationMemberMapper organizationMemberMapper;

    @Override
    @Transactional
    public ProjectVO create(CreateProjectRequest request, Long operatorId) {
        if (organizationMapper.selectById(request.orgId()) == null) {
            throw new ResourceNotFoundException("organization", request.orgId());
        }
        requireOrgMembership(request.orgId(), operatorId);

        Project project = new Project();
        project.setOrgId(request.orgId());
        project.setKey(request.key());
        project.setName(request.name());
        project.setDescription(request.description());
        project.setStatus(ProjectStatus.ACTIVE);
        project.setOwnerId(operatorId);
        try {
            projectMapper.insert(project);
        } catch (DuplicateKeyException e) {
            throw new com.workflowx.common.exception.BusinessException(409, "项目 key 已存在: " + request.key());
        }
        // 创建者即项目负责人（OWNER），同事务写入成员表（ADR-015）
        projectMemberMapper.insert(project.getId(), operatorId, ProjectMemberRole.OWNER.name());
        return ProjectVO.from(requireProject(project.getId()));
    }

    @Override
    public ProjectVO getById(Long id) {
        return ProjectVO.from(requireProject(id));
    }

    @Override
    public PageVO<ProjectVO> page(String keyword, ProjectStatus status, Long orgId, int page, int size) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<Project>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Project::getName, keyword).or().like(Project::getKey, keyword));
        }
        if (status != null) {
            wrapper.eq(Project::getStatus, status);
        }
        if (orgId != null) {
            wrapper.eq(Project::getOrgId, orgId);
        }
        wrapper.orderByDesc(Project::getCreatedAt).orderByDesc(Project::getId);
        Page<Project> result = projectMapper.selectPage(new Page<>(page, size), wrapper);
        return PageVO.of(result.convert(ProjectVO::from));
    }

    @Override
    @Transactional
    public ProjectVO update(Long id, UpdateProjectRequest request, Long operatorId) {
        Project project = requireProject(id);
        requireOrgMembership(project.getOrgId(), operatorId);
        if (request.name() != null) {
            project.setName(request.name());
        }
        if (request.description() != null) {
            project.setDescription(request.description());
        }
        projectMapper.updateById(project);
        return ProjectVO.from(requireProject(id));
    }

    @Override
    @Transactional
    public ProjectVO updateStatus(Long id, ProjectStatus status, Long operatorId) {
        Project project = requireProject(id);
        requireOrgMembership(project.getOrgId(), operatorId);
        project.setStatus(status);
        projectMapper.updateById(project);
        return ProjectVO.from(requireProject(id));
    }

    private Project requireProject(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new ResourceNotFoundException("project", id);
        }
        return project;
    }

    /** 数据级归属: 操作者必须是项目所属组织成员（ADR-014） */
    private void requireOrgMembership(Long orgId, Long operatorId) {
        if (organizationMemberMapper.findMember(orgId, operatorId) == null) {
            throw new ForbiddenException("仅组织成员可操作该组织的项目");
        }
    }
}
