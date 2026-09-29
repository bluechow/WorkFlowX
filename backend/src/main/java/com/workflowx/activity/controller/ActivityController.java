package com.workflowx.activity.controller;

import com.workflowx.activity.service.ActivityService;
import com.workflowx.activity.vo.ActivityVO;
import com.workflowx.common.web.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 项目活动流（V18，Final Edition FP-2）：读权限=issue:list（与工作项读一致）。
 */
@Tag(name = "Activities", description = "项目活动流（V18）")
@RestController
@RequestMapping("/api/v1/projects/{projectId}/activities")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ActivityController {

    private final ActivityService activityService;

    @Operation(summary = "项目活动（倒序，limit 默认 50 上限 200）")
    @GetMapping
    @PreAuthorize("hasAuthority('issue:list')")
    public Result<List<ActivityVO>> page(@PathVariable Long projectId,
                                         @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(activityService.page(projectId, limit));
    }
}
