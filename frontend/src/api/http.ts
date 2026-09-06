import axios, { AxiosError, type AxiosInstance } from 'axios'
import type { ApiError, Result } from '@/types/api'

/** 统一 API 客户端（任务 1-6.4）：baseURL 统一、Result 解包、错误归一化、traceId 透传 */
const http: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  timeout: 10_000,
})

http.interceptors.request.use((config) => {
  // 透传链路 ID；后端 TraceIdFilter 优先复用上游 X-Trace-Id
  config.headers['X-Trace-Id'] = crypto.randomUUID().replace(/-/g, '')
  return config
})

http.interceptors.response.use(
  (response) => {
    const result = response.data as Result
    if (result && typeof result.code === 'number' && result.code !== 200) {
      const error: ApiError = { code: result.code, message: result.message, traceId: result.traceId }
      return Promise.reject(error)
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
    return Promise.reject(normalized)
  },
)

export default http
