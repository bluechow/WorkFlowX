import http from './http'
import type { Result } from '@/types/api'
import type { IssuePriority, IssueSeverity, IssueStatus, IssueType } from './issue'

/** 我的待办条目（后端 TodoIssueVO，Phase A-④） */
export interface TodoIssueVO {
  issueId: number
  projectId: number
  projectKey: string | null
  projectName: string | null
  issueNo: number
  title: string
  type: IssueType
  priority: IssuePriority
  severity: IssueSeverity | null
  status: IssueStatus
  updatedAt: string | null
}

/** 我的待办：跨项目聚合指派给我的未完结 Issue（上限 20） */
export async function listMyTodoIssues(): Promise<TodoIssueVO[]> {
  const { data } = await http.get<Result<TodoIssueVO[]>>('/me/todo-issues')
  return data.data ?? []
}
