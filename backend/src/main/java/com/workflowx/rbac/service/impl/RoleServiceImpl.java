package com.workflowx.rbac.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.common.exception.BusinessException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.rbac.RbacConstants;
import com.workflowx.rbac.dto.AssignRolePermissionsRequest;
import com.workflowx.rbac.dto.CreateRoleRequest;
import com.workflowx.rbac.dto.UpdateRoleRequest;
import com.workflowx.rbac.entity.Permission;
import com.workflowx.rbac.entity.Role;
import com.workflowx.rbac.mapper.PermissionMapper;
import com.workflowx.rbac.mapper.RoleMapper;
import com.workflowx.rbac.mapper.RolePermissionMapper;
import com.workflowx.rbac.service.RoleService;
import com.workflowx.rbac.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色领域服务实现（P3-02）。
 * 异常约定与 UserServiceImpl 一致: 缺失 → 404；唯一冲突 → 409；系统保护/业务规则 → 400。
 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;

    @Override
    @Transactional
    public RoleVO create(CreateRoleRequest request) {
        Role role = new Role();
        role.setCode(request.code());
        role.setName(request.name());
        role.setDescription(request.description());
        try {
            roleMapper.insert(role);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "role code 已存在: " + request.code());
        }
        return RoleVO.from(requireRole(role.getId()));
    }

    @Override
    public RoleVO getById(Long id) {
        return RoleVO.from(requireRole(id));
    }

    @Override
    public List<RoleVO> list() {
        return roleMapper.selectList(new LambdaQueryWrapper<Role>().orderByAsc(Role::getId))
                .stream().map(RoleVO::from).toList();
    }

    @Override
    @Transactional
    public RoleVO update(Long id, UpdateRoleRequest request) {
        Role role = requireRole(id);
        if (request.name() != null) {
            role.setName(request.name());
        }
        if (request.description() != null) {
            role.setDescription(request.description());
        }
        roleMapper.updateById(role);
        return RoleVO.from(requireRole(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Role role = requireRole(id);
        if (RbacConstants.isSystemRole(role.getCode())) {
            throw new BusinessException(400, "系统角色禁止删除: " + role.getCode());
        }
        // user_roles / role_permissions 经 FK ON DELETE CASCADE 级联清理
        roleMapper.deleteById(id);
    }

    @Override
    @Transactional
    public RoleVO assignPermissions(Long roleId, AssignRolePermissionsRequest request) {
        requireRole(roleId);
        List<String> current = rolePermissionMapper.findPermissionCodesByRoleId(roleId);
        var target = request.permissionCodes();

        // 回收: 当前有、目标没有
        for (String code : current) {
            if (!target.contains(code)) {
                rolePermissionMapper.deleteByPermissionCode(roleId, code);
            }
        }
        // 新增: 目标有、当前没有（未知编码 → 404，保证绑定只指向存在的权限）
        for (String code : target) {
            if (!current.contains(code)) {
                if (permissionMapper.selectCount(
                        new LambdaQueryWrapper<Permission>().eq(Permission::getCode, code)) == 0) {
                    throw new ResourceNotFoundException("permission", code);
                }
                rolePermissionMapper.insertByPermissionCode(roleId, code);
            }
        }
        return RoleVO.from(requireRole(roleId));
    }

    @Override
    public List<String> getPermissionCodes(Long roleId) {
        requireRole(roleId);
        return rolePermissionMapper.findPermissionCodesByRoleId(roleId);
    }

    private Role requireRole(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new ResourceNotFoundException("role", id);
        }
        return role;
    }
}
