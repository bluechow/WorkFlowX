package com.workflowx.system.controller;

import com.workflowx.common.web.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * 基础健康检查（Phase 1 任务 1-3.7）。
 * 对外提供统一响应结构的健康信息；依赖级健康状态（DB/Redis）由
 * Spring Actuator /actuator/health 提供，仅供基础设施探针使用。
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.ok(Map.of(
                "status", "UP",
                "service", "workflowx-backend",
                "checkedAt", Instant.now().toString()
        ));
    }
}
