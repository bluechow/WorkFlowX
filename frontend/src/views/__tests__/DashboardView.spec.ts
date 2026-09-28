import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, getActivePinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import DashboardView from '../DashboardView.vue'
import { useAuthStore } from '@/stores/auth'
import { fetchDashboardOverview } from '@/api/dashboard'
import { listMyTodoIssues } from '@/api/me'

const push = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
  createRouter: () => ({
    beforeEach: vi.fn(),
    push: vi.fn(),
    currentRoute: { value: { params: {}, path: '/dashboard', query: {} } },
  }),
  createWebHistory: () => ({}),
}))
vi.mock('@/api/auth', () => ({
  fetchMe: vi.fn().mockResolvedValue(undefined),
  fetchMyPermissions: vi.fn().mockResolvedValue([]),
  login: vi.fn(),
  logout: vi.fn(),
}))
vi.mock('@/api/dashboard', () => ({
  fetchDashboardOverview: vi.fn(),
}))
vi.mock('@/api/me', () => ({
  listMyTodoIssues: vi.fn(),
}))
vi.mock('@/components/dashboard/StatsSection.vue', () => ({
  default: { name: 'StatsSection', template: '<div class="stats-stub" />' },
}))

const mocked = {
  fetchDashboardOverview: vi.mocked(fetchDashboardOverview),
  listMyTodoIssues: vi.mocked(listMyTodoIssues),
}

const TOKEN = 'dashboard-test-token'
const CURRENT_USER = {
  id: 7, username: 'alice', email: 'alice@test.local', nickname: 'Alice',
  status: 'ACTIVE', lastLoginAt: '', createdAt: '', updatedAt: '',
} as const

const OVERVIEW = {
  projects: { total: 4, active: 4, archived: 0 },
  issues: {
    total: 48,
    byStatus: { OPEN: 10, CLOSED: 8 },
    byType: {}, byPriority: {}, bySeverity: {},
    bugCount: 21,
  },
  createdTrend: [{ date: '2026-09-20', created: 3 }],
}

function mountView() {
  return mount(DashboardView, {
    global: { plugins: [getActivePinia() ?? createPinia(), ElementPlus] },
  })
}

describe('DashboardView（Phase A-⑥）', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
    setActivePinia(createPinia())
    push.mockClear()
    mocked.listMyTodoIssues.mockResolvedValue([
      {
        issueId: 1, projectId: 7, projectKey: 'CAMPUS', projectName: '智慧校园管理系统',
        issueNo: 9, title: '新增校园卡余额不足提醒', type: 'FEATURE', priority: 'MEDIUM',
        severity: null, status: 'OPEN', updatedAt: '',
      },
    ])
  })

  it('ADMIN：自动加载统计并渲染 StatsSection + 待办卡（中文标签）', async () => {
    mocked.fetchDashboardOverview.mockResolvedValue(OVERVIEW)
    const store = useAuthStore()
    store.accessToken = TOKEN
    store.username = 'alice'
    store.roles = ['ADMIN']
    store.permissionCodes = ['dashboard:view']
    store.currentUser = { ...CURRENT_USER }
    const wrapper = mountView()
    await flushPromises()
    // 自动加载（无需点击按钮）
    expect(mocked.fetchDashboardOverview).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.stats-stub').exists()).toBe(true)
    expect(wrapper.text()).toContain('，Alice') // 时段问候随测试时间变化，断言昵称部分
    expect(wrapper.text()).toContain('我的待办（1）')
    expect(wrapper.text()).toContain('CAMPUS-9')
    expect(wrapper.text()).toContain('中')
  })

  it('MEMBER：无 dashboard:view 时不加载统计，显示快捷入口 + 待办', async () => {
    const store = useAuthStore()
    store.accessToken = TOKEN
    store.username = 'alice'
    store.roles = ['MEMBER']
    store.permissionCodes = []
    store.currentUser = { ...CURRENT_USER }
    const wrapper = mountView()
    await flushPromises()
    expect(mocked.fetchDashboardOverview).not.toHaveBeenCalled()
    expect(wrapper.find('.stats-stub').exists()).toBe(false)
    expect(wrapper.text()).toContain('快捷入口')
    expect(wrapper.text()).toContain('我的待办与个人中心')
  })

  it('页面不显示 token/密码等敏感信息', async () => {
    const store = useAuthStore()
    store.accessToken = TOKEN
    store.username = 'alice'
    store.roles = ['MEMBER']
    store.currentUser = { ...CURRENT_USER }
    const wrapper = mountView()
    await flushPromises()
    const text = wrapper.text()
    expect(text).not.toContain(TOKEN)
    expect(text).not.toContain('Bearer')
  })
})
