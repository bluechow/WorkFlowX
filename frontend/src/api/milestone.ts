import http from './http'
import type { Result } from '@/types/api'

/** 里程碑（V19） */
export interface MilestoneVO {
  id: number
  projectId: number
  name: string
  description: string | null
  dueDate: string | null
  status: 'OPEN' | 'DONE'
  createdAt: string
  totalIssues: number
  doneIssues: number
}

export async function listMilestones(projectId: number): Promise<MilestoneVO[]> {
  const { data } = await http.get<Result<MilestoneVO[]>>(`/projects/${projectId}/milestones`)
  return data.data ?? []
}

export async function createMilestone(
  projectId: number,
  payload: { name: string; description?: string | null; dueDate?: string | null },
): Promise<MilestoneVO> {
  const { data } = await http.post<Result<MilestoneVO>>(`/projects/${projectId}/milestones`, payload)
  if (!data.data) return Promise.reject({ message: 'empty milestone' })
  return data.data
}

export async function updateMilestone(
  projectId: number,
  milestoneId: number,
  payload: { name: string; description?: string | null; dueDate?: string | null; status?: 'OPEN' | 'DONE' },
): Promise<MilestoneVO> {
  const { data } = await http.put<Result<MilestoneVO>>(
    `/projects/${projectId}/milestones/${milestoneId}`, payload)
  if (!data.data) return Promise.reject({ message: 'empty milestone' })
  return data.data
}

export async function deleteMilestone(projectId: number, milestoneId: number): Promise<void> {
  await http.delete<Result<void>>(`/projects/${projectId}/milestones/${milestoneId}`)
}
