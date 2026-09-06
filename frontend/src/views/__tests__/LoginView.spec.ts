import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import LoginView from '../LoginView.vue'
import { login as loginApi } from '@/api/auth'

const push = vi.fn()
let routeQuery: Record<string, string> = {}
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: routeQuery }),
  useRouter: () => ({ push }),
}))
vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  fetchMe: vi.fn(),
}))

const mockedLogin = vi.mocked(loginApi)

const LOGIN_OK = {
  accessToken: 'view-token',
  tokenType: 'Bearer',
  expiresIn: 7200,
  userId: 7,
  username: 'alice',
  roles: [] as string[],
}

const mountView = () =>
  mount(LoginView, {
    global: { plugins: [createPinia(), ElementPlus] },
  })

async function submitForm(wrapper: ReturnType<typeof mountView>, username: string, password: string) {
  const inputs = wrapper.findAll('input')
  await inputs[0].setValue(username)
  await inputs[1].setValue(password)
  await wrapper.find('form').trigger('submit')
  await flushPromises()
}

describe('LoginView（P2-21）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    routeQuery = {}
  })

  it('渲染用户名与密码表单', () => {
    const wrapper = mountView()
    const inputs = wrapper.findAll('input')
    expect(inputs.length).toBeGreaterThanOrEqual(2)
    expect(wrapper.find('input[type="password"]').exists()).toBe(true)
  })

  it('空表单提交不触发登录请求且展示校验错误', async () => {
    const wrapper = mountView()
    await submitForm(wrapper, '', '')
    expect(mockedLogin).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('请输入用户名')
  })

  it('登录失败时展示后端统一错误信息并停留当前页', async () => {
    mockedLogin.mockRejectedValue({ code: 401, message: '用户名或密码错误' })
    const wrapper = mountView()
    await submitForm(wrapper, 'p2e2e', 'WrongPass@1')
    expect(wrapper.text()).toContain('用户名或密码错误')
    expect(push).not.toHaveBeenCalled()
  })

  it('429 锁定错误信息展示', async () => {
    mockedLogin.mockRejectedValue({ code: 429, message: '登录尝试次数过多，请稍后再试' })
    const wrapper = mountView()
    await submitForm(wrapper, 'lockuser', 'Whatever@123')
    expect(wrapper.text()).toContain('登录尝试次数过多，请稍后再试')
    expect(push).not.toHaveBeenCalled()
  })

  it('登录成功默认跳转 /dashboard', async () => {
    mockedLogin.mockResolvedValue(LOGIN_OK)
    const wrapper = mountView()
    await submitForm(wrapper, 'alice', 'MockOnly-Not-A-Real-Credential')
    expect(push).toHaveBeenCalledWith('/dashboard')
    expect(localStorage.getItem('workflowx_access_token')).toBe('view-token')
  })

  it('登录成功跳转到 redirect 指定页面', async () => {
    routeQuery = { redirect: '/health' }
    mockedLogin.mockResolvedValue(LOGIN_OK)
    const wrapper = mountView()
    await submitForm(wrapper, 'alice', 'MockOnly-Not-A-Real-Credential')
    expect(push).toHaveBeenCalledWith('/health')
  })

  it('提交中防重复提交（loading 期间忽略再次提交）', async () => {
    const deferred: { resolve?: (v: typeof LOGIN_OK) => void } = {}
    mockedLogin.mockImplementation(
      () =>
        new Promise<typeof LOGIN_OK>((resolve) => {
          deferred.resolve = resolve
        }),
    )
    const wrapper = mountView()
    await submitForm(wrapper, 'alice', 'MockOnly-Not-A-Real-Credential')
    // 第一次提交进行中: 按钮禁用
    expect(mockedLogin).toHaveBeenCalledTimes(1)
    const btn = wrapper.find('button')
    expect(btn.attributes('disabled')).toBeDefined()
    // 提交未完成时再次提交被忽略
    await submitForm(wrapper, 'alice', 'MockOnly-Not-A-Real-Credential')
    expect(mockedLogin).toHaveBeenCalledTimes(1)
    deferred.resolve?.(LOGIN_OK)
    await flushPromises()
    expect(push).toHaveBeenCalledWith('/dashboard')
  })

  it('API 失败后停留登录页且可再次提交', async () => {
    mockedLogin.mockRejectedValueOnce({ code: 401, message: '用户名或密码错误' })
    mockedLogin.mockResolvedValueOnce(LOGIN_OK)
    const wrapper = mountView()
    await submitForm(wrapper, 'alice', 'WrongPass@1')
    expect(wrapper.text()).toContain('用户名或密码错误')
    await submitForm(wrapper, 'alice', 'MockOnly-Not-A-Real-Credential')
    expect(push).toHaveBeenCalledWith('/dashboard')
  })

  it('登录成功后页面不显示 token 明文', async () => {
    mockedLogin.mockResolvedValue(LOGIN_OK)
    const wrapper = mountView()
    await submitForm(wrapper, 'alice', 'MockOnly-Not-A-Real-Credential')
    await flushPromises()
    expect(wrapper.text()).not.toContain('view-token')
  })
})
