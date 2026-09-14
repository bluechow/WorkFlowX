package com.workflowx.org.service;

import com.workflowx.org.dto.CreateDepartmentRequest;
import com.workflowx.org.vo.DepartmentVO;

import java.util.List;

/**
 * 部门领域服务（P4-01/P4-02）。
 * 树规则（ADR-013）: parent 必须同组织、≠自身、非自身后代；删除父部门子级提升为根（SET NULL）。
 */
public interface DepartmentService {

    /** 创建部门（org 必须存在；防环校验） */
    DepartmentVO create(Long orgId, CreateDepartmentRequest request);

    DepartmentVO getById(Long id);

    /** 组织内部门列表（按 id 稳定排序） */
    List<DepartmentVO> listByOrg(Long orgId);

    /** 更新部门（改名/改父级；改父级走防环校验） */
    DepartmentVO update(Long id, String name, Long parentId);

    /** 删除部门（子级提升为根、成员 departmentId 置 NULL） */
    void delete(Long id);
}
