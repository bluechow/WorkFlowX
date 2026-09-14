package com.workflowx.org.vo;

import com.workflowx.org.entity.Department;

import java.time.LocalDateTime;

/** 部门视图对象（P4-01）。 */
public record DepartmentVO(
        Long id,
        Long orgId,
        Long parentId,
        String name,
        String code,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static DepartmentVO from(Department department) {
        return new DepartmentVO(department.getId(), department.getOrgId(), department.getParentId(),
                department.getName(), department.getCode(), department.getCreatedAt(), department.getUpdatedAt());
    }
}
