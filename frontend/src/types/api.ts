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
