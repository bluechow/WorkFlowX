package com.workflowx.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflowx.rbac.entity.Role;
import org.apache.ibatis.annotations.Mapper;

/** 角色数据访问（P3-02）：CRUD 由 BaseMapper 提供，条件查询由调用方构造。 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}
