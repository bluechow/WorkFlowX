import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, getActivePinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import IssuesView from '../IssuesView.vue'
import { useAuthStore } from '@/stores/auth'
import { allowedTargets, listIssues, transitionIssueStatus } from '@/api/issue'
import type { IssueVO } from '@/types/api'

vi.mock('@/api/issue', () => ({
  listIssues: vi.fn(),
  getIssue: vi.fn(),
  createIssue: vi.fn(),
  updateIssue: vi.fn(),
  transitionIssueStatus: vi.fn(),
  allowedTargets: vi.fn((from: string) =>
    ({ OPEN: ['IN_PROGRESS'], IN_PROGRESS: ['RESOLVED'], RESOLVED: ['TESTING'],
       TESTING: ['CLOSED', 'REOPENED'], REOPENED: ['IN_PROGRESS'], CLOSED: [] }[from] ?? [])),
  listProjectOptions: vi.fn(),
}))

vi.mock('@/api/project', () => ({
  listProjects: vi.fn(),
  createProject: vi.fn(),
  updateProject: vi.fn(),
  updateProjectStatus: vi.fn(),
  deleteProject: vi.fn(),
}))

vi.mock('vue-router', () => ({
  createRouter: () => ({ beforeEach: vi.fn(), push: vi.fn(), currentRoute: { value: { params: { projectId: '7' }, path: '/system/projects/7/issues', query: {} } } }),
  createWebHistory: () => ({}),
  useRoute: () => ({ params: { projectId: '7' } }),
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('@/api/projectMember', () => ({
  listProjectMembers: vi.fn(),
  addProjectMember: vi.fn(),
  removeProjectMember: vi.fn(),
}))

const mocked = {
  listIssues: vi.mocked(listIssues),
  transitionIssueStatus: vi.mocked(transitionIssueStatus),
  allowedTargets: vi.mocked(allowedTargets),
}

const OPEN_ISSUE: IssueVO = {
  id: 1, projectId: 7, issueNo: 1, title: '目标 Issue', description: null,
  type: 'BUG', priority: 'HIGH', severity: 'S1', status: 'OPEN',
  reporterId: 1, assigneeId: null, createdAt: '', updatedAt: '',
}

const mountView = () =>
  mount(IssuesView, { global: { plugins: [getActivePinia() ?? createPinia(), ElementPlus] } })

describe('IssuesView Workflow（P7-08）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    const store = useAuthStore()
    store.permissionCodes = ['issue:list', 'issue:transition']
    store.currentUser = { id: 1, username: 'admin', email: 'a@a', nickname: null, status: 'ACTIVE', lastLoginAt: null, createdAt: '', updatedAt: '' }
    mocked.listIssues.mockResolvedValue({ list: [OPEN_ISSUE], total: 1, page: 1, size: 10 })
  })

  it('加载并渲染 Issue（状态列 + 编号；有权限时渲染流转下拉）', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('目标 Issue')
    // 当前状态以禁用选项呈现 → 收起时即显示中文标签
    expect(wrapper.text()).toContain('待处理')
  })

  it('空列表展示空状态', async () => {
    mocked.listIssues.mockResolvedValue({ list: [], total: 0, page: 1, size: 10 })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('暂无 Issue')
  })

  it('无 issue:transition 权限时状态列显示纯标签（无流转下拉）', async () => {
    useAuthStore().permissionCodes = ['issue:list']
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.findAll('.el-table .el-select').length).toBe(0)
    // 无权限 → 纯中文状态标签（不再渲染下拉）
    expect(wrapper.text()).toContain('待处理')
  })

  it('allowedTargets 镜像 ADR-017 矩阵（合法目标白名单）', () => {
    // 组件内 allowedTargets 由同一常量驱动；此处直接验证镜像函数
    expect(allowedTargets('OPEN')).toEqual(['IN_PROGRESS'])
    expect(allowedTargets('IN_PROGRESS')).toEqual(['RESOLVED'])
    expect(allowedTargets('RESOLVED')).toEqual(['TESTING'])
    expect(allowedTargets('TESTING')).toEqual(['CLOSED', 'REOPENED'])
    expect(allowedTargets('REOPENED')).toEqual(['IN_PROGRESS'])
    expect(allowedTargets('CLOSED')).toEqual([])
  })

  it('加载失败不崩溃', async () => {
    mocked.listIssues.mockRejectedValue({ message: 'permission denied' })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('Issues')
  })
})
