import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, getActivePinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import ProjectBoardView from '../ProjectBoardView.vue'
import { useAuthStore } from '@/stores/auth'
import { listIssuesForBoard, listProjectOptions, transitionIssueStatus } from '@/api/issue'

vi.mock('@/api/issue', () => ({
  listIssuesForBoard: vi.fn(),
  listProjectOptions: vi.fn(),
  transitionIssueStatus: vi.fn(),
}))

const push = vi.fn()
vi.mock('vue-router', () => ({
  createRouter: () => ({ beforeEach: vi.fn(), push: vi.fn(), currentRoute: { value: { path: '/system/projects/7/board', query: {} } } }),
  createWebHistory: () => ({}),
  useRoute: () => ({ params: { projectId: '7' } }),
  useRouter: () => ({ push }),
}))

const mocked = {
  listIssuesForBoard: vi.mocked(listIssuesForBoard),
  listProjectOptions: vi.mocked(listProjectOptions),
  transitionIssueStatus: vi.mocked(transitionIssueStatus),
}

const ISSUES: import('@/types/api').IssueVO[] = [
  { id: 1, projectId: 7, issueNo: 1, title: '登录崩溃', description: 'd', type: 'BUG', priority: 'URGENT', severity: 'S1', status: 'OPEN', reporterId: 1, assigneeId: null, createdAt: '', updatedAt: '2026-09-20T10:00:00' },
  { id: 2, projectId: 7, issueNo: 2, title: '写文档', description: null, type: 'TASK', priority: 'LOW', severity: null, status: 'RESOLVED', reporterId: 1, assigneeId: 3, createdAt: '', updatedAt: '2026-09-21T10:00:00' },
]

const PROJECTS = [
  { id: 7, orgId: 5, key: 'WFX', name: '看板演示项目', description: '', status: 'ACTIVE', ownerId: 1, createdAt: '', updatedAt: '' },
]

const mountView = () =>
  mount(ProjectBoardView, { global: { plugins: [getActivePinia() ?? createPinia(), ElementPlus] } })

describe('ProjectBoardView（Phase A-②）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    mocked.listIssuesForBoard.mockResolvedValue(ISSUES)
    mocked.listProjectOptions.mockResolvedValue(PROJECTS as never)
  })

  it('按流转顺序渲染 6 个中文状态列，卡片按状态归列', async () => {
    const store = useAuthStore()
    store.permissionCodes = ['issue:list', 'issue:transition']
    const wrapper = mountView()
    await flushPromises()
    const cols = wrapper.findAll('.board-col')
    expect(cols.length).toBe(6)
    // 列序 = 流转顺序（ADR-017）
    const titles = cols.map((c) => c.find('.board-col__title').text())
    expect(titles).toEqual(['待处理', '处理中', '已解决', '测试中', '已关闭', '重新打开'])
    // 卡片归列：OPEN 列有「登录崩溃」，RESOLVED 列有「写文档」
    expect(cols[0].text()).toContain('登录崩溃')
    expect(cols[2].text()).toContain('写文档')
    // 列头计数
    expect(cols[0].text()).toContain('1')
  })

  it('有 issue:transition 权限时卡片可拖拽', async () => {
    useAuthStore().permissionCodes = ['issue:list', 'issue:transition']
    const wrapper = mountView()
    await flushPromises()
    const card = wrapper.find('.board-card')
    expect(card.attributes('draggable')).toBe('true')
  })

  it('无 issue:transition 权限时卡片只读（不可拖拽）', async () => {
    useAuthStore().permissionCodes = ['issue:list']
    const wrapper = mountView()
    await flushPromises()
    const card = wrapper.find('.board-card')
    expect(card.attributes('draggable')).toBe('false')
  })

  it('合法流转：拖到目标列调用 transition API 并原位更新', async () => {
    const store = useAuthStore()
    store.permissionCodes = ['issue:list', 'issue:transition']
    mocked.transitionIssueStatus.mockResolvedValue({ ...ISSUES[0], status: 'IN_PROGRESS' })
    const wrapper = mountView()
    await flushPromises()

    const openCol = wrapper.findAll('.board-col')[0]
    await openCol.find('.board-card').trigger('dragstart', {
      dataTransfer: { setData: vi.fn() },
    })
    const progressCol = wrapper.findAll('.board-col')[1]
    await progressCol.trigger('drop', { preventDefault: vi.fn() })

    expect(mocked.transitionIssueStatus).toHaveBeenCalledWith(7, 1, 'OPEN', 'IN_PROGRESS')
    // 列归属更新：待处理 0 张，处理中 1 张
    await flushPromises()
    const cols = wrapper.findAll('.board-col')
    expect(cols[0].text()).not.toContain('登录崩溃')
    expect(cols[1].text()).toContain('登录崩溃')
  })

  it('非法流转：不发请求并给出警告（TESTING → OPEN）', async () => {
    const store = useAuthStore()
    store.permissionCodes = ['issue:list', 'issue:transition']
    const testing: import('@/types/api').IssueVO = { ...ISSUES[0], status: 'TESTING' }
    mocked.listIssuesForBoard.mockResolvedValue([testing])
    const wrapper = mountView()
    await flushPromises()

    const testingCol = wrapper.findAll('.board-col')[3]
    await testingCol.find('.board-card').trigger('dragstart', {
      dataTransfer: { setData: vi.fn() },
    })
    const openCol = wrapper.findAll('.board-col')[0]
    await openCol.trigger('drop', { preventDefault: vi.fn() })

    expect(mocked.transitionIssueStatus).not.toHaveBeenCalled()
  })
})
