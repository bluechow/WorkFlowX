package com.workflowx.audit.vo;

import com.workflowx.audit.entity.AuditLog;

import java.time.LocalDateTime;

/** 审计日志视图对象（P10-06）。不含任何敏感凭证字段。 */
public record AuditLogVO(
        Long id,
        Long userId,
        String module,
        String action,
        String httpMethod,
        String uri,
        String ip,
        String target,
        String summary,
        Boolean success,
        String traceId,
        String userAgent,
        LocalDateTime createdAt) {

    public static AuditLogVO from(AuditLog entry) {
        return new AuditLogVO(entry.getId(), entry.getUserId(), entry.getModule(), entry.getAction(),
                entry.getHttpMethod(), entry.getUri(), entry.getIp(), entry.getTarget(),
                entry.getSummary(), entry.getSuccess(), entry.getTraceId(), entry.getUserAgent(),
                entry.getCreatedAt());
    }
}
