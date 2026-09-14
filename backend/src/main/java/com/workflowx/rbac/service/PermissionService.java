package com.workflowx.rbac.service;

import com.workflowx.rbac.dto.CreatePermissionRequest;
import com.workflowx.rbac.vo.PermissionVO;

import java.util.List;

/**
 * 权限领域服务（P3-02）。
 * 系统权限（V3 种子 14 项）禁止删除；code 唯一（预检 + DB UK → 409）。
 */
public interface PermissionService {

    PermissionVO create(CreatePermissionRequest request);

    PermissionVO getById(Long id);

    PermissionVO getByCode(String code);

    List<PermissionVO> list();

    /** 删除权限（系统权限拒绝 400；绑定经 FK CASCADE 级联清理） */
    void delete(Long id);

    /** 用户的实时权限编码（user_roles ⋈ role_permissions ⋈ permissions）——后续鉴权接线的数据源（ADR-012） */
    List<String> findPermissionCodesByUserId(Long userId);
}
