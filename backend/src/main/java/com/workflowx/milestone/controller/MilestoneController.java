package com.workflowx.milestone.controller;

import com.workflowx.common.security.JwtPayload;
import com.workflowx.common.web.Result;
import com.workflowx.milestone.dto.MilestoneRequests;
import com.workflowx.milestone.service.MilestoneService;
import com.workflowx.milestone.vo.MilestoneVO;
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
 * 里程碑（V19，Final Edition FP-4）：读=issue:list；管理=project:update。
 */
@Tag(name = "Milestones", description = "里程碑（V19，轻量计划）")
@RestController
@RequestMapping("/api/v1/projects/{projectId}/milestones")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MilestoneController {

    private final MilestoneService milestoneService;

    @Operation(summary = "里程碑列表（含进度统计）")
    @GetMapping
    @PreAuthorize("hasAuthority('issue:list')")
    public Result<List<MilestoneVO>> list(@PathVariable Long projectId) {
        return Result.ok(milestoneService.list(projectId));
    }

    @Operation(summary = "创建里程碑")
    @PostMapping
    @PreAuthorize("hasAuthority('project:update')")
    public ResponseEntity<Result<MilestoneVO>> create(@PathVariable Long projectId,
                                                      @jakarta.validation.Valid @RequestBody MilestoneRequests.CreateMilestoneRequest request,
                                                      @AuthenticationPrincipal JwtPayload operator) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.of(201, "created", milestoneService.create(projectId, request, operator.userId())));
    }

    @Operation(summary = "更新里程碑（重命名/目标日期/完成态）")
    @PutMapping("/{milestoneId}")
    @PreAuthorize("hasAuthority('project:update')")
    public Result<MilestoneVO> update(@PathVariable Long projectId, @PathVariable Long milestoneId,
                                      @jakarta.validation.Valid @RequestBody MilestoneRequests.UpdateMilestoneRequest request) {
        return Result.ok(milestoneService.update(projectId, milestoneId, request));
    }

    @Operation(summary = "删除里程碑（归属工作项退回未归属）")
    @DeleteMapping("/{milestoneId}")
    @PreAuthorize("hasAuthority('project:update')")
    public Result<Void> delete(@PathVariable Long projectId, @PathVariable Long milestoneId) {
        milestoneService.delete(projectId, milestoneId);
        return Result.ok(null);
    }
}
