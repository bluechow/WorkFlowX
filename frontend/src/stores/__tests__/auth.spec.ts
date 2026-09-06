import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../auth'
import { fetchMe, login as loginApi, logout as logoutApi } from '@/api/auth'
import type { UserVO } from '@/types/api'

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  fetchMe: vi.fn(),
}))

const mockedLogin = vi.mocked(loginApi)
const mockedLogout = vi.mocked(logoutApi)
const mockedFetchMe = vi.mocked(fetchMe)

const LOGIN_RESPONSE = {
  accessToken: 'token-abc',
  tokenType: 'Bearer',
  expiresIn: 7200,
  userId: 7,
  username: 'alice',
  roles: ['MEMBER'],
}

const ME_RESPONSE: UserVO = {
  id: 7,
  username: 'alice',
  email: 'alice@test.local',
  nickname: 'Alice',
  status: 'ACTIVE',
  lastLoginAt: null,
  createdAt: '2026-09-06T00:00:00Z',
  updatedAt: '2026-09-06T00:00:00Z',
}

describe('auth store', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('login 应持久化 token 并经 /me 填充 currentUser', async () => {
    mockedLogin.mockResolvedValue(LOGIN_RESPONSE)
    mockedFetchMe.mockResolvedValue(ME_RESPONSE)

    const store = useAuthStore()
    await store.login({ username: 'alice', password: 'Secret@123' })

    expect(store.accessToken).toBe('token-abc')
    expect(store.userId).toBe(7)
    expect(store.roles).toEqual(['MEMBER'])
    expect(store.currentUser?.username).toBe('alice')
    expect(store.isAuthenticated).toBe(true)
    expect(localStorage.getItem('workflowx_access_token')).toBe('token-abc')
    // 最终用户资料来自 /me
    expect(mockedFetchMe).toHaveBeenCalled()
  })

  it('fetchMe 失败应清空认证状态并清除 token', async () => {
    mockedLogin.mockResolvedValue(LOGIN_RESPONSE)
    mockedFetchMe.mockRejectedValue({ code: 401, message: 'authentication required' })

    const store = useAuthStore()
    await store.login({ username: 'alice', password: 'Secret@123' }).catch(() => undefined)

    expect(store.accessToken).toBeNull()
    expect(store.currentUser).toBeNull()
    expect(localStorage.getItem('workflowx_access_token')).toBeNull()
    expect(store.isAuthenticated).toBe(false)
  })

  it('logout 应调用后端并清理本地状态（后端失败也不阻塞）', async () => {
    mockedLogin.mockResolvedValue(LOGIN_RESPONSE)
    mockedFetchMe.mockResolvedValue(ME_RESPONSE)
    mockedLogout.mockRejectedValue({ code: 401, message: 'authentication required' })

    const store = useAuthStore()
    await store.login({ username: 'alice', password: 'Secret@123' })
    await store.logout()

    expect(mockedLogout).toHaveBeenCalled()
    expect(store.accessToken).toBeNull()
    expect(localStorage.getItem('workflowx_access_token')).toBeNull()
    expect(store.isAuthenticated).toBe(false)
  })

  it('刷新恢复: 初始化时应从 localStorage 恢复 token', () => {
    localStorage.setItem('workflowx_access_token', 'restored-token')
    setActivePinia(createPinia())
    const store = useAuthStore()
    expect(store.accessToken).toBe('restored-token')
    expect(store.isAuthenticated).toBe(true)
    expect(store.currentUser).toBeNull() // 有效性等待 /me 确认
  })
})
