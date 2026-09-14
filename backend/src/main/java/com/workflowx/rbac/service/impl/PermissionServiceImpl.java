package com.workflowx.rbac.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.common.exception.BusinessException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.rbac.RbacConstants;
import com.workflowx.rbac.dto.CreatePermissionRequest;
import com.workflowx.rbac.entity.Permission;
import com.workflowx.rbac.mapper.PermissionMapper;
import com.workflowx.rbac.mapper.RolePermissionMapper;
import com.workflowx.rbac.service.PermissionService;
import com.workflowx.rbac.vo.PermissionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 权限领域服务实现（P3-02）。 */
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;

    @Override
    @Transactional
    public PermissionVO create(CreatePermissionRequest request) {
        Permission permission = new Permission();
        permission.setCode(request.code());
        permission.setName(request.name());
        permission.setType(com.workflowx.rbac.entity.PermissionType.API);
        permission.setDescription(request.description());
        try {
            permissionMapper.insert(permission);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "permission code 已存在: " + request.code());
        }
        return PermissionVO.from(requirePermission(permission.getId()));
    }

    @Override
    public PermissionVO getById(Long id) {
        return PermissionVO.from(requirePermission(id));
    }

    @Override
    public PermissionVO getByCode(String code) {
        Permission permission = permissionMapper.selectOne(
                new LambdaQueryWrapper<Permission>().eq(Permission::getCode, code));
        if (permission == null) {
            throw new ResourceNotFoundException("permission", code);
        }
        return PermissionVO.from(permission);
    }

    @Override
    public List<PermissionVO> list() {
        return permissionMapper.selectList(new LambdaQueryWrapper<Permission>().orderByAsc(Permission::getId))
                .stream().map(PermissionVO::from).toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Permission permission = requirePermission(id);
        if (RbacConstants.isSystemPermission(permission.getCode())) {
            throw new BusinessException(400, "系统权限禁止删除: " + permission.getCode());
        }
        // role_permissions 经 FK ON DELETE CASCADE 级联清理
        permissionMapper.deleteById(id);
    }

    @Override
    public List<String> findPermissionCodesByUserId(Long userId) {
        return rolePermissionMapper.findPermissionCodesByUserId(userId);
    }

    private Permission requirePermission(Long id) {
        Permission permission = permissionMapper.selectById(id);
        if (permission == null) {
            throw new ResourceNotFoundException("permission", id);
        }
        return permission;
    }
}
