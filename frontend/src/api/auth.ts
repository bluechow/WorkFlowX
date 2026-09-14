import http from './http'
import type { LoginRequest, LoginResponse, Result, UserVO } from '@/types/api'

/** 登录（P2-16）：真实调用 POST /api/v1/auth/login，禁止任何本地 mock */
export async function login(request: LoginRequest): Promise<LoginResponse> {
  const { data } = await http.post<Result<LoginResponse>>('/auth/login', request)
  if (!data.data) {
    return Promise.reject({ code: data.code, message: 'empty login data', traceId: data.traceId })
  }
  return data.data
}

/** 登出（P2-09）：失败由调用方兜底清理本地状态 */
export async function logout(): Promise<void> {
  await http.post<Result<void>>('/auth/logout')
}

/** 当前用户（P2-10）：认证状态的最终确认来源 */
export async function fetchMe(): Promise<UserVO> {
  const { data } = await http.get<Result<UserVO>>('/auth/me')
  if (!data.data) {
    return Promise.reject({ code: data.code, message: 'empty user data', traceId: data.traceId })
  }
  return data.data
}

/** 当前用户实时权限编码（P3-05）：供前端按钮级 UX；安全边界仍为后端 authority */
export async function fetchMyPermissions(): Promise<string[]> {
  const { data } = await http.get<Result<string[]>>('/auth/me/permissions')
  return data.data ?? []
}
