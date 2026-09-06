import http from './http'
import type { HealthInfo, Result } from '@/types/api'

/** 健康检查接口（Phase 1 冒烟链路：前端 → /api/v1/health → 后端） */
export async function fetchHealth(): Promise<HealthInfo> {
  const { data } = await http.get<Result<HealthInfo>>('/health')
  if (!data.data) {
    return Promise.reject({ code: data.code, message: 'empty health data', traceId: data.traceId })
  }
  return data.data
}
