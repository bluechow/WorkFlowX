import http from './http'
import type { Result } from '@/types/api'

/** 目录级覆盖统计 */
export interface DirectoryStat {
  directoryId: number | null
  directoryName: string
  caseCount: number
  executedCount: number
  passCount: number
  failCount: number
  blockedCount: number
  pendingCount: number
  bugCount: number
  coverageRate: number
  passRate: number
  riskLevel: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
}

export interface HeatmapData {
  totalDirectories: number
  totalCases: number
  totalExecuted: number
  totalBugs: number
  overallCoverage: number
  directories: DirectoryStat[]
}

export interface TraceCase {
  caseId: number
  testcaseNo: number
  title: string
  caseType: string | null
  priority: string | null
  caseStatus: string | null
  latestResult: string | null
  latestExecutedAt: string | null
  linkedBugId: number | null
  linkedBugNo: number | null
  linkedBugTitle: string | null
  linkedBugStatus: string | null
}

export interface TraceDirectory {
  directoryId: number | null
  directoryName: string
  caseCount: number
  coveredCount: number
  gapCount: number
  cases: TraceCase[]
}

export interface TraceabilityData {
  totalModules: number
  coveredModules: number
  gapModules: number
  directories: TraceDirectory[]
}

export async function fetchHeatmap(projectId: number): Promise<HeatmapData> {
  const { data } = await http.get<Result<HeatmapData>>(`/projects/${projectId}/quality/heatmap`)
  return data.data ?? { totalDirectories: 0, totalCases: 0, totalExecuted: 0, totalBugs: 0, overallCoverage: 0, directories: [] }
}

export async function fetchTraceability(projectId: number): Promise<TraceabilityData> {
  const { data } = await http.get<Result<TraceabilityData>>(`/projects/${projectId}/quality/traceability`)
  return data.data ?? { totalModules: 0, coveredModules: 0, gapModules: 0, directories: [] }
}

export const RISK_LABELS: Record<string, string> = {
  CRITICAL: '零覆盖',
  HIGH: '高风险',
  MEDIUM: '中风险',
  LOW: '低风险',
}

export const RISK_COLORS: Record<string, string> = {
  CRITICAL: '#f56c6c',
  HIGH: '#e6a23c',
  MEDIUM: '#409eff',
  LOW: '#67c23a',
}

export const RESULT_LABELS: Record<string, string> = {
  PASS: '通过',
  FAIL: '失败',
  BLOCKED: '阻塞',
  PENDING: '待执行',
}

export const RESULT_TAG_TYPES: Record<string, string> = {
  PASS: 'success',
  FAIL: 'danger',
  BLOCKED: 'warning',
  PENDING: 'info',
}
