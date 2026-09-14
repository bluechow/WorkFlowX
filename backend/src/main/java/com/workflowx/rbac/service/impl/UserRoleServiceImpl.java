package com.workflowx.rbac.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.rbac.entity.Role;
import com.workflowx.rbac.mapper.RoleMapper;
import com.workflowx.rbac.mapper.UserRoleMapper;
import com.workflowx.rbac.service.UserRoleService;
import com.workflowx.rbac.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户-角色绑定领域服务实现（P3-02）。
 * 幂等语义: 重复分配查重后跳过；回收未绑定为空操作（不抛异常）。
 */
@Service
@RequiredArgsConstructor
public class UserRoleServiceImpl implements UserRoleService {

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    @Override
    @Transactional
    public void assignRole(Long userId, String roleCode) {
        requireRoleByCode(roleCode);
        if (userRoleMapper.existsBinding(userId, roleIdByCode(roleCode)) == 0) {
            userRoleMapper.insertByRoleCode(userId, roleCode);
        }
    }

    @Override
    @Transactional
    public void revokeRole(Long userId, String roleCode) {
        requireRoleByCode(roleCode);
        userRoleMapper.deleteByRoleCode(userId, roleCode);
    }

    @Override
    public List<String> findRoleCodesByUserId(Long userId) {
        return userRoleMapper.findRoleCodesByUserId(userId);
    }

    @Override
    public List<RoleVO> findRolesByUserId(Long userId) {
        return userRoleMapper.findRoleCodesByUserId(userId).stream()
                .map(code -> RoleVO.from(roleMapper.selectOne(
                        new LambdaQueryWrapper<Role>().eq(Role::getCode, code))))
                .toList();
    }

    private void requireRoleByCode(String roleCode) {
        if (roleMapper.selectCount(new LambdaQueryWrapper<Role>().eq(Role::getCode, roleCode)) == 0) {
            throw new ResourceNotFoundException("role", roleCode);
        }
    }

    private Long roleIdByCode(String roleCode) {
        return roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, roleCode)).getId();
    }
}
