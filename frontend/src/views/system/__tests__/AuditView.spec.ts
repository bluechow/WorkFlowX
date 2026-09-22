import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, getActivePinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import AuditView from '../AuditView.vue'
import { listAuditLogs, type AuditLogVO } from '@/api/audit'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/audit', () => ({
  listAuditLogs: vi.fn(),
  getAuditLog: vi.fn(),
}))

const mocked = { listAuditLogs: vi.mocked(listAuditLogs) }

const ROW = (id: number, success = true): AuditLogVO => ({
  id,
  userId: 1,
  module: 'ISSUE',
  action: 'TRANSITION',
  httpMethod: 'PATCH',
  uri: '/api/v1/projects/1/issues/2/status',
  ip: '127.0.0.1',
  target: 'issue:2',
  summary: '状态流转 OPEN -> IN_PROGRESS',
  success,
  traceId: 'trace-' + id,
  userAgent: 'vitest',
  createdAt: '2026-09-22T10:00:00',
})

function makeRouter(): Router {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: { template: '<div/>' } }],
  })
  router.push('/')
  return router
}

const mountView = () =>
  mount(AuditView, {
    global: {
      plugins: [getActivePinia() ?? createPinia(), ElementPlus, makeRouter()],
    },
  })

describe('AuditView（P10-13）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    const store = useAuthStore()
    store.permissionCodes = ['audit:list']
    store.currentUser = { id: 1, username: 'admin', email: 'a@a', nickname: null, status: 'ACTIVE', lastLoginAt: null, createdAt: '', updatedAt: '' }
  })

  it('加载审计表格并渲染操作事实', async () => {
    mocked.listAuditLogs.mockResolvedValue({
      list: [ROW(7), ROW(8, false)], total: 2, page: 1, size: 20,
    })
    const wrapper = mountView()
    await flushPromises()
    expect(mocked.listAuditLogs).toHaveBeenCalled()
    expect(wrapper.text()).toContain('状态流转 OPEN -> IN_PROGRESS')
    expect(wrapper.text()).toContain('失败')
    expect(wrapper.text()).toContain('trace-8')
  })

  it('空数据展示暂无审计记录', async () => {
    mocked.listAuditLogs.mockResolvedValue({ list: [], total: 0, page: 1, size: 20 })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('暂无审计记录')
  })

  it('加载失败展示错误提示', async () => {
    mocked.listAuditLogs.mockRejectedValue({ code: 500, message: 'db error' })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('db error')
  })

  it('无 audit:list 权限时展示权限提示而非空白页', async () => {
    const store = useAuthStore()
    store.permissionCodes = []
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.find('[data-test="no-permission"]').exists()).toBe(true)
    expect(mocked.listAuditLogs).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('没有查看审计日志的权限')
  })

  it('筛选触发带参数查询', async () => {
    mocked.listAuditLogs.mockResolvedValue({ list: [], total: 0, page: 1, size: 20 })
    const wrapper = mountView()
    await flushPromises()
    const buttons = wrapper.findAll('button').filter((b) => b.text() === '查询')
    await buttons[0].trigger('click')
    await flushPromises()
    expect(mocked.listAuditLogs).toHaveBeenCalled()
  })
})
