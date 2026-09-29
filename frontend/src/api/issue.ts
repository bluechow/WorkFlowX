import http from './http'
import type { PageVO, ProjectVO, Result } from '@/types/api'

/** Issue 类型/优先级/严重程度/状态（对齐后端 V9 ENUM，Master Prompt §14） */
export type IssueType = 'BUG' | 'TASK' | 'FEATURE' | 'IMPROVEMENT'
export type IssuePriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'
export type IssueSeverity = 'S1' | 'S2' | 'S3' | 'S4'
export type IssueStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'TESTING' | 'CLOSED' | 'REOPENED'

import type { LabelVO as CanonicalLabelVO } from '@/types/api'

export type LabelVO = CanonicalLabelVO
export type { IssueVO } from '@/types/api'

/** 本模块内部沿用的 IssueVO 引用（与全局类型同源） */
type IssueVO = import('@/types/api').IssueVO

/** 工作项关联（V17） */
export interface IssueLinkVO {
  linkId: number
  otherIssueId: number
  otherIssueNo: number
  otherTitle: string
  otherType: string
  otherStatus: string
  linkType: 'RELATES' | 'BLOCKS'
  direction: 'OUTGOING' | 'INCOMING'
}



export interface CreateIssuePayload {
  title: string
  description?: string
  type: IssueType
  priority?: IssuePriority
  severity?: IssueSeverity | null
  assigneeId?: number | null
  dueDate?: string | null
  labelIds?: number[]
}

export interface UpdateIssuePayload {
  title?: string
  description?: string
  priority?: IssuePriority
  severity?: IssueSeverity | null
  assigneeId?: number | null
  dueDate?: string | null
  clearDueDate?: boolean
  labelIds?: number[]
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
    labelId?: number
    dueAfter?: string
    dueBefore?: string
    page?: number
    size?: number
  },
): Promise<PageVO<IssueVO>> {
  const response = await http.get(`/projects/${projectId}/issues`, { params })
  // P11 修复: Result 包装取 .data（原实现导致 Issue 列表恒空）
  return (response.data as { data: PageVO<IssueVO> }).data
}

/** 看板全量列表（Phase A-②）：项目内全部 Issue，按最近活动排序（上限 500） */
export async function listIssuesForBoard(projectId: number): Promise<IssueVO[]> {
  const { data } = await http.get<Result<IssueVO[]>>(`/projects/${projectId}/issues/board`)
  if (!data.data) {
    return Promise.reject({ code: data.code, message: 'empty board data', traceId: data.traceId })
  }
  return data.data
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
  const page = (response.data as { data: PageVO<ProjectVO> }).data
  return page.list
}

// ===== 标签（V17）=====

export async function listLabels(projectId: number): Promise<LabelVO[]> {
  const { data } = await http.get<Result<LabelVO[]>>(`/projects/${projectId}/labels`)
  return data.data ?? []
}

export async function createLabel(
  projectId: number,
  payload: { name: string; color?: string },
): Promise<LabelVO> {
  const { data } = await http.post<Result<LabelVO>>(`/projects/${projectId}/labels`, payload)
  if (!data.data) {
    return Promise.reject({ message: 'empty label data' })
  }
  return data.data
}

export async function updateLabel(
  projectId: number,
  labelId: number,
  payload: { name: string; color?: string },
): Promise<LabelVO> {
  const { data } = await http.put<Result<LabelVO>>(`/projects/${projectId}/labels/${labelId}`, payload)
  if (!data.data) {
    return Promise.reject({ message: 'empty label data' })
  }
  return data.data
}

export async function deleteLabel(projectId: number, labelId: number): Promise<void> {
  await http.delete<Result<void>>(`/projects/${projectId}/labels/${labelId}`)
}

// ===== 工作项关联（V17）=====

export async function listIssueLinks(projectId: number, issueId: number): Promise<IssueLinkVO[]> {
  const { data } = await http.get<Result<IssueLinkVO[]>>(`/projects/${projectId}/issues/${issueId}/links`)
  return data.data ?? []
}

export async function createIssueLink(
  projectId: number,
  issueId: number,
  payload: { targetIssueId: number; linkType: 'RELATES' | 'BLOCKS' },
): Promise<IssueLinkVO[]> {
  const { data } = await http.post<Result<IssueLinkVO[]>>(
    `/projects/${projectId}/issues/${issueId}/links`, payload)
  return data.data ?? []
}

export async function deleteIssueLink(
  projectId: number,
  issueId: number,
  linkId: number,
): Promise<void> {
  await http.delete<Result<void>>(`/projects/${projectId}/issues/${issueId}/links/${linkId}`)
}

// ===== 全局工作项（V17）=====

export interface WorkItemVO {
  id: number
  projectId: number
  projectKey: string | null
  projectName: string | null
  issueNo: number
  title: string
  type: IssueType
  priority: IssuePriority
  severity: IssueSeverity | null
  status: IssueStatus
  reporterId: number
  assigneeId: number | null
  dueDate: string | null
  updatedAt: string
}

export type WorkItemScope = 'all' | 'assigned' | 'todo' | 'created'

export async function listMyWorkItems(
  params: {
    scope?: WorkItemScope
    keyword?: string
    type?: IssueType
    priority?: IssuePriority
    status?: IssueStatus
    page?: number
    size?: number
  },
): Promise<PageVO<WorkItemVO>> {
  const { data } = await http.get<Result<PageVO<WorkItemVO>>>('/me/work-items', { params })
  if (!data.data) {
    return Promise.reject({ message: 'empty work items' })
  }
  return data.data
}
