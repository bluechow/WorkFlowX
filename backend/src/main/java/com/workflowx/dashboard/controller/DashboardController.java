package com.workflowx.dashboard.controller;

import com.workflowx.common.security.JwtPayload;
import com.workflowx.common.web.Result;
import com.workflowx.dashboard.service.DashboardService;
import com.workflowx.dashboard.vo.DashboardOverviewVO;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仪表盘接口（P10-10; ADR-020）。
 * dashboard:view authority（V14 种子，ADMIN 绑定）；数据范围由 Service 按角色收敛：
 * ADMIN=全系统，其他授权用户=仅其成员项目（普通 MEMBER 无该权限则 403）。
 */
@Tag(name = "Dashboard", description = "数据统计仪表盘（P10，真实业务聚合）")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public Result<DashboardOverviewVO> overview(@AuthenticationPrincipal JwtPayload operator) {
        return Result.ok(dashboardService.overview(operator.userId(), operator.roles()));
    }
}
