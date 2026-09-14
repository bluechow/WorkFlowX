package com.workflowx.rbac.service;

import com.workflowx.rbac.dto.AssignRolePermissionsRequest;
import com.workflowx.rbac.dto.CreateRoleRequest;
import com.workflowx.rbac.dto.UpdateRoleRequest;
import com.workflowx.rbac.vo.RoleVO;

import java.util.List;

/**
 * 角色领域服务（P3-02）。
 * 业务规则（ADR-012）: code 创建后不可修改；系统角色（ADMIN/MEMBER）禁止删除；
 * 权限绑定为 replace 语义（整体替换，事务内）；唯一约束 Service 预检 + DB UK 兜底 → 409。
 */
public interface RoleService {

    RoleVO create(CreateRoleRequest request);

    RoleVO getById(Long id);

    /** 全量角色列表（roles 为小表，不做分页；按 id 排序保证稳定） */
    List<RoleVO> list();

    RoleVO update(Long id, UpdateRoleRequest request);

    /** 删除角色（系统角色拒绝 400；绑定经 FK CASCADE 级联清理） */
    void delete(Long id);

    /** 整体替换角色权限绑定（事务内；未知权限编码 → 404 语义由 PermissionService 保证存在性） */
    RoleVO assignPermissions(Long roleId, AssignRolePermissionsRequest request);

    /** 角色当前权限编码 */
    List<String> getPermissionCodes(Long roleId);
}
