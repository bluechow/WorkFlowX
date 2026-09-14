import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import PermissionsView from '../PermissionsView.vue'
import { listPermissions } from '@/api/rbac'

vi.mock('@/api/rbac', () => ({
  listPermissions: vi.fn(),
  createRole: vi.fn(),
  updateRole: vi.fn(),
  deleteRole: vi.fn(),
  getRolePermissions: vi.fn(),
  assignRolePermissions: vi.fn(),
  listRoles: vi.fn(),
  getUserRoles: vi.fn(),
  replaceUserRoles: vi.fn(),
}))

const mocked = { listPermissions: vi.mocked(listPermissions) }

const mountView = () =>
  mount(PermissionsView, { global: { plugins: [createPinia(), ElementPlus] } })

describe('PermissionsView（P3-04）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('渲染权限列表（编码/名称/类型/系统标记）', async () => {
    mocked.listPermissions.mockResolvedValue([
      { id: 1, code: 'user:list', name: '用户列表', type: 'API', description: '分页查询用户', system: true, createdAt: '', updatedAt: '' },
    ])
    const wrapper = mountView()
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('user:list')
    expect(text).toContain('用户列表')
    expect(text).toContain('系统')
  })

  it('加载失败不崩溃', async () => {
    mocked.listPermissions.mockRejectedValue({ message: 'network error' })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('权限管理')
  })
})
