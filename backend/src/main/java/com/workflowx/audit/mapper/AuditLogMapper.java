package com.workflowx.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflowx.audit.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

/** 审计日志数据访问（P10-03）：CRUD 由 BaseMapper 提供；查询条件由 Service 构造。 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
