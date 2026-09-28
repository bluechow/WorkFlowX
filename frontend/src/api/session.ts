import http from './http'
import type { Result } from '@/types/api'

/** 在线会话条目（后端 SessionVO，Phase A-⑤） */
export interface SessionVO {
  userId: number
  username: string
  nickname: string | null
  roles: string[]
  /** 会话剩余有效期（秒） */
  expiresInSeconds: number | null
  /** 是否为当前登录用户自己 */
  self: boolean
}

/** 在线会话列表（ADMIN，user:status） */
export async function listSessions(): Promise<SessionVO[]> {
  const { data } = await http.get<Result<SessionVO[]>>('/auth/sessions')
  return data.data ?? []
}

/** 踢下线（删除目标用户会话，其下一个请求即 401） */
export async function kickSession(userId: number): Promise<void> {
  await http.delete<Result<void>>(`/auth/sessions/${userId}`)
}
