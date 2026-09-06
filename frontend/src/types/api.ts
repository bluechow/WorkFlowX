/** 统一响应结构（对齐后端 Result<T> / ADR-005） */
export interface Result<T = unknown> {
  code: number
  message: string
  data?: T
  timestamp: string
  traceId: string
}

/** 统一业务错误对象（由 http.ts 拦截器归一化） */
export interface ApiError {
  code: number
  message: string
  traceId?: string
}

export interface HealthInfo {
  status: string
  service: string
  checkedAt: string
}

/** 登录请求（后端 LoginRequest） */
export interface LoginRequest {
  username: string
  password: string
}

/** 登录响应（后端 LoginResponse；最终用户资料以 /me 为准） */
export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  userId: number
  username: string
  roles: string[]
}

/** 用户状态（对齐后端 UserStatus） */
export type UserStatus = 'ACTIVE' | 'DISABLED' | 'LOCKED'

/** 用户视图对象（后端 UserVO；不含任何密码字段） */
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

/** 统一分页结构（后端 PageVO，docs/api/api-conventions.md §5） */
export interface PageVO<T> {
  list: T[]
  total: number
  page: number
  size: number
}
