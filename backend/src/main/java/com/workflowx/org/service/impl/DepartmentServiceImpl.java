package com.workflowx.org.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.common.exception.BusinessException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.org.dto.CreateDepartmentRequest;
import com.workflowx.org.entity.Department;
import com.workflowx.org.mapper.DepartmentMapper;
import com.workflowx.org.mapper.OrganizationMemberMapper;
import com.workflowx.org.mapper.OrganizationMapper;
import com.workflowx.org.service.DepartmentService;
import com.workflowx.org.vo.DepartmentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 部门领域服务实现（P4-01）。
 * 树规则（ADR-013）: parent 同组织、≠自身、非自身后代；删除父部门 → 子级提升为根（FK SET NULL）、
 * 成员 departmentId 置 NULL（FK SET NULL）。
 */
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final OrganizationMapper organizationMapper;
    private final OrganizationMemberMapper memberMapper;

    @Override
    @Transactional
    public DepartmentVO create(Long orgId, CreateDepartmentRequest request) {
        requireOrg(orgId);
        if (request.parentId() != null) {
            Department parent = requireDepartment(request.parentId());
            if (!parent.getOrgId().equals(orgId)) {
                throw new BusinessException(400, "父部门必须属于同一组织");
            }
        }
        Department department = new Department();
        department.setOrgId(orgId);
        department.setParentId(request.parentId());
        department.setName(request.name());
        department.setCode(request.code());
        try {
            departmentMapper.insert(department);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "部门 code 在组织内已存在: " + request.code());
        }
        return DepartmentVO.from(requireDepartment(department.getId()));
    }

    @Override
    public DepartmentVO getById(Long id) {
        return DepartmentVO.from(requireDepartment(id));
    }

    @Override
    public List<DepartmentVO> listByOrg(Long orgId) {
        requireOrg(orgId);
        return departmentMapper.selectList(
                        new LambdaQueryWrapper<Department>().eq(Department::getOrgId, orgId).orderByAsc(Department::getId))
                .stream().map(DepartmentVO::from).toList();
    }

    @Override
    @Transactional
    public DepartmentVO update(Long id, String name, Long parentId) {
        Department department = requireDepartment(id);
        if (parentId != null) {
            if (parentId.equals(id)) {
                throw new BusinessException(400, "父部门不能是自身");
            }
            Department parent = requireDepartment(parentId);
            if (!parent.getOrgId().equals(department.getOrgId())) {
                throw new BusinessException(400, "父部门必须属于同一组织");
            }
            // 防环: 从新父级沿 parent 链上溯，遇到自身即成环
            Long cursor = parentId;
            while (cursor != null) {
                if (cursor.equals(id)) {
                    throw new BusinessException(400, "父部门不能是自身的后代");
                }
                Department step = departmentMapper.selectById(cursor);
                cursor = step == null ? null : step.getParentId();
            }
            department.setParentId(parentId);
        }
        if (name != null) {
            department.setName(name);
        }
        try {
            departmentMapper.updateById(department);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "部门 code 在组织内已存在");
        }
        return DepartmentVO.from(requireDepartment(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireDepartment(id);
        // 子部门 parent 置 NULL（提升为根）、成员 department_id 置 NULL（FK ON DELETE SET NULL）
        departmentMapper.deleteById(id);
    }

    private Department requireDepartment(Long id) {
        Department department = departmentMapper.selectById(id);
        if (department == null) {
            throw new ResourceNotFoundException("department", id);
        }
        return department;
    }

    private void requireOrg(Long orgId) {
        if (organizationMapper.selectById(orgId) == null) {
            throw new ResourceNotFoundException("organization", orgId);
        }
    }
}
