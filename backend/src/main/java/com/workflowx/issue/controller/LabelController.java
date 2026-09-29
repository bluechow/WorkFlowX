package com.workflowx.issue.controller;

import com.workflowx.common.security.JwtPayload;
import com.workflowx.common.web.Result;
import com.workflowx.issue.dto.LabelRequests;
import com.workflowx.issue.service.LabelService;
import com.workflowx.issue.vo.LabelVO;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工作项标签（V17，Final Edition FP-1）。
 * 读=issue:list（任何能看工作项的人都要能看标签名）；管理=issue:update；无新权限码。
 */
@Tag(name = "Labels", description = "工作项标签（项目内，V17）")
@RestController
@RequestMapping("/api/v1/projects/{projectId}/labels")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class LabelController {

    private final LabelService labelService;

    @Operation(summary = "标签列表")
    @GetMapping
    @PreAuthorize("hasAuthority('issue:list')")
    public Result<List<LabelVO>> list(@PathVariable Long projectId) {
        return Result.ok(labelService.list(projectId));
    }

    @Operation(summary = "创建标签")
    @PostMapping
    @PreAuthorize("hasAuthority('issue:update')")
    public ResponseEntity<Result<LabelVO>> create(@PathVariable Long projectId,
                                                  @jakarta.validation.Valid @RequestBody LabelRequests.CreateLabelRequest request,
                                                  @AuthenticationPrincipal JwtPayload operator) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.of(201, "created", labelService.create(projectId, request, operator.userId())));
    }

    @Operation(summary = "更新标签（重命名/改色）")
    @PutMapping("/{labelId}")
    @PreAuthorize("hasAuthority('issue:update')")
    public Result<LabelVO> update(@PathVariable Long projectId, @PathVariable Long labelId,
                                  @jakarta.validation.Valid @RequestBody LabelRequests.UpdateLabelRequest request) {
        return Result.ok(labelService.update(projectId, labelId, request));
    }

    @Operation(summary = "删除标签（绑定级联解除）")
    @DeleteMapping("/{labelId}")
    @PreAuthorize("hasAuthority('issue:update')")
    public Result<Void> delete(@PathVariable Long projectId, @PathVariable Long labelId) {
        labelService.delete(projectId, labelId);
        return Result.ok(null);
    }
}
