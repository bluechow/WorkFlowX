import http from './http'
import type { PageVO, Result } from '@/types/api'

/** 用户状态（对齐后端 UserStatus） */
export type UserStatus = 'ACTIVE' | 'DISABLED' | 'LOCKED'

/** 用户视图对象（后端 UserVO，无敏感字段） */
export interface UserVO {
  id: number
  username: string
  email: string
  nickname: string | null
  status: UserStatus
  lastLoginAt: string | null
  createdAt: string
  updatedAt: string
}

/** 用户列表（ADMIN，user:list）：keyword 匹配 username/email/nickname，可按状态筛选 */
export async function listUsers(
  keyword?: string,
  status?: UserStatus,
  page = 1,
  size = 20,
): Promise<PageVO<UserVO>> {
  const resp = await http.get<Result<PageVO<UserVO>>>('/users', {
    params: { keyword, status, page, size },
  })
  return resp.data.data as PageVO<UserVO>
}

/** 创建用户（user:create） */
export async function createUser(payload: {
  username: string
  email: string
  password: string
  nickname?: string
}): Promise<UserVO> {
  const resp = await http.post<Result<UserVO>>('/users', payload)
  return resp.data.data as UserVO
}

/** 更新用户（user:update；username 不可变；email 后端必填） */
export async function updateUser(
  id: number,
  payload: { email: string; nickname?: string },
): Promise<UserVO> {
  const resp = await http.put<Result<UserVO>>(`/users/${id}`, payload)
  return resp.data.data as UserVO
}

/** 变更状态（user:status）；DISABLED 即踢线，恢复需该用户重新登录 */
export async function updateUserStatus(id: number, status: UserStatus): Promise<UserVO> {
  const resp = await http.patch<Result<UserVO>>(`/users/${id}/status`, { status })
  return resp.data.data as UserVO
}
