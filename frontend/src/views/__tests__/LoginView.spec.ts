import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import LoginView from '../LoginView.vue'
import { login as loginApi } from '@/api/auth'

const push = vi.fn()
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push }),
}))
vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  fetchMe: vi.fn(),
}))

const mockedLogin = vi.mocked(loginApi)

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

describe('LoginView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
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
})
