package com.workflowx.dashboard.service;

import com.workflowx.dashboard.vo.DashboardOverviewVO;

import java.util.List;

/**
 * 数据统计仪表盘（P10-09/10; ADR-020）。全部指标由后端从真实业务表聚合计算，无假数据。
 * 数据范围: ADMIN 角色=全系统；其他用户=仅其 project_members 成员项目（含其 Issue/评论统计）。
 */
public interface DashboardService {

    DashboardOverviewVO overview(Long operatorId, List<String> roles);
}
