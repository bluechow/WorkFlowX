package com.workflowx.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflowx.rbac.entity.Permission;
import org.apache.ibatis.annotations.Mapper;

/** 权限数据访问（P3-02）：CRUD 由 BaseMapper 提供，跨表查询走 UserRoleMapper/RolePermissionMapper。 */
@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}
