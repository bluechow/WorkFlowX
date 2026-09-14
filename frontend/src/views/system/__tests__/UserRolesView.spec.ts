import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import UserRolesView from '../UserRolesView.vue'
import { getUserRoles, listRoles, replaceUserRoles } from '@/api/rbac'
import http from '@/api/http'

vi.mock('@/api/rbac', () => ({
  listRoles: vi.fn(),
  getUserRoles: vi.fn(),
  replaceUserRoles: vi.fn(),
  createRole: vi.fn(),
  updateRole: vi.fn(),
  deleteRole: vi.fn(),
  getRolePermissions: vi.fn(),
  assignRolePermissions: vi.fn(),
  listPermissions: vi.fn(),
}))

// http mock 位于 HTTP boundary：拦截器已解包 Result，AxiosResponse.data 即 PageVO
vi.mock('@/api/http', async () => {
  const actual = await vi.importActual<typeof import('@/api/http')>('@/api/http')
  return { default: { ...actual.default, get: vi.fn() } }
})

const mockedHttp = vi.mocked(http.get, true)
const mocked = {
  listRoles: vi.mocked(listRoles),
  getUserRoles: vi.mocked(getUserRoles),
  replaceUserRoles: vi.mocked(replaceUserRoles),
}

const USERS_PAGE = {
  list: [
    { id: 2, username: 'user1', email: 'user1@workflowx.local', nickname: '测试用户一', status: 'ACTIVE', lastLoginAt: null, createdAt: '', updatedAt: '' },
  ],
  total: 1,
  page: 1,
  size: 10,
}

const ROLES = [
  { id: 1, code: 'ADMIN', name: '系统管理员', description: null, system: true, createdAt: '', updatedAt: '' },
  { id: 2, code: 'MEMBER', name: '普通成员', description: null, system: true, createdAt: '', updatedAt: '' },
]

const mountView = () =>
  mount(UserRolesView, { global: { plugins: [createPinia(), ElementPlus] } })

describe('UserRolesView（P3-04）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedHttp.mockResolvedValue({ data: USERS_PAGE } as never)
    mocked.listRoles.mockResolvedValue(ROLES)
  })

  it('加载用户分页列表', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('user1')
    expect(wrapper.text()).toContain('user1@workflowx.local')
  })

  it('分配对话框加载全量角色与用户当前角色并 replace 提交', async () => {
    mocked.getUserRoles.mockResolvedValue(['MEMBER'])
    mocked.replaceUserRoles.mockResolvedValue(['MEMBER', 'ADMIN'])
    const wrapper = mountView()
    await flushPromises()
    const assignBtn = wrapper.findAll('button').find((b) => b.text() === '分配角色')
    await assignBtn?.trigger('click')
    await flushPromises()
    expect(mocked.getUserRoles).toHaveBeenCalledWith(2)
    // 勾选 ADMIN 后保存
    const checkboxes = wrapper.findAll('.el-dialog .el-checkbox')
    expect(checkboxes.length).toBe(2)
    await checkboxes[0].find('input').setValue(true)
    const submitBtn = wrapper.findAll('.el-dialog button').find((b) => b.text() === '保存')
    await submitBtn?.trigger('click')
    await flushPromises()
    expect(mocked.replaceUserRoles).toHaveBeenCalledWith(2, ['MEMBER', 'ADMIN'])
  })

  it('用户加载失败不崩溃', async () => {
    mockedHttp.mockRejectedValue({ message: 'network error' })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('用户角色管理')
  })
})
