import http from './http'
import type { Result } from '@/types/api'

/** 项目统计（total/active/archived） */
export interface ProjectStats {
  total: number
  active: number
  archived: number
}

/** Issue 统计（四维分布 + bug 计数，键为真实枚举值） */
export interface IssueStats {
  total: number
  byStatus: Record<string, number>
  byType: Record<string, number>
  byPriority: Record<string, number>
  bySeverity: Record<string, number>
  bugCount: number
}

/** 创建趋势点（近 14 天按天，无数据日补零） */
export interface TrendPoint {
  date: string
  created: number
}

/** 仪表盘总览（dashboard:view；数据范围按角色收敛） */
export interface DashboardOverview {
  projects: ProjectStats
  issues: IssueStats
  createdTrend: TrendPoint[]
}

export async function fetchDashboardOverview(): Promise<DashboardOverview> {
  const resp = await http.get<Result<DashboardOverview>>('/dashboard/overview')
  return resp.data.data as DashboardOverview
}
