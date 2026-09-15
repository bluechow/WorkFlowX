package com.workflowx.issue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflowx.issue.entity.Issue;
import org.apache.ibatis.annotations.Mapper;

/** Issue 数据访问（P6-01）：CRUD 由 BaseMapper 提供；查询条件由 Service 构造。 */
@Mapper
public interface IssueMapper extends BaseMapper<Issue> {
}
