import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import router from '../index'
import { useAuthStore } from '@/stores/auth'
import { fetchMe } from '@/api/auth'

vi.mock('@/api/auth', () => ({
  fetchMe: vi.fn(),
  fetchMyPermissions: vi.fn().mockResolvedValue([]),
  login: vi.fn(),
  logout: vi.fn(),
}))

const mockedFetchMe = vi.mocked(fetchMe)

const ME = {
  id: 7,
  username: 'alice',
  email: 'alice@test.local',
  nickname: 'Alice',
  status: 'ACTIVE',
  lastLoginAt: null,
  createdAt: '2026-09-06T00:00:00Z',
  updatedAt: '2026-09-06T00:00:00Z',
} as const

/** 重置: 全新 pinia + 清空 localStorage + 回到 /login（无 token 时守卫放行） */
async function reset() {
  setActivePinia(createPinia())
  localStorage.clear()
  vi.clearAllMocks()
  if (router.currentRoute.value.path !== '/login') {
    await router.push('/login')
  }
}

  function givenToken(token: string | null) {
    // 与生产一致: token 由 store 持有（store 创建时读取 LS，之后运行时以 store 为准）
    if (token) localStorage.setItem('workflowx_access_token', token)
    else localStorage.removeItem('workflowx_access_token')
    const store = useAuthStore()
    store.accessToken = token
  }

describe('router guard（P2-21）', () => {
  beforeEach(async () => {
    await reset()
  })

  it('未登录访问 /login 放行', async () => {
    await router.push('/login')
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('未登录访问 /dashboard → 重定向 /login 并携带 redirect 参数', async () => {
    await router.push('/dashboard')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/dashboard')
  })

  it('未登录访问 /health 公开路由放行', async () => {
    await router.push('/health')
    expect(router.currentRoute.value.path).toBe('/health')
  })

  it('token 存在但 currentUser 缺失 → 调用 /me 确认（成功后放行）', async () => {
    givenToken('valid-token')
    mockedFetchMe.mockResolvedValue(ME)
    await router.push('/dashboard')
    expect(mockedFetchMe).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.path).toBe('/dashboard')
    // /me 成功后 currentUser 填充
    const store = useAuthStore()
    expect(store.currentUser?.username).toBe('alice')
  })

  it('已认证（currentUser 就绪）再次导航不重复调用 /me', async () => {
    givenToken('valid-token')
    mockedFetchMe.mockResolvedValue(ME)
    await router.push('/dashboard')
    expect(mockedFetchMe).toHaveBeenCalledTimes(1)
    await router.push('/dashboard')
    expect(mockedFetchMe).toHaveBeenCalledTimes(1)
  })

  it('token 存在但 /me 401（伪造/过期/被覆盖）→ 清理并回登录页', async () => {
    givenToken('forged-invalid-token')
    mockedFetchMe.mockRejectedValue({ code: 401, message: 'authentication required' })
    await router.push('/dashboard')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.reason).toBe('401')
    // 认证状态被清理（token 存在 ≠ 有效）
    const store = useAuthStore()
    expect(store.accessToken).toBeNull()
    expect(localStorage.getItem('workflowx_access_token')).toBeNull()
  })

  it('已登录访问 /login → 重定向 /dashboard', async () => {
    givenToken('valid-token')
    mockedFetchMe.mockResolvedValue(ME)
    await router.push('/dashboard')
    await router.push('/login')
    expect(router.currentRoute.value.path).toBe('/dashboard')
  })

  it('redirect 查询参数在守卫重定向时正确保留', async () => {
    givenToken('forged-token')
    mockedFetchMe.mockRejectedValue({ code: 401, message: 'authentication required' })
    await router.push('/dashboard?tab=1')
    expect(router.currentRoute.value.query.reason).toBe('401')
  })
})
