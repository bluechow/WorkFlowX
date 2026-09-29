import http from './http'
import type { Result } from '@/types/api'
import type { ActivityVO } from '@/api/activity'

/** 我加入的项目（成员视角；FP-5） */
export interface MyProjectVO {
  id: number
  key: string
  name: string
  status: string
  myRole: string
  ownerId: number
}

export async function listMyProjects(): Promise<MyProjectVO[]> {
  const { data } = await http.get<Result<MyProjectVO[]>>('/me/projects')
  return data.data ?? []
}

export async function listMyActivities(limit = 10): Promise<ActivityVO[]> {
  const { data } = await http.get<Result<ActivityVO[]>>('/me/activities', { params: { limit } })
  return data.data ?? []
}

export interface WorkSummaryVO {
  assigned: number
  todo: number
  created: number
}

export async function fetchWorkSummary(): Promise<WorkSummaryVO> {
  const { data } = await http.get<Result<WorkSummaryVO>>('/me/work-summary')
  return data.data ?? { assigned: 0, todo: 0, created: 0 }
}
