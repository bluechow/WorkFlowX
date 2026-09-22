package com.workflowx.issue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflowx.issue.entity.IssueComment;
import org.apache.ibatis.annotations.Mapper;

/** Issue 评论数据访问（P8-02）：CRUD 由 BaseMapper 提供；查询条件由 Service 构造。 */
@Mapper
public interface CommentMapper extends BaseMapper<IssueComment> {
}
