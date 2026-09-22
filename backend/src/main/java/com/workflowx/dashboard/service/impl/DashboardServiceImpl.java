package com.workflowx.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.workflowx.dashboard.service.DashboardService;
import com.workflowx.dashboard.vo.DashboardOverviewVO;
import com.workflowx.dashboard.vo.DashboardOverviewVO.IssueStats;
import com.workflowx.dashboard.vo.DashboardOverviewVO.ProjectStats;
import com.workflowx.dashboard.vo.DashboardOverviewVO.TrendPoint;
import com.workflowx.issue.mapper.IssueMapper;
import com.workflowx.project.entity.Project;
import com.workflowx.project.mapper.ProjectMapper;
import com.workflowx.project.mapper.ProjectMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘实现（P10-10; ADR-020）。
 * 全部指标为数据库 GROUP BY 聚合（无全表捞取到内存计算）；scope 下推到 WHERE project_id IN。
 * 项目状态与 Issue 枚举分组的键以真实数据为准（无数据的维度不出现在 Map 中，由前端兜底渲染）。
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final int TREND_DAYS = 14;

    private final IssueMapper issueMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;

    @Override
    public DashboardOverviewVO overview(Long operatorId, List<String> roles) {
        boolean globalScope = roles != null && roles.contains("ADMIN");
        List<Long> scopeProjectIds = globalScope ? null
                : projectMemberMapper.findAllProjectIdsByUserId(operatorId);
        return new DashboardOverviewVO(
                projectStats(scopeProjectIds),
                issueStats(scopeProjectIds),
                createdTrend(scopeProjectIds));
    }

    private ProjectStats projectStats(List<Long> scopeProjectIds) {
        long total = projectMapper.selectCount(inScope(new QueryWrapper<Project>(), scopeProjectIds));
        long archived = projectMapper.selectCount(inScope(new QueryWrapper<Project>(), scopeProjectIds)
                .eq("status", "ARCHIVED"));
        return new ProjectStats(total, total - archived, archived);
    }

    private IssueStats issueStats(List<Long> scopeProjectIds) {
        long total = countGrouped(scopeProjectIds, null);
        return new IssueStats(
                total,
                groupedCounts(scopeProjectIds, "status"),
                groupedCounts(scopeProjectIds, "type"),
                groupedCounts(scopeProjectIds, "priority"),
                groupedCounts(scopeProjectIds, "severity"),
                countGrouped(scopeProjectIds, "type = 'BUG'"));
    }

    /** 近 14 天创建趋势（按天聚合，无数据日补零，保证前端曲线连续）。 */
    private List<TrendPoint> createdTrend(List<Long> scopeProjectIds) {
        LocalDate today = LocalDate.now();
        LocalDateTime since = today.minusDays(TREND_DAYS - 1L).atStartOfDay();
        QueryWrapper<com.workflowx.issue.entity.Issue> wrapper =
                inScope(new QueryWrapper<com.workflowx.issue.entity.Issue>(), scopeProjectIds)
                        .select("DATE(created_at) AS d", "COUNT(*) AS cnt")
                        .ge("created_at", since)
                        .groupBy("d");
        Map<String, Long> byDate = new HashMap<>();
        for (Map<String, Object> row : issueMapper.selectMaps(wrapper)) {
            Object d = row.get("d");
            Object cnt = row.get("cnt");
            if (d != null && cnt != null) {
                byDate.put(String.valueOf(d), ((Number) cnt).longValue());
            }
        }
        List<TrendPoint> trend = new ArrayList<>();
        for (int i = 0; i < TREND_DAYS; i++) {
            LocalDate day = today.minusDays(TREND_DAYS - 1L - i);
            trend.add(new TrendPoint(day.toString(), byDate.getOrDefault(day.toString(), 0L)));
        }
        return trend;
    }

    private long countGrouped(List<Long> scopeProjectIds, String extraCondition) {
        QueryWrapper<com.workflowx.issue.entity.Issue> wrapper = inScope(new QueryWrapper<>(), scopeProjectIds);
        if (extraCondition != null) {
            wrapper.apply(extraCondition);
        }
        return issueMapper.selectCount(wrapper);
    }

    private Map<String, Long> groupedCounts(List<Long> scopeProjectIds, String column) {
        QueryWrapper<com.workflowx.issue.entity.Issue> wrapper =
                inScope(new QueryWrapper<com.workflowx.issue.entity.Issue>(), scopeProjectIds)
                        .select(column + " AS k", "COUNT(*) AS cnt")
                        .isNotNull(column)
                        .groupBy(column);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : issueMapper.selectMaps(wrapper)) {
            Object key = row.get("k");
            Object cnt = row.get("cnt");
            if (key != null && cnt != null) {
                counts.put(String.valueOf(key), ((Number) cnt).longValue());
            }
        }
        return counts;
    }

    private <T> QueryWrapper<T> inScope(QueryWrapper<T> wrapper, List<Long> scopeProjectIds) {
        if (scopeProjectIds != null) {
            wrapper.in("project_id", scopeProjectIds);
        }
        return wrapper;
    }
}
