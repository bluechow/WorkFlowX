package com.workflowx.audit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflowx.audit.entity.AuditLog;
import com.workflowx.audit.mapper.AuditLogMapper;
import com.workflowx.audit.service.AuditService;
import com.workflowx.audit.vo.AuditLogVO;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.common.web.PageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 审计日志实现（P10-03; ADR-020）。
 * record: 同步同事务写入；Web 上下文自动采集（MDC traceId + 当前 HttpServletRequest）。
 * 查询: 全部条件下推 SQL，分页由数据库完成（不在 Java 内存分页）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogMapper auditLogMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordStandalone(String module, String action, String target, String summary,
                                 boolean success, Long userId) {
        auditLogMapper.insert(buildEntry(module, action, target, summary, success, userId));
    }

    @Override
    public void record(String module, String action, String target, String summary, boolean success, Long userId) {
        auditLogMapper.insert(buildEntry(module, action, target, summary, success, userId));
    }

    /** 组装审计条目；Web 上下文自动采集（MDC traceId + 当前请求），无请求上下文容错。 */
    private AuditLog buildEntry(String module, String action, String target, String summary,
                                boolean success, Long userId) {
        AuditLog entry = new AuditLog();
        entry.setUserId(userId);
        entry.setModule(module);
        entry.setAction(action);
        entry.setTarget(target);
        entry.setSummary(summary);
        entry.setSuccess(success);
        entry.setTraceId(MDC.get("traceId"));

        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            var request = attrs.getRequest();
            entry.setHttpMethod(request.getMethod());
            entry.setUri(truncate(request.getRequestURI(), 200));
            entry.setIp(resolveClientIp(request));
            entry.setUserAgent(truncate(request.getHeader("User-Agent"), 255));
        } else {
            // 无请求上下文（集成测试直调/系统内部调用）
            entry.setHttpMethod("NONE");
            entry.setUri("internal");
            entry.setIp("0.0.0.0");
        }
        return entry;
    }

    @Override
    public PageVO<AuditLogVO> query(AuditQueryParams params) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<AuditLog>()
                .eq(params.operatorId() != null, AuditLog::getUserId, params.operatorId())
                .eq(StringUtils.hasText(params.module()), AuditLog::getModule, params.module())
                .eq(StringUtils.hasText(params.action()), AuditLog::getAction, params.action())
                .eq(params.success() != null, AuditLog::getSuccess, params.success())
                .like(StringUtils.hasText(params.target()), AuditLog::getTarget, params.target())
                .eq(StringUtils.hasText(params.traceId()), AuditLog::getTraceId, params.traceId())
                .ge(params.beginTime() != null, AuditLog::getCreatedAt, params.beginTime())
                .le(params.endTime() != null, AuditLog::getCreatedAt, params.endTime())
                .orderByDesc(AuditLog::getCreatedAt)
                .orderByDesc(AuditLog::getId);
        IPage<AuditLog> result = auditLogMapper.selectPage(new Page<>(params.page(), params.size()), wrapper);
        return PageVO.of(result.convert(AuditLogVO::from));
    }

    @Override
    public AuditLogVO getById(Long id) {
        AuditLog entry = auditLogMapper.selectById(id);
        if (entry == null) {
            throw new ResourceNotFoundException("audit-log", id);
        }
        return AuditLogVO.from(entry);
    }

    /** X-Forwarded-For 优先（反代场景），其次 remoteAddr；截断防异常超长头。 */
    private String resolveClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = StringUtils.hasText(forwarded) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        return truncate(ip, 45);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
