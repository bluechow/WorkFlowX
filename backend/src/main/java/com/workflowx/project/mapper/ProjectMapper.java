package com.workflowx.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflowx.project.entity.Project;
import org.apache.ibatis.annotations.Mapper;

/** 项目数据访问（P5-01）：CRUD 由 BaseMapper 提供。 */
@Mapper
public interface ProjectMapper extends BaseMapper<Project> {
}
