package com.workflowx.audit.controller;

import com.workflowx.audit.dto.AuditLogPageQuery;
import com.workflowx.audit.service.AuditService;
import com.workflowx.audit.vo.AuditLogVO;
import com.workflowx.common.web.PageVO;
import com.workflowx.common.web.Result;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志接口（P10-06; ADR-020）。
 * 高敏感系统资源：audit:list/audit:get authority（V14 种子，仅 ADMIN 绑定）；
 * 不给普通项目成员/普通角色开放——数据范围即权限本身（全系统操作事实）。
 */
@Tag(name = "Audit Logs", description = "审计日志（P10，高敏感，仅管理角色）")
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasAuthority('audit:list')")
    public Result<PageVO<AuditLogVO>> query(@Valid AuditLogPageQuery query) {
        return Result.ok(auditService.query(new AuditService.AuditQueryParams(
                query.operator(), query.module(), query.action(), query.success(),
                query.target(), query.traceId(), query.beginTime(), query.endTime(),
                query.pageNum(), query.pageSize())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('audit:get')")
    public Result<AuditLogVO> getById(@PathVariable Long id) {
        return Result.ok(auditService.getById(id));
    }
}
