import http from './http'
import type { PageVO, ProjectVO, Result } from '@/types/api'

/** 项目管理 API（P5-03）：对接 P5-02 的 /api/v1/projects 端点 */

export interface CreateProjectPayload {
  name: string
  key: string
  orgId: number
  description?: string
}

export async function listProjects(params?: {
  keyword?: string
  status?: 'ACTIVE' | 'ARCHIVED'
  orgId?: number
  page?: number
  size?: number
}): Promise<PageVO<ProjectVO>> {
  const response = await http.get('/projects', { params })
  return (response.data as PageVO<ProjectVO>) ?? { list: [], total: 0, page: 1, size: 20 }
}

export async function createProject(payload: CreateProjectPayload): Promise<ProjectVO> {
  const { data } = await http.post<Result<ProjectVO>>('/projects', payload)
  if (!data.data) {
    return Promise.reject({ code: data.code, message: 'empty project data', traceId: data.traceId })
  }
  return data.data
}

export async function updateProject(
  id: number,
  payload: { name?: string; description?: string },
): Promise<ProjectVO> {
  const { data } = await http.put<Result<ProjectVO>>(`/projects/${id}`, payload)
  if (!data.data) {
    return Promise.reject({ code: data.code, message: 'empty project data', traceId: data.traceId })
  }
  return data.data
}

export async function updateProjectStatus(
  id: number,
  status: 'ACTIVE' | 'ARCHIVED',
): Promise<ProjectVO> {
  const { data } = await http.patch<Result<ProjectVO>>(`/projects/${id}/status`, { status })
  if (!data.data) {
    return Promise.reject({ code: data.code, message: 'empty project data', traceId: data.traceId })
  }
  return data.data
}

export async function listOrgOptions(): Promise<{ id: number; name: string; code: string }[]> {
  const response = await http.get('/orgs', { params: { page: 1, size: 100 } })
  const page = response.data as PageVO<OrganizationOption>
  return page.list.map((o) => ({ id: o.id, name: o.name, code: o.code }))
}

interface OrganizationOption {
  id: number
  name: string
  code: string
}
