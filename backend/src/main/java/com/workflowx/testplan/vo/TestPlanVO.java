package com.workflowx.testplan.vo;

import com.workflowx.testplan.entity.TestPlan;

import java.time.LocalDateTime;

/** 测试计划视图对象（V1.1），含执行统计（由 Service 实时计算）。 */
public record TestPlanVO(
        Long id,
        Long projectId,
        String name,
        TestPlan.TestPlanStatus status,
        Long createdBy,
        LocalDateTime createdAt,
        long total,
        long passed,
        long failed,
        long blocked,
        long pending) {
}
