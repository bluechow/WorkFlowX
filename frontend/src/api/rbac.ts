import http from './http'
import type { PermissionVO, Result, RoleVO } from '@/types/api'

/** RBAC 管理 API（P3-04）：对接 P3-03 的 /api/v1/roles、/permissions、/users/{id}/roles 端点 */

export async function listRoles(): Promise<RoleVO[]> {
  const { data } = await http.get<Result<RoleVO[]>>('/roles')
  return data.data ?? []
}

export async function createRole(payload: { code: string; name: string; description?: string }): Promise<RoleVO> {
  const { data } = await http.post<Result<RoleVO>>('/roles', payload)
  if (!data.data) {
    return Promise.reject({ code: data.code, message: 'empty role data', traceId: data.traceId })
  }
  return data.data
}

export async function updateRole(
  id: number,
  payload: { name?: string; description?: string },
): Promise<RoleVO> {
  const { data } = await http.put<Result<RoleVO>>(`/roles/${id}`, payload)
  if (!data.data) {
    return Promise.reject({ code: data.code, message: 'empty role data', traceId: data.traceId })
  }
  return data.data
}

export async function deleteRole(id: number): Promise<void> {
  await http.delete(`/roles/${id}`)
}

export async function getRolePermissions(id: number): Promise<string[]> {
  const { data } = await http.get<Result<string[]>>(`/roles/${id}/permissions`)
  return data.data ?? []
}

export async function assignRolePermissions(id: number, permissionCodes: string[]): Promise<string[]> {
  const { data } = await http.put<Result<string[]>>(`/roles/${id}/permissions`, { permissionCodes })
  return data.data ?? []
}

export async function listPermissions(): Promise<PermissionVO[]> {
  const { data } = await http.get<Result<PermissionVO[]>>('/permissions')
  return data.data ?? []
}

export async function getUserRoles(userId: number): Promise<string[]> {
  const { data } = await http.get<Result<string[]>>(`/users/${userId}/roles`)
  return data.data ?? []
}

export async function replaceUserRoles(userId: number, roleCodes: string[]): Promise<string[]> {
  const { data } = await http.put<Result<string[]>>(`/users/${userId}/roles`, { roleCodes })
  return data.data ?? []
}
