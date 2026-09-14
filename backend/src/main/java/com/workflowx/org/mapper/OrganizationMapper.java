package com.workflowx.org.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflowx.org.entity.Organization;
import org.apache.ibatis.annotations.Mapper;

/** 组织数据访问（P4-01）：CRUD 由 BaseMapper 提供。 */
@Mapper
public interface OrganizationMapper extends BaseMapper<Organization> {
}
