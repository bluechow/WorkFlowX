import { defineStore } from 'pinia'
import {
  fetchUnreadCount,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  type NotificationVO,
} from '@/api/notification'

/**
 * 通知 store（P9-09）。
 * 未读数策略（ADR-019）: 页面初始化 + 用户操作后刷新（不做高频轮询，无 WebSocket）。
 * 登出/切号必须 reset——防止残留上一用户的未读数与列表。
 */
export const useNotificationStore = defineStore('notification', {
  state: () => ({
    unreadCount: 0,
    notifications: [] as NotificationVO[],
    total: 0,
    page: 1,
    size: 10,
    readFilter: undefined as undefined | boolean,
    /** 类型筛选（FP-7「@我的」；undefined=全部） */
    typeFilter: undefined as undefined | string,
    listStatus: 'idle' as 'idle' | 'loading' | 'success' | 'failed',
    listError: '' ,
  }),
  actions: {
    /** 拉取未读数（登录后/操作后调用）；失败静默为 0（UX 数据，不阻塞主流程） */
    async refreshUnreadCount(): Promise<void> {
      try {
        this.unreadCount = await fetchUnreadCount()
      } catch {
        this.unreadCount = 0
      }
    },
    async fetchList(reset = false): Promise<void> {
      if (reset) {
        this.page = 1
        this.notifications = []
      }
      this.listStatus = 'loading'
      this.listError = ''
      try {
        const data = await listNotifications(this.readFilter, this.page, this.size, this.typeFilter)
        this.notifications = reset ? data.list : [...this.notifications, ...data.list]
        this.total = data.total
        this.listStatus = 'success'
      } catch (e) {
        this.listStatus = 'failed'
        this.listError = (e as { message?: string }).message ?? '通知加载失败'
      }
    },
    async markRead(id: number): Promise<void> {
      await markNotificationRead(id)
      const target = this.notifications.find((n) => n.id === id)
      if (target && !target.isRead) {
        target.isRead = true
        target.readAt = new Date().toISOString()
        this.unreadCount = Math.max(0, this.unreadCount - 1)
      }
      // 与服务端真实状态再对齐（未读数以数据库为准）
      await this.refreshUnreadCount()
    },
    async markAllRead(): Promise<void> {
      await markAllNotificationsRead()
      this.notifications.forEach((n) => {
        n.isRead = true
        n.readAt = n.readAt ?? new Date().toISOString()
      })
      await this.refreshUnreadCount()
    },
    async setReadFilter(read: boolean | undefined): Promise<void> {
      this.readFilter = read
      await this.fetchList(true)
    },
    async loadMore(): Promise<void> {
      if (this.notifications.length < this.total) {
        this.page += 1
        await this.fetchList(false)
      }
    },
    /** 登出/切换用户: 无条件清理本 store 状态（防止跨用户残留） */
    reset(): void {
      this.unreadCount = 0
      this.notifications = []
      this.total = 0
      this.page = 1
      this.readFilter = undefined
      this.listStatus = 'idle'
      this.listError = ''
    },
  },
})
