import axios, { AxiosError, type AxiosInstance } from 'axios'
import router from '@/router'
import { clearToken, getToken } from '@/utils/token'
import type { ApiError, Result } from '@/types/api'

/**
 * 统一 API 客户端（P2-18）。
 * - 请求拦截: 自动注入 Authorization: Bearer <token>（禁止各 API 方法手动拼头）
 * - 响应拦截: Result 解包 + 错误归一化
 * - 401: 会话失效 → 清理本地 token 并回登录页（当前已在 /login 或请求本身是 login 时不跳转，防循环）
 * - 403: 已认证但无权限 → 不自动登出，交页面展示权限错误
 * - 5xx: 归一化为后端安全消息，不展示 Java 堆栈
 */
const http: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  timeout: 10_000,
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // 透传链路 ID；后端 TraceIdFilter 优先复用上游 X-Trace-Id
  config.headers['X-Trace-Id'] = crypto.randomUUID().replace(/-/g, '')
  return config
})

http.interceptors.response.use(
  (response) => {
    const result = response.data as Result
    if (result && typeof result.code === 'number') {
      // 2xx 成功（201 创建等），其余业务码归一化为错误
      if (result.code < 200 || result.code >= 300) {
        const error: ApiError = { code: result.code, message: result.message, traceId: result.traceId }
        return Promise.reject(error)
      }
    }
    return response
  },
  (error: AxiosError<Result>) => {
    const result = error.response?.data
    const normalized: ApiError = {
      code: result?.code ?? error.response?.status ?? 500,
      message: result?.message ?? 'network error',
      traceId: result?.traceId,
    }

    if (normalized.code === 401) {
      const requestUrl = error.config?.url ?? ''
      const onLoginPage = router.currentRoute.value.path === '/login'
      // login 请求自身的 401 是"凭证错误"，交给登录页展示；其余 401 视为会话失效
      if (!requestUrl.includes('/auth/login') && !onLoginPage) {
        clearToken()
        router.push({ path: '/login', query: { reason: '401' } })
      }
    }
    // 403/409/422/429/500: 不自动登出, 由页面按 normalized.message 展示
    return Promise.reject(normalized)
  },
)

export default http
