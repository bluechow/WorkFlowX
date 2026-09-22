import http from './http'
import type {
  DepartmentVO,
  OrganizationMemberVO,
  OrganizationVO,
  PageVO,
  Result,
} from '@/types/api'

/** 组织架构 API（P4-03）：对接 P4-02 的 /api/v1/orgs、/departments 端点 */

export async function listOrgs(params?: {
  keyword?: string
  page?: number
  size?: number
}): Promise<PageVO<OrganizationVO>> {
  const response = await http.get('/orgs', { params })
  // P11 修复: http 拦截器返回 axios response，Result 包装需取 .data（原实现取 .data 当 PageVO 导致列表恒空）
  return (response.data as { data: PageVO<OrganizationVO> }).data
}

export async function createOrg(payload: {
  name: string
  code: string
  description?: string
}): Promise<OrganizationVO> {
  const { data } = await http.post<Result<OrganizationVO>>('/orgs', payload)
  if (!data.data) {
    return Promise.reject({ code: data.code, message: 'empty org data', traceId: data.traceId })
  }
  return data.data
}

export async function updateOrg(
  id: number,
  payload: { name?: string; description?: string },
): Promise<OrganizationVO> {
  const { data } = await http.put<Result<OrganizationVO>>(`/orgs/${id}`, payload)
  if (!data.data) {
    return Promise.reject({ code: data.code, message: 'empty org data', traceId: data.traceId })
  }
  return data.data
}

export async function deleteOrg(id: number): Promise<void> {
  await http.delete(`/orgs/${id}`)
}

export async function getOrg(id: number): Promise<OrganizationVO> {
  const { data } = await http.get<Result<OrganizationVO>>(`/orgs/${id}`)
  if (!data.data) {
    return Promise.reject({ code: data.code, message: 'empty org data', traceId: data.traceId })
  }
  return data.data
}

export async function listOrgMembers(orgId: number): Promise<OrganizationMemberVO[]> {
  const { data } = await http.get<Result<OrganizationMemberVO[]>>(`/orgs/${orgId}/members`)
  return data.data ?? []
}

export async function addOrgMember(
  orgId: number,
  payload: { userId: number; role?: 'ADMIN' | 'MEMBER'; departmentId?: number },
): Promise<OrganizationMemberVO> {
  const { data } = await http.post<Result<OrganizationMemberVO>>(`/orgs/${orgId}/members`, payload)
  if (!data.data) {
    return Promise.reject({ code: data.code, message: 'empty member data', traceId: data.traceId })
  }
  return data.data
}

export async function removeOrgMember(orgId: number, userId: number): Promise<void> {
  await http.delete(`/orgs/${orgId}/members/${userId}`)
}

export async function listDepartments(orgId: number): Promise<DepartmentVO[]> {
  const { data } = await http.get<Result<DepartmentVO[]>>(`/orgs/${orgId}/departments`)
  return data.data ?? []
}

export async function createDepartment(
  orgId: number,
  payload: { name: string; code: string; description?: string; parentId?: number | null },
): Promise<DepartmentVO> {
  const { data } = await http.post<Result<DepartmentVO>>(`/orgs/${orgId}/departments`, payload)
  if (!data.data) {
    return Promise.reject({ code: data.code, message: 'empty department data', traceId: data.traceId })
  }
  return data.data
}

export async function updateDepartment(
  id: number,
  payload: { name?: string; parentId?: number | null },
): Promise<DepartmentVO> {
  const { data } = await http.patch<Result<DepartmentVO>>(`/departments/${id}`, payload)
  if (!data.data) {
    return Promise.reject({ code: data.code, message: 'empty department data', traceId: data.traceId })
  }
  return data.data
}

export async function deleteDepartment(id: number): Promise<void> {
  await http.delete(`/departments/${id}`)
}
