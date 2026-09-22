import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, getActivePinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import NotificationBell from '../NotificationBell.vue'
import NotificationList from '../NotificationList.vue'
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

const note = (overrides: Partial<NotificationVO> = {}): NotificationVO => ({
  id: 31,
  type: 'ISSUE_STATUS_CHANGED',
  title: 'Issue 状态变更为 IN_PROGRESS',
  content: 'P9NT-1 目标（由 #1 流转）',
  relatedType: 'ISSUE',
  relatedId: 1,
  isRead: false,
  readAt: null,
  createdAt: '2026-09-22T10:00:00',
  projectId: 7,
  ...overrides,
})

function makeRouter(): Router {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/system/projects/:projectId/issues', component: { template: '<div/>' } }],
  })
  router.push('/')
  return router
}

describe('NotificationBell badge（P9-09）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    mocked.fetchUnreadCount.mockResolvedValue(2)
  })

  it('初始化拉取未读数并显示 badge', async () => {
    const wrapper = mount(NotificationBell, {
      global: { plugins: [getActivePinia() ?? createPinia(), ElementPlus] },
    })
    await flushPromises()
    expect(mocked.fetchUnreadCount).toHaveBeenCalled()
    expect(wrapper.find('.el-badge__content').text()).toBe('2')
  })

  it('未读为 0 时隐藏 badge', async () => {
    mocked.fetchUnreadCount.mockResolvedValue(0)
    const wrapper = mount(NotificationBell, {
      global: { plugins: [getActivePinia() ?? createPinia(), ElementPlus] },
    })
    await flushPromises()
    expect(wrapper.find('.el-badge__content').exists()).toBe(false)
  })
})

describe('NotificationList 面板（P9-09）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    const store = useNotificationStore()
    store.reset()
  })

  function mountList() {
    const router = makeRouter()
    const wrapper = mount(NotificationList, {
      global: { plugins: [getActivePinia() ?? createPinia(), ElementPlus, router] },
    })
    return { wrapper, router }
  }

  it('挂载即拉取列表并渲染通知（类型/标题/内容）', async () => {
    mocked.listNotifications.mockResolvedValue({ list: [note()], total: 1, page: 1, size: 10 })
    const { wrapper } = mountList()
    await flushPromises()
    expect(mocked.listNotifications).toHaveBeenCalled()
    expect(wrapper.text()).toContain('通知中心')
    expect(wrapper.text()).toContain('Issue 状态变更为 IN_PROGRESS')
    expect(wrapper.text()).toContain('P9NT-1 目标（由 #1 流转）')
    expect(wrapper.find('[data-test="notification-item"]').exists()).toBe(true)
  })

  it('空列表展示暂无通知', async () => {
    mocked.listNotifications.mockResolvedValue({ list: [], total: 0, page: 1, size: 10 })
    const { wrapper } = mountList()
    await flushPromises()
    expect(wrapper.text()).toContain('暂无通知')
  })

  it('加载失败展示错误', async () => {
    mocked.listNotifications.mockRejectedValue({ code: 500, message: 'network error' })
    const { wrapper } = mountList()
    await flushPromises()
    expect(wrapper.text()).toContain('network error')
  })

  it('点击通知: 标记已读并跳转关联 Issue', async () => {
    mocked.listNotifications.mockResolvedValue({ list: [note()], total: 1, page: 1, size: 10 })
    mocked.markNotificationRead.mockResolvedValue(undefined)
    mocked.fetchUnreadCount.mockResolvedValue(0)
    const { wrapper, router } = mountList()
    await flushPromises()
    await wrapper.find('[data-test="notification-item"]').trigger('click')
    await flushPromises()
    expect(mocked.markNotificationRead).toHaveBeenCalledWith(31)
    expect(router.currentRoute.value.path).toBe('/system/projects/7/issues')
  })

  it('projectId 缺失（Issue 已删）: 仅标记已读不跳转', async () => {
    mocked.listNotifications.mockResolvedValue({
      list: [note({ projectId: null })], total: 1, page: 1, size: 10,
    })
    mocked.markNotificationRead.mockResolvedValue(undefined)
    const { wrapper, router } = mountList()
    await flushPromises()
    await wrapper.find('[data-test="notification-item"]').trigger('click')
    await flushPromises()
    expect(mocked.markNotificationRead).toHaveBeenCalledWith(31)
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('全部已读按钮调用批量接口', async () => {
    mocked.listNotifications.mockResolvedValue({ list: [note()], total: 1, page: 1, size: 10 })
    mocked.markAllRead.mockResolvedValue(1)
    const { wrapper } = mountList()
    await flushPromises()
    await wrapper.find('[data-test="mark-all"]').trigger('click')
    await flushPromises()
    expect(mocked.markAllRead).toHaveBeenCalled()
  })
})
