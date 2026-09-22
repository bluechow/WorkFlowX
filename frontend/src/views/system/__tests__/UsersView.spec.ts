import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, getActivePinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import UsersView from '../UsersView.vue'
import { createUser, listUsers, updateUserStatus, type UserVO } from '@/api/user'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/user', () => ({
  listUsers: vi.fn(),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  updateUserStatus: vi.fn(),
}))

const mocked = {
  listUsers: vi.mocked(listUsers),
  createUser: vi.mocked(createUser),
  updateUserStatus: vi.mocked(updateUserStatus),
}

const USER = (id: number, status: UserVO['status'] = 'ACTIVE'): UserVO => ({
  id,
  username: `user_${id}`,
  email: `user_${id}@workflowx.local`,
  nickname: null,
  status,
  lastLoginAt: null,
  createdAt: '2026-09-22T10:00:00',
  updatedAt: '2026-09-22T10:00:00',
})

const mountView = () =>
  mount(UsersView, { global: { plugins: [getActivePinia() ?? createPinia(), ElementPlus] } })

describe('UsersView（P11-02 G2）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    const store = useAuthStore()
    store.permissionCodes = ['user:list', 'user:create', 'user:update', 'user:status']
    store.currentUser = { id: 1, username: 'admin', email: 'a@a', nickname: null, status: 'ACTIVE', lastLoginAt: null, createdAt: '', updatedAt: '' }
  })

  it('加载用户列表并渲染（用户名/状态/最近登录）', async () => {
    mocked.listUsers.mockResolvedValue({ list: [USER(2), USER(3, 'DISABLED')], total: 2, page: 1, size: 20 })
    const wrapper = mountView()
    await flushPromises()
    expect(mocked.listUsers).toHaveBeenCalledWith(undefined, undefined, 1, 20)
    expect(wrapper.text()).toContain('user_2')
    expect(wrapper.text()).toContain('DISABLED')
  })

  it('空列表展示暂无用户', async () => {
    mocked.listUsers.mockResolvedValue({ list: [], total: 0, page: 1, size: 20 })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('暂无用户')
  })

  it('加载失败展示错误而非暂无数据', async () => {
    mocked.listUsers.mockRejectedValue({ code: 500, message: 'db down' })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('db down')
  })

  it('无 user:list 权限时展示权限提示且不请求', async () => {
    const store = useAuthStore()
    store.permissionCodes = []
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.find('[data-test="no-permission"]').exists()).toBe(true)
    expect(mocked.listUsers).not.toHaveBeenCalled()
  })

  it('创建用户成功后重新拉取', async () => {
    mocked.listUsers.mockResolvedValue({ list: [], total: 0, page: 1, size: 20 })
    const wrapper = mountView()
    await flushPromises()
    await wrapper.findAll('button').find((b) => b.text().includes('新建用户'))!.trigger('click')
    await flushPromises()
    // dialog 内输入框（排除页面级搜索框/select 干扰）
    const dialog = wrapper.find('.el-dialog')
    const inputs = dialog.findAll('input')
    await inputs[0].setValue('newuser')
    await inputs[1].setValue('newuser@workflowx.local')
    await inputs[2].setValue('Password@123')
    mocked.createUser.mockResolvedValue(USER(9))
    await dialog.findAll('button').find((b) => b.text().includes('保存'))!.trigger('click')
    await flushPromises()
    expect(mocked.createUser).toHaveBeenCalledWith({
      username: 'newuser', email: 'newuser@workflowx.local', password: 'Password@123', nickname: undefined,
    })
    expect(mocked.listUsers).toHaveBeenCalledTimes(2)
  })

  it('自己那行不显示禁用按钮（自我保护 UX，后端 400 兜底）', async () => {
    mocked.listUsers.mockResolvedValue({ list: [USER(1), USER(2)], total: 2, page: 1, size: 20 })
    const wrapper = mountView()
    await flushPromises()
    const firstRow = wrapper.findAll('tbody tr')[0]
    expect(firstRow.text()).toContain('user_1')
    expect(firstRow.findAll('button').some((b) => b.text() === '禁用')).toBe(false)
    const secondRow = wrapper.findAll('tbody tr')[1]
    expect(secondRow.findAll('button').some((b) => b.text() === '禁用')).toBe(true)
  })

  it('状态变更（禁用）带确认后调用 API 并重拉', async () => {
    mocked.listUsers.mockResolvedValue({ list: [USER(1), USER(2)], total: 2, page: 1, size: 20 })
    const wrapper = mountView()
    await flushPromises()
    mocked.updateUserStatus.mockResolvedValue(USER(2, 'DISABLED'))
    const disableBtn = wrapper.findAll('tbody tr')[1].findAll('button').find((b) => b.text() === '禁用')
    await disableBtn!.trigger('click')
    await flushPromises()
    // ElMessageBox 确认（jsdom 下确认对话框）
    const confirmBtn = document.querySelector('.el-message-box__btns .el-button--primary') as HTMLButtonElement
    expect(confirmBtn).toBeTruthy()
    confirmBtn.click()
    await flushPromises()
    expect(mocked.updateUserStatus).toHaveBeenCalledWith(2, 'DISABLED')
    expect(mocked.listUsers).toHaveBeenCalledTimes(2)
  })
})
