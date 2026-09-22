package com.workflowx.issue.controller;

import com.workflowx.common.security.JwtPayload;
import com.workflowx.common.web.PageVO;
import com.workflowx.common.web.Result;
import com.workflowx.issue.dto.CreateCommentRequest;
import com.workflowx.issue.dto.CommentPageQuery;
import com.workflowx.issue.dto.UpdateCommentRequest;
import com.workflowx.issue.service.CommentService;
import com.workflowx.issue.vo.CommentVO;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Issue 评论接口（P8-04；ADR-018）。嵌套于项目+Issue 路径，资源边界由 Service 校验
 * （project 404 → issue 404 → comment 404，跨项目/跨 Issue 拼接一律 404）。
 * 权限三层: comment:* authority（V12）+ 项目成员数据级 + 作者本人 ownership（编辑/删除）。
 */
@Tag(name = "Issue Comments", description = "Issue 评论（P8）")
@RestController
@RequestMapping("/api/v1/projects/{projectId}/issues/{issueId}/comments")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    @PreAuthorize("hasAuthority('comment:list')")
    public Result<PageVO<CommentVO>> page(@PathVariable Long projectId, @PathVariable Long issueId,
                                          @Valid CommentPageQuery query,
                                          @AuthenticationPrincipal JwtPayload operator) {
        return Result.ok(commentService.page(projectId, issueId, query.pageNum(), query.pageSize(), operator.userId()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('comment:create')")
    public ResponseEntity<Result<CommentVO>> create(@PathVariable Long projectId, @PathVariable Long issueId,
                                                    @Valid @RequestBody CreateCommentRequest request,
                                                    @AuthenticationPrincipal JwtPayload operator) {
        CommentVO created = commentService.create(projectId, issueId, request, operator.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.of(201, "created", created));
    }

    @GetMapping("/{commentId}")
    @PreAuthorize("hasAuthority('comment:get')")
    public Result<CommentVO> getById(@PathVariable Long projectId, @PathVariable Long issueId,
                                     @PathVariable Long commentId,
                                     @AuthenticationPrincipal JwtPayload operator) {
        return Result.ok(commentService.getById(projectId, issueId, commentId, operator.userId()));
    }

    @PutMapping("/{commentId}")
    @PreAuthorize("hasAuthority('comment:update')")
    public Result<CommentVO> update(@PathVariable Long projectId, @PathVariable Long issueId,
                                    @PathVariable Long commentId,
                                    @Valid @RequestBody UpdateCommentRequest request,
                                    @AuthenticationPrincipal JwtPayload operator) {
        return Result.ok(commentService.update(projectId, issueId, commentId, request, operator.userId()));
    }

    @DeleteMapping("/{commentId}")
    @PreAuthorize("hasAuthority('comment:delete')")
    public Result<Void> delete(@PathVariable Long projectId, @PathVariable Long issueId,
                               @PathVariable Long commentId,
                               @AuthenticationPrincipal JwtPayload operator) {
        commentService.delete(projectId, issueId, commentId, operator.userId());
        return Result.ok(null);
    }
}
