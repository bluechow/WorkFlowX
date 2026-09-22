package com.workflowx.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志实体，映射 V14 表 audit_logs（data-dictionary §15 + ADR-020 扩展）。
 * 记录高价值操作事实；summary 为白名单字段组装的业务摘要，严禁密码/令牌等敏感数据。
 */
@Data
@TableName("audit_logs")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人；匿名请求（如登录失败时的未知用户）为 null */
    private Long userId;

    private String module;

    private String action;

    private String httpMethod;

    private String uri;

    private String ip;

    /** 目标对象，格式 "issue:123" / "user:45" */
    private String target;

    private String summary;

    private Boolean success;

    private String traceId;

    private String userAgent;

    private LocalDateTime createdAt;
}
