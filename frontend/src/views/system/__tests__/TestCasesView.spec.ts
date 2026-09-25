import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, getActivePinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import TestCasesView from '../TestCasesView.vue'
import {
  createDirectory,
  createTestCase,
  deleteTestCase,
  listDirectories,
  listTestCases,
  type DirectoryVO,
  type TestCaseVO,
} from '@/api/testcase'
import { useAuthStore } from '@/stores/auth'

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { projectId: '7' } }),
  useRouter: () => ({ push: vi.fn() }),
  createRouter: () => ({
    beforeEach: vi.fn(),
    push: vi.fn(),
    currentRoute: { value: { params: { projectId: '7' }, path: '/system/projects/7/testcases', query: {} } },
  }),
  createWebHistory: () => ({}),
}))

vi.mock('@/api/testcase', () => ({
  listDirectories: vi.fn(),
  createDirectory: vi.fn(),
  updateDirectory: vi.fn(),
  deleteDirectory: vi.fn(),
  listTestCases: vi.fn(),
  createTestCase: vi.fn(),
  updateTestCase: vi.fn(),
  deleteTestCase: vi.fn(),
}))

const mocked = {
  listDirectories: vi.mocked(listDirectories),
  createDirectory: vi.mocked(createDirectory),
  listTestCases: vi.mocked(listTestCases),
  createTestCase: vi.mocked(createTestCase),
  deleteTestCase: vi.mocked(deleteTestCase),
}

const DIR: DirectoryVO = {
  id: 5, projectId: 7, parentId: null, name: '登录模块', createdBy: 1, createdAt: '2026-09-25T10:00:00',
}
const CASE: TestCaseVO = {
  id: 11, projectId: 7, directoryId: 5, testcaseNo: 1, title: '登录成功用例',
  preconditions: null, steps: null, expected: null, type: 'FUNCTIONAL',
  priority: 'HIGH', status: 'ACTIVE', createdBy: 1,
  createdAt: '2026-09-25T10:00:00', updatedAt: '2026-09-25T10:00:00',
}

const mountView = () =>
  mount(TestCasesView, {
    global: {
      plugins: [getActivePinia() ?? createPinia(), ElementPlus],
      mocks: { $route: { params: { projectId: '7' } } },
    },
  })

describe('TestCasesView（Phase 20）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    const store = useAuthStore()
    store.permissionCodes = ['testcase:list', 'testcase:create', 'testcase:update', 'testcase:delete']
    store.currentUser = { id: 1, username: 'admin', email: 'a@a', nickname: null, status: 'ACTIVE', lastLoginAt: null, createdAt: '', updatedAt: '' }
    mocked.listDirectories.mockResolvedValue([DIR])
    mocked.listTestCases.mockResolvedValue({ list: [CASE], total: 1, page: 1, size: 10 })
  })

  it('加载目录与用例并渲染（P20 核心：真实解包）', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(mocked.listDirectories).toHaveBeenCalledWith(7)
    expect(mocked.listTestCases).toHaveBeenCalled()
    expect(wrapper.text()).toContain('登录模块')
    expect(wrapper.text()).toContain('登录成功用例')
    expect(wrapper.text()).toContain('TC-1')
  })

  it('空用例展示空状态', async () => {
    mocked.listTestCases.mockResolvedValue({ list: [], total: 0, page: 1, size: 10 })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('暂无用例')
  })

  it('加载失败展示错误提示', async () => {
    mocked.listTestCases.mockRejectedValue({ code: 403, message: '仅项目成员可操作' })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('仅项目成员可操作')
  })

  it('创建用例成功后重新拉取', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.findAll('button').find((b) => b.text().includes('新建用例'))!.trigger('click')
    const dialog = wrapper.find('.el-dialog')
    await dialog.findAll('input')[0].setValue('新用例标题')
    mocked.createTestCase.mockResolvedValue(CASE)
    await dialog.findAll('button').find((b) => b.text().includes('保存'))!.trigger('click')
    await flushPromises()
    expect(mocked.createTestCase).toHaveBeenCalled()
    expect(mocked.listTestCases).toHaveBeenCalledTimes(2)
  })

  it('空标题创建被前端拦截', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.findAll('button').find((b) => b.text().includes('新建用例'))!.trigger('click')
    const dialog = wrapper.find('.el-dialog')
    await dialog.findAll('button').find((b) => b.text().includes('保存'))!.trigger('click')
    await flushPromises()
    expect(mocked.createTestCase).not.toHaveBeenCalled()
  })

  it('无 testcase:create 权限隐藏新建按钮（纯 UX）', async () => {
    const store = useAuthStore()
    store.permissionCodes = ['testcase:list']
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.findAll('button').some((b) => b.text().includes('新建用例'))).toBe(false)
  })
})
