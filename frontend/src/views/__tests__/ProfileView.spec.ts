import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, getActivePinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import ProfileView from '@/views/ProfileView.vue'
import { useAuthStore } from '@/stores/auth'
import { changePassword, updateProfile } from '@/api/auth'
import { listMyTodoIssues } from '@/api/me'

vi.mock('@/api/auth', () => ({
  updateProfile: vi.fn(),
  changePassword: vi.fn(),
  logout: vi.fn(),
}))
vi.mock('@/api/me', () => ({
  listMyTodoIssues: vi.fn(),
}))

const replace = vi.fn()
vi.mock('vue-router', () => ({
  createRouter: () => ({ beforeEach: vi.fn(), push: vi.fn() }),
  createWebHistory: () => ({}),
  useRouter: () => ({ push: vi.fn(), replace }),
}))

const mocked = {
  updateProfile: vi.mocked(updateProfile),
  changePassword: vi.mocked(changePassword),
  listMyTodoIssues: vi.mocked(listMyTodoIssues),
}

const mountIt = () =>
  mount(ProfileView, { global: { plugins: [getActivePinia() ?? createPinia(), ElementPlus] } })

describe('ProfileView（Phase A-④）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    const store = useAuthStore()
    store.currentUser = {
      id: 9, username: 'demo_u01', email: 'u01@demo.local', nickname: '张伟',
      status: 'ACTIVE', lastLoginAt: null, createdAt: '', updatedAt: '',
    }
    mocked.listMyTodoIssues.mockResolvedValue([
      {
        issueId: 1, projectId: 7, projectKey: 'CAMPUS', projectName: '智慧校园管理系统',
        issueNo: 1, title: '学生选课并发提交时接口返回 500', type: 'BUG', priority: 'URGENT',
        severity: 'S1', status: 'OPEN', updatedAt: '2026-09-20T10:00:00',
      },
    ])
  })

  it('渲染资料表单（预填当前用户）与待办列表（中文标签）', async () => {
    const wrapper = mountIt()
    await flushPromises()
    expect(wrapper.text()).toContain('我的资料')
    expect(wrapper.text()).toContain('修改密码')
    expect(wrapper.text()).toContain('我的待办（1）')
    expect(wrapper.text()).toContain('CAMPUS-1')
    expect(wrapper.text()).toContain('紧急')
    expect(wrapper.text()).toContain('学生选课并发提交时接口返回 500')
    // 用户名禁用（不可自改）
    const inputs = wrapper.findAll('input')
    expect(inputs.some((i) => (i.element as HTMLInputElement).disabled)).toBe(true)
  })

  it('保存资料调用 updateProfile 并同步 store', async () => {
    const updated = {
      id: 9, username: 'demo_u01', email: 'new@demo.local', nickname: '新名',
      status: 'ACTIVE' as const, lastLoginAt: null, createdAt: '', updatedAt: '',
    }
    mocked.updateProfile.mockResolvedValue(updated)
    const wrapper = mountIt()
    await flushPromises()
    const emailInput = wrapper.findAll('input').find((i) => i.attributes('placeholder') === 'name@example.com')!
    await emailInput.setValue('new@demo.local')
    const saveBtn = wrapper.findAll('button').find((b) => b.text() === '保存资料')!
    await saveBtn.trigger('click')
    await flushPromises()
    expect(mocked.updateProfile).toHaveBeenCalledWith({ email: 'new@demo.local', nickname: '张伟' })
    expect(useAuthStore().currentUser?.email).toBe('new@demo.local')
  })

  it('修改密码成功后登出并跳转登录页', async () => {
    mocked.changePassword.mockResolvedValue(undefined)
    const wrapper = mountIt()
    await flushPromises()
    const pwInputs = wrapper.findAll('input[type="password"]')
    await pwInputs[0].setValue('OldPass@123')
    await pwInputs[1].setValue('NewPass@456')
    await pwInputs[2].setValue('NewPass@456')
    const btn = wrapper.findAll('button').find((b) => b.text() === '修改密码')!
    await btn.trigger('click')
    await flushPromises()
    expect(mocked.changePassword).toHaveBeenCalledWith({
      oldPassword: 'OldPass@123',
      newPassword: 'NewPass@456',
    })
    // logout 被调用（清理本地态），随后跳登录页
    const auth = useAuthStore()
    expect(auth.isAuthenticated).toBe(false)
  })

  it('两次新密码不一致时不发请求', async () => {
    const wrapper = mountIt()
    await flushPromises()
    const pwInputs = wrapper.findAll('input[type="password"]')
    await pwInputs[0].setValue('OldPass@123')
    await pwInputs[1].setValue('NewPass@456')
    await pwInputs[2].setValue('Different@789')
    const btn = wrapper.findAll('button').find((b) => b.text() === '修改密码')!
    await btn.trigger('click')
    await flushPromises()
    expect(mocked.changePassword).not.toHaveBeenCalled()
  })
})
