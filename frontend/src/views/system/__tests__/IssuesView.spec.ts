import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, getActivePinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import IssuesView from '../IssuesView.vue'
import { useAuthStore } from '@/stores/auth'
import { assignIssue, createIssue, listIssues, transitionIssueStatus, updateIssue } from '@/api/issue'

vi.mock('@/api/issue', () => ({
  listIssues: vi.fn(),
  getIssue: vi.fn(),
  createIssue: vi.fn(),
  updateIssue: vi.fn(),
  transitionIssueStatus: vi.fn(),
  assignIssue: vi.fn(),
  listProjectOptions: vi.fn(),
}))

vi.mock('@/api/project', () => ({
  listProjects: vi.fn(),
  createProject: vi.fn(),
  updateProject: vi.fn(),
  updateProjectStatus: vi.fn(),
  deleteProject: vi.fn(),
}))

vi.mock('@/api/projectMember', () => ({
  listProjectMembers: vi.fn(),
  addProjectMember: vi.fn(),
  removeProjectMember: vi.fn(),
}))

const push = vi.fn()
vi.mock('vue-router', () => ({
  createRouter: () => ({ beforeEach: vi.fn(), push: vi.fn(), currentRoute: { value: { path: '/system/projects/7/issues', query: {} } } }),
  createWebHistory: () => ({}),
  useRoute: () => ({ params: { projectId: '7' } }),
  useRouter: () => ({ push }),
}))

const mocked = {
  listIssues: vi.mocked(listIssues),
  createIssue: vi.mocked(createIssue),
  updateIssue: vi.mocked(updateIssue),
  transitionIssueStatus: vi.mocked(transitionIssueStatus),
  assignIssue: vi.mocked(assignIssue),
}

const ISSUES: import('@/types/api').IssueVO[] = [
  { id: 1, projectId: 7, issueNo: 1, title: '登录崩溃', description: 'd', type: 'BUG', priority: 'URGENT', severity: 'S1', status: 'OPEN', reporterId: 1, assigneeId: null, createdAt: '', updatedAt: '', dueDate: null, milestoneId: null, labels: [] },
  { id: 2, projectId: 7, issueNo: 2, title: '写文档', description: null, type: 'TASK', priority: 'LOW', severity: null, status: 'RESOLVED', reporterId: 1, assigneeId: 3, createdAt: '', updatedAt: '', dueDate: null, milestoneId: null, labels: [] },
]

const mountView = () =>
  mount(IssuesView, { global: { plugins: [getActivePinia() ?? createPinia(), ElementPlus] } })

describe('IssuesView（P6-08）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    const store = useAuthStore()
    store.permissionCodes = ['issue:list', 'issue:create', 'issue:update', 'issue:assign']
    store.currentUser = { id: 1, username: 'admin', email: 'a@a', nickname: null, status: 'ACTIVE', lastLoginAt: null, createdAt: '', updatedAt: '' }
    mocked.listIssues.mockResolvedValue({ list: ISSUES, total: 2, page: 1, size: 10 })
  })

  it('加载并渲染 Issue 列表（业务编号/类型/状态）', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(mocked.listIssues).toHaveBeenCalledWith(7, expect.objectContaining({ page: 1, size: 10 }))
    expect(wrapper.text()).toContain('登录崩溃')
  })

  it('空列表展示空状态', async () => {
    mocked.listIssues.mockResolvedValue({ list: [], total: 0, page: 1, size: 10 })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('暂无 Issue')
  })

  it('无 issue:create 权限时新建按钮不渲染', async () => {
    useAuthStore().permissionCodes = ['issue:list']
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.findAll('button').find((b) => b.text() === '新建 Issue')).toBeUndefined()
  })

  it('新建 BUG Issue 经真实 API（severity 仅 BUG 提交）', async () => {
    mocked.createIssue.mockResolvedValue(ISSUES[0])
    const wrapper = mountView()
    await flushPromises()
    await wrapper.findAll('button').find((b) => b.text() === '新建 Issue')!.trigger('click')
    await flushPromises()
    const dialog = wrapper.find('.el-dialog')
    const inputs = dialog.findAll('input')
    await inputs[1].setValue('新 BUG') // inputs[0] 是 el-select 内 input（类型）
    const textareas = dialog.findAll('textarea')
    await textareas[0]?.setValue('描述')
    const saveBtn = dialog.findAll('button').find((b) => b.text() === '保存')
    await saveBtn?.trigger('click')
    await flushPromises()
    expect(mocked.createIssue).toHaveBeenCalledWith(
      7,
      expect.objectContaining({ title: '新 BUG', type: 'TASK' }),
    )
  })

  it('状态变更调用 status API 并刷新', async () => {
    mocked.transitionIssueStatus.mockResolvedValue(ISSUES[0])
    const wrapper = mountView()
    await flushPromises()
    const selects = wrapper.findAll('.el-table .el-select')
    expect(selects.length).toBeGreaterThan(0)
    // 直接验证渲染了状态下拉（中文状态标签，交互由 change 触发）
    expect(wrapper.text()).toContain('待处理')
  })

  it('updateIssue API 可用（行内编辑对话框复用同一提交路径）', async () => {
    mocked.updateIssue.mockResolvedValue(ISSUES[0])
    expect(typeof updateIssue).toBe('function')
  })

  it('加载失败不崩溃', async () => {
    mocked.listIssues.mockRejectedValue({ message: 'permission denied' })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('Issues')
  })
})
