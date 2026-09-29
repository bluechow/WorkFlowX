import http from './http'
import type { PageVO, Result } from '@/types/api'

/** 通知类型（对齐后端 NotificationType，ADR-019 最小集） */
export type NotificationType = 'ISSUE_ASSIGNED' | 'ISSUE_STATUS_CHANGED' | 'ISSUE_COMMENTED'

/** 通知视图对象（后端 NotificationVO；projectId 供跳转，Issue 已删时为 null） */
export interface NotificationVO {
  id: number
  type: NotificationType
  title: string
  content: string | null
  relatedType: string | null
  relatedId: number | null
  isRead: boolean
  readAt: string | null
  createdAt: string
  projectId: number | null
}

/** 我的通知列表（read 筛选 + 分页，created_at DESC） */
export async function listNotifications(
  read?: boolean,
  page = 1,
  size = 20,
  type?: string,
): Promise<PageVO<NotificationVO>> {
  const resp = await http.get<Result<PageVO<NotificationVO>>>('/notifications', {
    params: { read, page, size, type },
  })
  return resp.data.data as PageVO<NotificationVO>
}

/** 未读数量 */
export async function fetchUnreadCount(): Promise<number> {
  const resp = await http.get<Result<{ count: number }>>('/notifications/unread-count')
  return (resp.data.data as { count: number }).count
}

/** 标记单条已读（幂等） */
export async function markNotificationRead(id: number): Promise<void> {
  await http.patch<Result<null>>(`/notifications/${id}/read`)
}

/** 全部已读（仅当前用户） */
export async function markAllNotificationsRead(): Promise<number> {
  const resp = await http.patch<Result<{ updated: number }>>('/notifications/read-all')
  return (resp.data.data as { updated: number }).updated
}
