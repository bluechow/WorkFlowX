package com.workflowx.issue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflowx.issue.entity.IssueLabel;
import org.apache.ibatis.annotations.Mapper;

/** 工作项-标签绑定（V17）；批量装配由 Service 层两次查询 + Java 分组完成（避免拼接 SQL）。 */
@Mapper
public interface IssueLabelMapper extends BaseMapper<IssueLabel> {
}
