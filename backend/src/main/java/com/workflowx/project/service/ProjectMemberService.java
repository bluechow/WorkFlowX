package com.workflowx.project.service;

import com.workflowx.project.dto.AddProjectMemberRequest;
import com.workflowx.project.vo.ProjectMemberVO;

import java.util.List;

/**
 * 项目成员领域服务（P5-04，ADR-015）。
 * 前置规则: 被添加用户必须已是项目所属组织的成员 → 否则 400。
 * OWNER: 创建项目时自动产生，唯一，不可移除。
 * 数据级: 添加/移除的操作者须为项目所属组织成员（ADR-014）→ 403。
 */
public interface ProjectMemberService {

    /** 添加成员（用户须为组织成员；重复 409；操作者须为组织成员 403） */
    ProjectMemberVO addMember(Long projectId, AddProjectMemberRequest request, Long operatorId);

    /** 移除成员（OWNER 拒绝 400；未绑定 404；操作者须为组织成员 403） */
    void removeMember(Long projectId, Long userId, Long operatorId);

    /** 成员列表 */
    List<ProjectMemberVO> listMembers(Long projectId);
}
