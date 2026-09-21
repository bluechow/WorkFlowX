import http from './http'
import type { PageVO, ProjectVO, Result } from '@/types/api'

/** Issue 类型/优先级/严重程度/状态（对齐后端 V9 ENUM，Master Prompt §14） */
export type IssueType = 'BUG' | 'TASK' | 'FEATURE' | 'IMPROVEMENT'
export type IssuePriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'
export type IssueSeverity = 'S1' | 'S2' | 'S3' | 'S4'
export type IssueStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'TESTING' | 'CLOSED' | 'REOPENED'

/** Issue 视图对象（后端 IssueVO，P6-02） */
export interface IssueVO {
  id: number
  projectId: number
  issueNo: number
  title: string
  description: string | null
  type: IssueType
  priority: IssuePriority
  severity: IssueSeverity | null
  status: IssueStatus
  reporterId: number
  assigneeId: number | null
  createdAt: string
  updatedAt: string
}

export interface CreateIssuePayload {
  title: string
  description?: string
  type: IssueType
  priority?: IssuePriority
  severity?: IssueSeverity | null
  assigneeId?: number | null
}

export interface UpdateIssuePayload {
  title?: string
  description?: string
  priority?: IssuePriority
  severity?: IssueSeverity | null
  assigneeId?: number | null
}

export async function listIssues(
  projectId: number,
  params?: {
    keyword?: string
    issueNo?: number
    type?: IssueType
    priority?: IssuePriority
    severity?: IssueSeverity
    status?: IssueStatus
    reporterId?: number
    assigneeId?: number
    page?: number
    size?: number
  },
): Promise<PageVO<IssueVO>> {
  const response = await http.get(`/projects/${projectId}/issues`, { params })
  return (response.data as PageVO<IssueVO>) ?? { list: [], total: 0, page: 1, size: 20 }
}

export async function getIssue(projectId: number, issueId: number): Promise<IssueVO> {
  const { data } = await http.get<Result<IssueVO>>(`/projects/${projectId}/issues/${issueId}`)
  if (!data.data) {
    return Promise.reject({ code: data.code, message: 'empty issue data', traceId: data.traceId })
  }
  return data.data
}

export async function createIssue(projectId: number, payload: CreateIssuePayload): Promise<IssueVO> {
  const { data } = await http.post<Result<IssueVO>>(`/projects/${projectId}/issues`, payload)
  if (!data.data) {
    return Promise.reject({ code: data.code, message: 'empty issue data', traceId: data.traceId })
  }
  return data.data
}

export async function updateIssue(
  projectId: number,
  issueId: number,
  payload: UpdateIssuePayload,
): Promise<IssueVO> {
  const { data } = await http.put<Result<IssueVO>>(`/projects/${projectId}/issues/${issueId}`, payload)
  if (!data.data) {
    return Promise.reject({ code: data.code, message: 'empty issue data', traceId: data.traceId })
  }
  return data.data
}

/**
 * 状态流转（P7-04，ADR-017）：fromStatus 必传做乐观并发（条件 UPDATE）。
 * 合法目标矩阵与后端 VALID_TRANSITIONS 镜像（前端仅 UX；安全边界在后端矩阵校验）。
 */
export const VALID_TRANSITIONS: Record<IssueStatus, IssueStatus[]> = {
  OPEN: ['IN_PROGRESS'],
  IN_PROGRESS: ['RESOLVED'],
  RESOLVED: ['TESTING'],
  TESTING: ['CLOSED', 'REOPENED'],
  REOPENED: ['IN_PROGRESS'],
  CLOSED: [],
}

export function allowedTargets(from: IssueStatus): IssueStatus[] {
  return VALID_TRANSITIONS[from] ?? []
}

export async function transitionIssueStatus(
  projectId: number,
  issueId: number,
  fromStatus: IssueStatus,
  toStatus: IssueStatus,
): Promise<IssueVO> {
  const { data } = await http.patch<Result<IssueVO>>(`/projects/${projectId}/issues/${issueId}/status`, {
    fromStatus,
    toStatus,
  })
  if (!data.data) {
    return Promise.reject({ code: data.code, message: 'empty issue data', traceId: data.traceId })
  }
  return data.data
}

export async function assignIssue(
  projectId: number,
  issueId: number,
  assigneeId: number | null,
): Promise<IssueVO> {
  const { data } = await http.patch<Result<IssueVO>>(`/projects/${projectId}/issues/${issueId}/assignee`, {
    assigneeId,
  })
  if (!data.data) {
    return Promise.reject({ code: data.code, message: 'empty issue data', traceId: data.traceId })
  }
  return data.data
}

/** 项目选项（IssuesView 顶部项目切换用，复用 orgs 权限无需 project:list 之外的能力） */
export async function listProjectOptions(): Promise<ProjectVO[]> {
  const response = await http.get('/projects', { params: { page: 1, size: 100 } })
  const page = response.data as PageVO<ProjectVO>
  return page.list
}
