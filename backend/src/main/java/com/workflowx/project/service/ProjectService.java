package com.workflowx.project.service;

import com.workflowx.common.web.PageVO;
import com.workflowx.project.dto.CreateProjectRequest;
import com.workflowx.project.dto.UpdateProjectRequest;
import com.workflowx.project.entity.ProjectStatus;
import com.workflowx.project.vo.ProjectVO;

/**
 * 项目领域服务（P5-01）。
 * 业务规则（ADR-014）: key 全局唯一且不可修改；org 必须存在；
 * 写操作（create/update/status）要求操作者为项目所属组织成员（数据级归属 → 403）。
 */
public interface ProjectService {

    ProjectVO create(CreateProjectRequest request, Long operatorId);

    ProjectVO getById(Long id);

    /** 分页: keyword 匹配 name/key，status 过滤；created_at DESC, id DESC 稳定排序 */
    PageVO<ProjectVO> page(String keyword, ProjectStatus status, Long orgId, int page, int size);

    ProjectVO update(Long id, UpdateProjectRequest request, Long operatorId);

    /** 归档/恢复（status 变更，数据级归属校验同 update） */
    ProjectVO updateStatus(Long id, ProjectStatus status, Long operatorId);
}
