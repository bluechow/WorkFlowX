package com.workflowx.quality.controller;

import com.workflowx.common.web.Result;
import com.workflowx.quality.dto.QualityDTOs.HeatmapVO;
import com.workflowx.quality.dto.QualityDTOs.TraceabilityVO;
import com.workflowx.quality.service.QualityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 质量分析接口（创新点：覆盖率热力图 + 需求-测试追溯矩阵）。
 * 权限：读 = testcase:list（与用例库读一致）。
 */
@Tag(name = "Quality Analytics", description = "质量分析：覆盖率热力图 + 追溯矩阵")
@RestController
@RequestMapping("/api/v1/projects/{projectId}/quality")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class QualityController {

    private final QualityService qualityService;

    @Operation(summary = "覆盖率热力图（按用例目录聚合，含风险评级）")
    @GetMapping("/heatmap")
    @PreAuthorize("hasAuthority('testcase:list')")
    public Result<HeatmapVO> heatmap(@PathVariable Long projectId) {
        return Result.ok(qualityService.heatmap(projectId));
    }

    @Operation(summary = "需求-测试追溯矩阵（目录×用例×执行结果×关联Bug）")
    @GetMapping("/traceability")
    @PreAuthorize("hasAuthority('testcase:list')")
    public Result<TraceabilityVO> traceability(@PathVariable Long projectId) {
        return Result.ok(qualityService.traceability(projectId));
    }
}
