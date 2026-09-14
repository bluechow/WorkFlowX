package com.workflowx.org.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflowx.org.entity.Department;
import org.apache.ibatis.annotations.Mapper;

/** 部门数据访问（P4-01）。 */
@Mapper
public interface DepartmentMapper extends BaseMapper<Department> {
}
