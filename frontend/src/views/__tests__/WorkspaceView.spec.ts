import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, getActivePinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import WorkspaceView from '@/views/WorkspaceView.vue'
import { useAuthStore } from '@/stores/auth'
import { fetchDashboardOverview } from '@/api/dashboard'
import { listMyTodoIssues } from '@/api/me'
import { fetchWorkSummary, listMyActivities, listMyProjects } from '@/api/workspace'

vi.mock('@/api/auth', () => ({
  fetchMe: vi.fn().mockResolvedValue(undefined),
  fetchMyPermissions: vi.fn().mockResolvedValue([]),
  login: vi.fn(),
  logout: vi.fn(),
}))
vi.mock('@/api/dashboard', () => ({ fetchDashboardOverview: vi.fn() }))
vi.mock('@/api/me', () => ({ listMyTodoIssues: vi.fn() }))
vi.mock('@/api/workspace', () => ({
  listMyProjects: vi.fn(),
  listMyActivities: vi.fn(),
  fetchWorkSummary: vi.fn(),
}))
vi.mock('@/components/dashboard/StatsSection.vue', () => ({
  default: { name: 'StatsSection', template: '<div class="stats-stub" />' },
}))
const push = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
  createRouter: () => ({ beforeEach: vi.fn(), push: vi.fn() }),
  createWebHistory: () => ({}),
}))

const mocked = {
  fetchDashboardOverview: vi.mocked(fetchDashboardOverview),
  listMyTodoIssues: vi.mocked(listMyTodoIssues),
  listMyProjects: vi.mocked(listMyProjects),
  listMyActivities: vi.mocked(listMyActivities),
  fetchWorkSummary: vi.mocked(fetchWorkSummary),
}

const mountIt = () =>
  mount(WorkspaceView, { global: { plugins: [getActivePinia() ?? createPinia(), ElementPlus] } })

describe('WorkspaceView（Final Edition FP-5）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    push.mockClear()
    mocked.listMyTodoIssues.mockResolvedValue([
      {
        issueId: 1, projectId: 7, projectKey: 'CAMPUS', projectName: '智慧校园',
        issueNo: 9, title: '新增校园卡余额不足提醒', type: 'FEATURE', priority: 'MEDIUM',
        severity: null, status: 'OPEN', updatedAt: '',
      },
    ])
    mocked.fetchWorkSummary.mockResolvedValue({ assigned: 5, todo: 2, created: 8 })
    mocked.listMyProjects.mockResolvedValue([
      { id: 7, key: 'CAMPUS', name: '智慧校园管理系统', status: 'ACTIVE', myRole: 'MANAGER', ownerId: 2 },
    ])
    mocked.listMyActivities.mockResolvedValue([
      {
        id: 1, projectId: 7, issueId: 1, actorId: 9, action: 'CREATE', issueNo: 9,
        summary: '创建了 CAMPUS-9 新增校园卡余额不足提醒', createdAt: '2026-09-28T10:00:00',
      },
    ])
  })

  it('ADMIN：渲染四数字卡 + 我的项目 + 待办 + 我的操作 + 统计区', async () => {
    mocked.fetchDashboardOverview.mockResolvedValue({
      projects: { total: 4, active: 4, archived: 0 },
      issues: { total: 48, byStatus: { OPEN: 10 }, byType: {}, byPriority: {}, bySeverity: {}, bugCount: 21 },
      createdTrend: [],
    })
    const store = useAuthStore()
    store.currentUser = {
      id: 9, username: 'demo_admin', email: 'a@a', nickname: '演示管理员',
      status: 'ACTIVE', lastLoginAt: null, createdAt: '', updatedAt: '',
    }
    store.permissionCodes = ['dashboard:view', 'issue:list', 'project:list']
    const wrapper = mountIt()
    await flushPromises()
    // 四数字卡
    expect(wrapper.text()).toContain('待我处理')
    expect(wrapper.text()).toContain('我的工作项')
    expect(wrapper.text()).toContain('我创建的')
    expect(wrapper.text()).toContain('我加入的项目')
    // 项目/待办/动态
    expect(wrapper.text()).toContain('智慧校园管理系统')
    expect(wrapper.text()).toContain('CAMPUS-9')
    expect(wrapper.text()).toContain('创建了 CAMPUS-9')
    // 统计区（dashboard:view）
    expect(wrapper.find('.stats-stub').exists()).toBe(true)
  })

  it('MEMBER（无 dashboard:view）：不加载统计，仅个人聚合', async () => {
    mocked.listMyTodoIssues.mockResolvedValue([]) // MEMBER 视角无待办
    const store = useAuthStore()
    store.currentUser = {
      id: 9, username: 'u1', email: 'a@a', nickname: null,
      status: 'ACTIVE', lastLoginAt: null, createdAt: '', updatedAt: '',
    }
    store.permissionCodes = []
    const wrapper = mountIt()
    await flushPromises()
    expect(mocked.fetchDashboardOverview).not.toHaveBeenCalled()
    expect(wrapper.find('.stats-stub').exists()).toBe(false)
    expect(wrapper.text()).toContain('太棒了，没有待办 🎉')
  })
})
