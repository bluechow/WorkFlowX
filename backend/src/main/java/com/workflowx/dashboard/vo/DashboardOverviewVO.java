package com.workflowx.dashboard.vo;

import java.util.List;
import java.util.Map;

/**
 * 仪表盘总览视图对象（P10-09）。全部数字来自真实业务表聚合（projects/issues）。
 * 趋势仅提供创建趋势——Issue 无"解决时刻"时间戳，不做假 resolved 趋势（ADR-020）。
 */
public record DashboardOverviewVO(
        ProjectStats projects,
        IssueStats issues,
        List<TrendPoint> createdTrend) {

    /** 项目统计：total/active/archived */
    public record ProjectStats(long total, long active, long archived) {
    }

    /** Issue 统计：total + 四维分布 + bug 计数 */
    public record IssueStats(
            long total,
            Map<String, Long> byStatus,
            Map<String, Long> byType,
            Map<String, Long> byPriority,
            Map<String, Long> bySeverity,
            long bugCount) {
    }

    /** 创建趋势点（按天，近 14 天，无数据日补零） */
    public record TrendPoint(String date, long created) {
    }
}
