package com.workflowx.issue.controller;

import com.workflowx.common.security.JwtPayload;
import com.workflowx.common.web.PageVO;
import com.workflowx.common.web.Result;
import com.workflowx.issue.dto.CreateIssueRequest;
import com.workflowx.issue.dto.IssuePageQuery;
import com.workflowx.issue.dto.UpdateIssueRequest;
import com.workflowx.issue.service.IssueService;
import com.workflowx.issue.vo.IssueVO;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Issue 接口（P6-03）。嵌套于项目路径，跨项目访问在 Service 层防为 404。
 * 权限双层: hasAuthority（issue:*，V10 种子）+ Service 数据级项目成员校验（ADR-016）。
 * 状态流转矩阵属 Phase 7；本阶段 PATCH status 仅校验枚举合法。
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/issues")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class IssueController {

    private final IssueService issueService;
    private final com.workflowx.issue.service.WorkflowService workflowService;

    @GetMapping
    @PreAuthorize("hasAuthority('issue:list')")
    public Result<PageVO<IssueVO>> page(@PathVariable Long projectId, @Valid IssuePageQuery query) {
        return Result.ok(issueService.page(projectId, query));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('issue:create')")
    public ResponseEntity<Result<IssueVO>> create(@PathVariable Long projectId,
                                                  @Valid @RequestBody CreateIssueRequest request,
                                                  @AuthenticationPrincipal JwtPayload operator) {
        IssueVO created = issueService.create(projectId, request, operator.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.of(201, "created", created));
    }

    @GetMapping("/{issueId}")
    @PreAuthorize("hasAuthority('issue:get')")
    public Result<IssueVO> getById(@PathVariable Long projectId, @PathVariable Long issueId) {
        return Result.ok(issueService.getById(projectId, issueId));
    }

    @PutMapping("/{issueId}")
    @PreAuthorize("hasAuthority('issue:update')")
    public Result<IssueVO> update(@PathVariable Long projectId, @PathVariable Long issueId,
                                  @Valid @RequestBody UpdateIssueRequest request,
                                  @AuthenticationPrincipal JwtPayload operator) {
        return Result.ok(issueService.update(projectId, issueId, request, operator.userId()));
    }

    /** 分派/取消分派（P6-05 边界内：assignee 变更，走 issue:assign authority） */
    @PatchMapping("/{issueId}/assignee")
    @PreAuthorize("hasAuthority('issue:assign')")
    public Result<IssueVO> assign(@PathVariable Long projectId, @PathVariable Long issueId,
                                  @RequestBody MapBody body,
                                  @AuthenticationPrincipal JwtPayload operator) {
        Long assigneeId = body == null ? null : body.assigneeId();
        // 复用 update 的 assignee 校验链：仅传 assignee 字段（null=清空走 0 哨兵约定之外——
        // 本端点语义为"设置分派"，取消分派传 0）
        UpdateIssueRequest request = new UpdateIssueRequest(null, null, null, null,
                assigneeId == null ? 0L : assigneeId);
        return Result.ok(issueService.update(projectId, issueId, request, operator.userId()));
    }

    /**
     * 状态流转（P7-04，ADR-017）：issue:transition authority + 正式矩阵校验（非法 409）+
     * 条件 UPDATE 并发保护（fromStatus 不匹配当前状态 → 409）。
     */
    @PatchMapping("/{issueId}/status")
    @PreAuthorize("hasAuthority('issue:transition')")
    public Result<IssueVO> transition(@PathVariable Long projectId, @PathVariable Long issueId,
                                      @Valid @RequestBody com.workflowx.issue.dto.TransitionIssueStatusRequest request,
                                      @AuthenticationPrincipal JwtPayload operator) {
        return Result.ok(workflowService.transition(projectId, issueId,
                request.fromStatus(), request.toStatus(), operator.userId()));
    }

    /** 简单 body 载体（assigneeId 可空表示取消分派） */
    public record MapBody(Long assigneeId) {
    }
}
