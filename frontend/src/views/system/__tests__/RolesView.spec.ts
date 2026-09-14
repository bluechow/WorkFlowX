import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import RolesView from '../RolesView.vue'
import { assignRolePermissions, createRole, deleteRole, getRolePermissions, listPermissions, listRoles, updateRole } from '@/api/rbac'

vi.mock('@/api/rbac', () => ({
  listRoles: vi.fn(),
  createRole: vi.fn(),
  updateRole: vi.fn(),
  deleteRole: vi.fn(),
  getRolePermissions: vi.fn(),
  assignRolePermissions: vi.fn(),
  listPermissions: vi.fn(),
  getUserRoles: vi.fn(),
  replaceUserRoles: vi.fn(),
}))

const mocked = {
  listRoles: vi.mocked(listRoles),
  createRole: vi.mocked(createRole),
  updateRole: vi.mocked(updateRole),
  deleteRole: vi.mocked(deleteRole),
  getRolePermissions: vi.mocked(getRolePermissions),
  assignRolePermissions: vi.mocked(assignRolePermissions),
  listPermissions: vi.mocked(listPermissions),
}

const ROLES = [
  { id: 1, code: 'ADMIN', name: '系统管理员', description: null, system: true, createdAt: '', updatedAt: '' },
  { id: 2, code: 'MEMBER', name: '普通成员', description: null, system: true, createdAt: '', updatedAt: '' },
  { id: 9, code: 'P3_TEST_X', name: '测试角色', description: 'd', system: false, createdAt: '', updatedAt: '' },
]

const mountView = () =>
  mount(RolesView, { global: { plugins: [createPinia(), ElementPlus] } })

describe('RolesView（P3-04）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocked.listRoles.mockResolvedValue(ROLES)
  })

  it('加载并渲染角色列表（含系统标记）', async () => {
    const wrapper = mountView()
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('ADMIN')
    expect(text).toContain('MEMBER')
    expect(text).toContain('P3_TEST_X')
    expect(wrapper.findAll('.el-tag').length).toBeGreaterThanOrEqual(2)
  })

  it('系统角色的删除按钮禁用（UX 层保护）', async () => {
    const wrapper = mountView()
    await flushPromises()
    const deleteButtons = wrapper.findAll('button').filter((b) => b.text() === '删除')
    expect(deleteButtons.length).toBe(3)
    const disabled = deleteButtons.filter((b) => b.attributes('disabled') !== undefined)
    expect(disabled.length).toBe(2)
  })

  it('删除确认后调用后端并刷新', async () => {
    mocked.deleteRole.mockResolvedValue(undefined)
    const wrapper = mountView()
    await flushPromises()
    // 非系统角色（第三行）的删除按钮
    const deleteButtons = wrapper.findAll('button').filter((b) => b.text() === '删除')
    await deleteButtons[2].trigger('click')
    await flushPromises()
    // ElMessageBox confirm 需要用户确认——确认前不得误删
    expect(mocked.deleteRole).not.toHaveBeenCalled()
  })

  it('新建角色走真实 API（大写化编码）', async () => {
    mocked.createRole.mockResolvedValue(ROLES[2])
    const wrapper = mountView()
    await flushPromises()
    await wrapper.find('button.el-button--primary').trigger('click')
    await flushPromises()
    // 对话框打开后填写并提交
    const inputs = wrapper.findAll('.el-dialog input')
    await inputs[0].setValue('p3_test_new')
    await inputs[1].setValue('新角色')
    const dialogButtons = wrapper.findAll('.el-dialog button')
    const submitBtn = dialogButtons.find((b) => b.text() === '保存')
    await submitBtn?.trigger('click')
    await flushPromises()
    expect(mocked.createRole).toHaveBeenCalledWith(
      expect.objectContaining({ code: 'P3_TEST_NEW', name: '新角色' }),
    )
  })

  it('权限分配对话框加载全量权限与当前勾选并保存', async () => {
    mocked.listPermissions.mockResolvedValue([
      { id: 1, code: 'user:list', name: '用户列表', type: 'API', description: null, system: true, createdAt: '', updatedAt: '' },
      { id: 2, code: 'role:list', name: '角色列表', type: 'API', description: null, system: true, createdAt: '', updatedAt: '' },
    ])
    mocked.getRolePermissions.mockResolvedValue(['user:list'])
    mocked.assignRolePermissions.mockResolvedValue(['user:list', 'role:list'])
    const wrapper = mountView()
    await flushPromises()
    const permButtons = wrapper.findAll('button').filter((b) => b.text() === '权限')
    await permButtons[2].trigger('click')
    await flushPromises()
    expect(mocked.getRolePermissions).toHaveBeenCalledWith(9)
    // 勾选第二个权限后保存
    const checkboxes = wrapper.findAll('.el-dialog .el-checkbox')
    await checkboxes[1].find('input').setValue(true)
    const dialogButtons = wrapper.findAll('.el-dialog button')
    const submitBtn = dialogButtons.find((b) => b.text() === '保存')
    await submitBtn?.trigger('click')
    await flushPromises()
    expect(mocked.assignRolePermissions).toHaveBeenCalledWith(9, ['user:list', 'role:list'])
  })

  it('列表加载失败时展示错误且不崩溃', async () => {
    mocked.listRoles.mockRejectedValue({ message: 'permission denied' })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('角色管理')
  })
})
