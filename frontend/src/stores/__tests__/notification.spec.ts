import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import {
  fetchUnreadCount,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  type NotificationVO,
} from '@/api/notification'
import { useNotificationStore } from '@/stores/notification'

vi.mock('@/api/notification', () => ({
  fetchUnreadCount: vi.fn(),
  listNotifications: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}))

const mocked = {
  fetchUnreadCount: vi.mocked(fetchUnreadCount),
  listNotifications: vi.mocked(listNotifications),
  markNotificationRead: vi.mocked(markNotificationRead),
  markAllRead: vi.mocked(markAllNotificationsRead),
}

const NOTE = (id: number, isRead = false): NotificationVO => ({
  id,
  type: 'ISSUE_ASSIGNED',
  title: 'Issue 已分派给您',
  content: 'P9NT-1 目标（由 #1 分派）',
  relatedType: 'ISSUE',
  relatedId: 1,
  isRead,
  readAt: null,
  createdAt: '2026-09-22T10:00:00',
  projectId: 7,
})

describe('notification store（P9）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('refreshUnreadCount 拉取未读数', async () => {
    const store = useNotificationStore()
    mocked.fetchUnreadCount.mockResolvedValue(3)
    await store.refreshUnreadCount()
    expect(store.unreadCount).toBe(3)
  })

  it('unread 拉取失败静默归零（UX 数据不阻塞）', async () => {
    const store = useNotificationStore()
    mocked.fetchUnreadCount.mockRejectedValue({ code: 500, message: 'boom' })
    await store.refreshUnreadCount()
    expect(store.unreadCount).toBe(0)
  })

  it('fetchList 填充列表与 total', async () => {
    const store = useNotificationStore()
    mocked.listNotifications.mockResolvedValue({
      list: [NOTE(1), NOTE(2, true)], total: 2, page: 1, size: 10,
    })
    await store.fetchList(true)
    expect(store.notifications).toHaveLength(2)
    expect(store.total).toBe(2)
    expect(store.listStatus).toBe('success')
  })

  it('markRead 更新本地状态并对齐服务端未读数', async () => {
    const store = useNotificationStore()
    store.notifications = [NOTE(11)]
    store.unreadCount = 1
    mocked.markNotificationRead.mockResolvedValue(undefined)
    mocked.fetchUnreadCount.mockResolvedValue(0)
    await store.markRead(11)
    expect(store.notifications[0].isRead).toBe(true)
    expect(store.unreadCount).toBe(0)
  })

  it('markAllRead 全部置为已读', async () => {
    const store = useNotificationStore()
    store.notifications = [NOTE(1), NOTE(2)]
    mocked.markAllRead.mockResolvedValue(2)
    mocked.fetchUnreadCount.mockResolvedValue(0)
    await store.markAllRead()
    expect(store.notifications.every((n) => n.isRead)).toBe(true)
    expect(store.unreadCount).toBe(0)
  })

  it('loadMore 追加下一页（reset=false）', async () => {
    const store = useNotificationStore()
    mocked.listNotifications.mockResolvedValueOnce({
      list: [NOTE(1)], total: 2, page: 1, size: 10,
    })
    await store.fetchList(true)
    mocked.listNotifications.mockResolvedValueOnce({
      list: [NOTE(2)], total: 2, page: 2, size: 10,
    })
    await store.loadMore()
    expect(store.notifications).toHaveLength(2)
    expect(mocked.listNotifications).toHaveBeenLastCalledWith(undefined, 2, 10, undefined)
  })

  it('reset 清理全部状态（登出/切号防残留）', async () => {
    const store = useNotificationStore()
    store.unreadCount = 5
    store.notifications = [NOTE(1)]
    store.total = 1
    store.listStatus = 'failed'
    store.listError = 'x'
    store.reset()
    expect(store.unreadCount).toBe(0)
    expect(store.notifications).toHaveLength(0)
    expect(store.total).toBe(0)
    expect(store.listStatus).toBe('idle')
    expect(store.listError).toBe('')
  })
})
