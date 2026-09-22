import http from './http'
import type { PageVO, Result } from '@/types/api'

/** 审计日志视图对象（后端 AuditLogVO；无敏感凭证字段） */
export interface AuditLogVO {
  id: number
  userId: number | null
  module: string
  action: string
  httpMethod: string
  uri: string
  ip: string
  target: string | null
  summary: string | null
  success: boolean
  traceId: string | null
  userAgent: string | null
  createdAt: string
}

/** 审计查询参数（audit:list，仅管理角色） */
export async function listAuditLogs(params: {
  operator?: number
  module?: string
  action?: string
  success?: boolean
  target?: string
  traceId?: string
  beginTime?: string
  endTime?: string
  page?: number
  size?: number
}): Promise<PageVO<AuditLogVO>> {
  const resp = await http.get<Result<PageVO<AuditLogVO>>>('/audit-logs', { params })
  return resp.data.data as PageVO<AuditLogVO>
}

/** 单条详情（audit:get） */
export async function getAuditLog(id: number): Promise<AuditLogVO> {
  const resp = await http.get<Result<AuditLogVO>>(`/audit-logs/${id}`)
  return resp.data.data as AuditLogVO
}
