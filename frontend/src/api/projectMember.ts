import http from './http'
import type { Result } from '@/types/api'

export interface ProjectMemberVO {
  projectId: number
  userId: number
  role: 'OWNER' | 'MANAGER' | 'MEMBER'
  createdAt: string
}

export async function listProjectMembers(projectId: number): Promise<ProjectMemberVO[]> {
  const { data } = await http.get<Result<ProjectMemberVO[]>>(`/projects/${projectId}/members`)
  return data.data ?? []
}
