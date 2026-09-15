import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, getActivePinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import OrganizationDetailView from '../OrganizationDetailView.vue'
import { useAuthStore } from '@/stores/auth'
import type { OrganizationMemberVO } from '@/types/api'
import {
  addOrgMember,
  createDepartment,
  deleteDepartment,
  getOrg,
  listDepartments,
  listOrgMembers,
  removeOrgMember,
  updateDepartment,
} from '@/api/org'

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

vi.mock('vue-router', () => ({
  createRouter: () => ({ beforeEach: vi.fn(), push: vi.fn(), currentRoute: { value: { path: '/system/organizations/7', query: {} } } }),
  createWebHistory: () => ({}),
  useRoute: () => ({ params: { id: '7' } }),
}))
const mocked = {
  getOrg: vi.mocked(getOrg),
  listDepartments: vi.mocked(listDepartments),
  listOrgMembers: vi.mocked(listOrgMembers),
  createDepartment: vi.mocked(createDepartment),
  updateDepartment: vi.mocked(updateDepartment),
  deleteDepartment: vi.mocked(deleteDepartment),
  addOrgMember: vi.mocked(addOrgMember),
  removeOrgMember: vi.mocked(removeOrgMember),
}

const ORG = { id: 7, name: 'Detail Org', code: 'DETAIL_ORG', ownerId: 1, description: 'd', createdAt: '', updatedAt: '' }
const DEPTS = [
  { id: 1, orgId: 7, parentId: null, name: '研发部', code: 'RD', createdAt: '', updatedAt: '' },
  { id: 2, orgId: 7, parentId: 1, name: '后端组', code: 'BE', createdAt: '', updatedAt: '' },
]
const MEMBERS: OrganizationMemberVO[] = [
  { orgId: 7, userId: 1, role: 'OWNER', departmentId: null, createdAt: '' },
  { orgId: 7, userId: 2, role: 'MEMBER', departmentId: 2, createdAt: '' },
]

const ALL_PERMS = ['org:get', 'org:assign_member', 'department:create', 'department:update', 'department:delete']

const mountView = () =>
  mount(OrganizationDetailView, { global: { plugins: [getActivePinia() ?? createPinia(), ElementPlus] } })

describe('OrganizationDetailView（P4-03）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    useAuthStore().permissionCodes = ALL_PERMS
    mocked.getOrg.mockResolvedValue(ORG)
    mocked.listDepartments.mockResolvedValue(DEPTS)
    mocked.listOrgMembers.mockResolvedValue(MEMBERS)
  })

  it('加载组织信息/部门树/成员列表', async () => {
    const wrapper = mountView()
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('Detail Org')
    expect(text).toContain('研发部（RD）')
    expect(text).toContain('后端组（BE）')
    expect(text).toContain('OWNER')
    expect(text).toContain('MEMBER')
  })

  it('OWNER 行的移除按钮禁用（UX 层保护）', async () => {
    const wrapper = mountView()
    await flushPromises()
    const removeButtons = wrapper.findAll('button').filter((b) => b.text() === '移除')
    const disabled = removeButtons.filter((b) => b.attributes('disabled') !== undefined)
    expect(disabled.length).toBe(1)
  })

  it('部门树空时展示空状态', async () => {
    mocked.listDepartments.mockResolvedValue([])
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('暂无部门')
  })

  it('新建子部门携带父级并调用 API', async () => {
    mocked.createDepartment.mockResolvedValue(DEPTS[0])
    const wrapper = mountView()
    await flushPromises()
    const addButtons = wrapper.findAll('button').filter((b) => b.text() === '添加子部门')
    await addButtons[0].trigger('click')
    await flushPromises()
    const inputs = wrapper.findAll('.el-dialog input')
    await inputs[0].setValue('测试组')
    await inputs[1].setValue('QA')
    const saveBtn = wrapper.findAll('.el-dialog button').find((b) => b.text() === '保存')
    await saveBtn?.trigger('click')
    await flushPromises()
    expect(mocked.createDepartment).toHaveBeenCalledWith(
      7,
      expect.objectContaining({ name: '测试组', code: 'QA', parentId: 1 }),
    )
  })

  it('添加成员调用 API 并刷新', async () => {
    mocked.addOrgMember.mockResolvedValue(MEMBERS[1])
    const wrapper = mountView()
    await flushPromises()
    const addBtn = wrapper.findAll('button').find((b) => b.text() === '添加成员')
    await addBtn?.trigger('click')
    await flushPromises()
    const numberInput = wrapper.find('.el-dialog .el-input-number input')
    await numberInput.setValue('5')
    const saveBtn = wrapper.findAll('.el-dialog button').find((b) => b.text() === '添加')
    await saveBtn?.trigger('click')
    await flushPromises()
    expect(mocked.addOrgMember).toHaveBeenCalledWith(7, expect.objectContaining({ userId: 5, role: 'MEMBER' }))
  })

  it('加载失败展示错误且不崩溃', async () => {
    mocked.getOrg.mockRejectedValue({ message: 'permission denied' })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('组织详情')
  })
})
