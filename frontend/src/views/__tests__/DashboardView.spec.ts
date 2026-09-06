import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, getActivePinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import DashboardView from '../DashboardView.vue'
import { useAuthStore } from '@/stores/auth'
import { logout as logoutApi } from '@/api/auth'

const push = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
}))
vi.mock('@/api/auth', () => ({
  fetchMe: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

const mockedLogout = vi.mocked(logoutApi)

const TOKEN = 'dashboard-test-token'

const CURRENT_USER = {
  id: 7,
  username: 'alice',
  email: 'alice@test.local',
  nickname: 'Alice',
  status: 'ACTIVE',
  lastLoginAt: '2026-09-06T00:00:00Z',
  createdAt: '2026-09-06T00:00:00Z',
  updatedAt: '2026-09-06T00:00:00Z',
} as const

function mountView() {
  // 使用 active pinia（与 givenAuthenticated 预置的状态一致）
  return mount(DashboardView, {
    global: { plugins: [getActivePinia() ?? createPinia(), ElementPlus] },
  })
}

describe('DashboardView（P2-21）', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
    setActivePinia(createPinia())
    push.mockClear()
  })

  function givenAuthenticated() {
    localStorage.setItem('workflowx_access_token', TOKEN)
    const store = useAuthStore()
    store.accessToken = TOKEN
    store.username = 'alice'
    store.roles = ['MEMBER']
    store.currentUser = { ...CURRENT_USER }
    return store
  }

  it('正确展示 currentUser 资料（nickname/username/email/status/roles）', async () => {
    givenAuthenticated()
    const wrapper = mountView()
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('欢迎回来，Alice')
    expect(text).toContain('alice')
    expect(text).toContain('alice@test.local')
    expect(text).toContain('ACTIVE')
    expect(text).toContain('MEMBER')
  })

  it('未认证状态不显示伪造用户信息', async () => {
    const wrapper = mountView() // 全新空 pinia，无 currentUser
    await flushPromises()
    expect(wrapper.text()).not.toContain('欢迎回来，')
    expect(wrapper.text()).not.toContain('alice')
  })

  it('页面不显示 token/密码等敏感信息', async () => {
    givenAuthenticated()
    const wrapper = mountView()
    await flushPromises()
    const text = wrapper.text()
    expect(text).not.toContain(TOKEN)
    expect(text).not.toContain('password')
    expect(text).not.toContain('Bearer')
  })

  it('点击退出登录调用后端并跳转 /login', async () => {
    givenAuthenticated()
    const wrapper = mountView()
    await flushPromises()
    const btns = wrapper.findAll('button')
    const logoutBtn = btns.find((b) => b.text().includes('退出登录'))
    await logoutBtn?.trigger('click')
    await flushPromises()
    expect(mockedLogout).toHaveBeenCalled()
    expect(push).toHaveBeenCalledWith('/login')
  })
})
