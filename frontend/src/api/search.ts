import http from './http'
import type { Result } from '@/types/api'

export interface ProjectHit { id: number; key: string; name: string }
export interface IssueHit { id: number; projectId: number; projectKey: string | null; issueNo: number; title: string; status: string }
export interface UserHit { id: number; username: string; nickname: string | null }

export async function globalSearch(keyword: string): Promise<{
  projects: ProjectHit[]; issues: IssueHit[]; users: UserHit[]
}> {
  const { data } = await http.get<Result<{ projects: ProjectHit[]; issues: IssueHit[]; users: UserHit[] }>>(
    '/search', { params: { keyword } })
  return data.data ?? { projects: [], issues: [], users: [] }
}
