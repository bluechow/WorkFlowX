package com.workflowx.rbac.service;

import com.workflowx.rbac.vo.RoleVO;

import java.util.List;

/**
 * 用户-角色绑定领域服务（P3-02）。
 * 分配幂等（重复分配静默成功）；回收未绑定为空操作；角色/用户不存在 → 404。
 */
public interface UserRoleService {

    /** 给用户分配角色（按角色编码，幂等） */
    void assignRole(Long userId, String roleCode);

    /** 回收用户角色（按角色编码，未绑定静默成功） */
    void revokeRole(Long userId, String roleCode);

    /** 用户的角色编码列表 */
    List<String> findRoleCodesByUserId(Long userId);

    /** 用户的角色详情列表 */
    List<RoleVO> findRolesByUserId(Long userId);
}
