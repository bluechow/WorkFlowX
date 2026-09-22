package com.workflowx.issue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflowx.issue.entity.Attachment;
import org.apache.ibatis.annotations.Mapper;

/** Issue 附件元数据访问（P8-06）：CRUD 由 BaseMapper 提供；查询条件由 Service 构造。 */
@Mapper
public interface AttachmentMapper extends BaseMapper<Attachment> {
}
