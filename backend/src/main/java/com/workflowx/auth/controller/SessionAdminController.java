package com.workflowx.auth.controller;

import com.workflowx.auth.dto.SessionVO;
import com.workflowx.auth.service.SessionAdminService;
import com.workflowx.common.security.JwtPayload;
import com.workflowx.common.web.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 在线会话管理（Phase A-⑤）：ADMIN 专属。
 * 权限复用 user:status（用户状态管理语义，禁用即踢线的同源能力），不新增权限码；
 * self 资源式的踢下线禁止（防误操作，走退出登录）。
 */
@Tag(name = "Sessions", description = "在线会话管理（ADMIN，Phase A-⑤）")
@RestController
@RequestMapping("/api/v1/auth/sessions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class SessionAdminController {

    private final SessionAdminService sessionAdminService;
    private final com.workflowx.audit.service.AuditService auditService;

    @Operation(summary = "在线会话列表（含剩余有效期）")
    @GetMapping
    @PreAuthorize("hasAuthority('user:status')")
    public Result<List<SessionVO>> list(@AuthenticationPrincipal JwtPayload principal) {
        return Result.ok(sessionAdminService.listActiveSessions(principal.userId()));
    }

    @Operation(summary = "踢下线（删除目标用户会话，下一个请求即 401）")
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('user:status')")
    public ResponseEntity<Result<Void>> kick(@AuthenticationPrincipal JwtPayload principal,
                                             @PathVariable Long userId) {
        sessionAdminService.kick(principal.userId(), userId);
        auditService.record("AUTH", "STATUS", "user:" + userId, "管理员踢下线会话", true, principal.userId());
        return ResponseEntity.status(HttpStatus.OK).body(Result.ok(null));
    }
}
