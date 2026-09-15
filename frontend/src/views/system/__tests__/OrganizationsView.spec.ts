import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, getActivePinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import OrganizationsView from '../OrganizationsView.vue'
import { useAuthStore } from '@/stores/auth'
import { createOrg, deleteOrg, listOrgs, updateOrg } from '@/api/org'

vi.mock('@/api/org', () => ({
  listOrgs: vi.fn(),
  createOrg: vi.fn(),
  updateOrg: vi.fn(),
  deleteOrg: vi.fn(),
  getOrg: vi.fn(),
  listOrgMembers: vi.fn(),
  addOrgMember: vi.fn(),
  removeOrgMember: vi.fn(),
  listDepartments: vi.fn(),
  createDepartment: vi.fn(),
  updateDepartment: vi.fn(),
  deleteDepartment: vi.fn(),
}))

const push = vi.fn()
vi.mock('vue-router', () => ({
  createRouter: () => ({ beforeEach: vi.fn(), push: vi.fn(), currentRoute: { value: { path: '/dashboard', query: {} } } }),
  createWebHistory: () => ({}),
  useRouter: () => ({ push }),
}))
const mocked = {
  listOrgs: vi.mocked(listOrgs),
  createOrg: vi.mocked(createOrg),
  updateOrg: vi.mocked(updateOrg),
  deleteOrg: vi.mocked(deleteOrg),
}

const ORGS = [
  { id: 1, name: 'Acme', code: 'ACME_HQ', ownerId: 1, description: 'd', createdAt: '', updatedAt: '' },
]

const mountView = () =>
  mount(OrganizationsView, { global: { plugins: [getActivePinia() ?? createPinia(), ElementPlus] } })

describe('OrganizationsView（P4-03）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    useAuthStore().permissionCodes = ['org:create', 'org:update', 'org:delete', 'org:list']
    mocked.listOrgs.mockResolvedValue({ list: ORGS, total: 1, page: 1, size: 10 })
  })

  it('加载并渲染组织列表', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('Acme')
    expect(wrapper.text()).toContain('ACME_HQ')
    expect(mocked.listOrgs).toHaveBeenCalled()
  })

  it('空列表展示空状态', async () => {
    mocked.listOrgs.mockResolvedValue({ list: [], total: 0, page: 1, size: 10 })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('暂无组织')
  })

  it('无 org:create 权限时新建按钮不渲染', async () => {
    useAuthStore().permissionCodes = []
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.findAll('button').find((b) => b.text() === '新建组织')).toBeUndefined()
  })

  it('新建组织经真实 API 且编码大写化', async () => {
    mocked.createOrg.mockResolvedValue(ORGS[0])
    const wrapper = mountView()
    await flushPromises()
    await wrapper.findAll('button').find((b) => b.text() === '新建组织')!.trigger('click')
    await flushPromises()
    const inputs = wrapper.findAll('.el-dialog input')
    await inputs[0].setValue('新组织')
    await inputs[1].setValue('new_org')
    const saveBtn = wrapper.findAll('.el-dialog button').find((b) => b.text() === '保存')
    await saveBtn?.trigger('click')
    await flushPromises()
    expect(mocked.createOrg).toHaveBeenCalledWith(
      expect.objectContaining({ code: 'NEW_ORG', name: '新组织' }),
    )
  })

  it('删除需确认后才调用 API', async () => {
    const wrapper = mountView()
    await flushPromises()
    const del = wrapper.findAll('button').find((b) => b.text() === '删除')
    await del?.trigger('click')
    await flushPromises()
    expect(mocked.deleteOrg).not.toHaveBeenCalled()
  })

  it('编辑仅允许改名称与描述', async () => {
    mocked.updateOrg.mockResolvedValue(ORGS[0])
    const wrapper = mountView()
    await flushPromises()
    const edit = wrapper.findAll('button').find((b) => b.text() === '编辑')
    await edit?.trigger('click')
    await flushPromises()
    const codeInput = wrapper.findAll('.el-dialog input')[1]
    expect((codeInput?.element as HTMLInputElement).disabled).toBe(true)
    const saveBtn = wrapper.findAll('.el-dialog button').find((b) => b.text() === '保存')
    await saveBtn?.trigger('click')
    await flushPromises()
    expect(mocked.updateOrg).toHaveBeenCalledWith(1, expect.objectContaining({ name: 'Acme' }))
  })
})
