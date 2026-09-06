import { beforeEach, describe, expect, it, vi } from 'vitest'
import http from '../http'
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'

/**
 * Axios 拦截器测试（P2-21 Integration）:
 * 使用真实 http 实例 + 自定义 adapter 捕获实际请求配置并注入受控响应，
 * 验证"请求拦截 → 响应拦截 → 401 行为"的真实链路，而非 mock 拦截器函数本身。
 */

type AdapterResponder = {
  status: number
  body: unknown
}

let lastRequestConfig: {
  url?: string
  authorization?: unknown
  traceId?: unknown
} | null = null
let responder: AdapterResponder = { status: 200, body: { code: 200, message: 'success', data: null } }

const push = vi.fn()
const currentRoutePath = vi.fn(() => '/dashboard')

vi.mock('@/router', () => ({
  default: {
    get currentRoute() {
      return { value: { path: currentRoutePath() } }
    },
    push: (...args: unknown[]) => push(...(args as [])),
  },
}))

// 安装受控 adapter: 捕获请求配置 + 注入受控响应（走真实拦截器链）。
// 非成功状态须以带 response 的 rejection 抛出（与 axios 内置 adapter 的 settle 行为一致），
// 否则自定义 adapter 的 401 会被当作成功响应。
http.defaults.adapter = async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => {
  const headers = config.headers as unknown as Record<string, unknown>
  lastRequestConfig = {
    url: config.url,
    authorization: headers?.Authorization,
    traceId: headers?.['X-Trace-Id'],
  }
  const response: AxiosResponse = {
    data: responder.body,
    status: responder.status,
    statusText: String(responder.status),
    headers: {},
    config,
    request: {},
  }
  if (responder.status >= 300) {
    // 非成功状态以带 response 的 rejection 抛出（与 axios 内置 adapter 的 settle 行为一致）
    const error = new Error('Request failed with status ' + responder.status) as Error & {
      response?: AxiosResponse
      config?: InternalAxiosRequestConfig
    }
    error.response = response
    error.config = config
    throw error
  }
  return response
}

beforeEach(() => {
  localStorage.clear()
  lastRequestConfig = null
  push.mockClear()
  currentRoutePath.mockReturnValue('/dashboard')
  responder = { status: 200, body: { code: 200, message: 'success', data: null } }
})

describe('请求拦截（P2-21）', () => {
  it('无 token 时不注入 Authorization，但始终携带 X-Trace-Id', async () => {
    await http.get('/auth/me')
    expect(lastRequestConfig?.authorization).toBeUndefined()
    expect(typeof lastRequestConfig?.traceId).toBe('string')
    expect((lastRequestConfig?.traceId as string).length).toBe(32)
  })

  it('有 token 时自动注入 Bearer Authorization', async () => {
    localStorage.setItem('workflowx_access_token', 'token-abc')
    await http.get('/auth/me')
    expect(lastRequestConfig?.authorization).toBe('Bearer token-abc')
  })

  it('business 2xx（201）正常解包，不视为错误', async () => {
    responder = { status: 201, body: { code: 201, message: 'created', data: { id: 1 } } }
    const resp = await http.post('/users', {})
    expect(resp.data.code).toBe(201)
    expect(resp.data.data).toEqual({ id: 1 })
  })
})

describe('响应拦截与 401 行为（P2-21）', () => {
  it('401 清除本地 token 并跳转 /login（携带 reason=401）', async () => {
    localStorage.setItem('workflowx_access_token', 'stale-token')
    responder = { status: 401, body: { code: 401, message: 'authentication required' } }
    await expect(http.get('/users')).rejects.toMatchObject({
      code: 401,
      message: 'authentication required',
    })
    expect(localStorage.getItem('workflowx_access_token')).toBeNull()
    expect(push).toHaveBeenCalledTimes(1)
    expect(push.mock.calls[0][0]).toMatchObject({ path: '/login', query: { reason: '401' } })
  })

  it('login 请求自身的 401 不触发跳转（防循环，交给登录页展示）', async () => {
    responder = { status: 401, body: { code: 401, message: '用户名或密码错误' } }
    await expect(http.post('/auth/login', {})).rejects.toMatchObject({
      code: 401,
      message: '用户名或密码错误',
    })
    expect(push).not.toHaveBeenCalled()
    // token 不存在时也不受影响
    expect(localStorage.getItem('workflowx_access_token')).toBeNull()
  })

  it('已处于 /login 时 401 不再次跳转（防循环）', async () => {
    currentRoutePath.mockReturnValue('/login')
    responder = { status: 401, body: { code: 401, message: 'authentication required' } }
    await expect(http.get('/users')).rejects.toBeDefined()
    expect(push).not.toHaveBeenCalled()
  })

  it('403 不清除 token、不跳转（权限错误交页面展示）', async () => {
    localStorage.setItem('workflowx_access_token', 'valid-token')
    responder = { status: 403, body: { code: 403, message: 'permission denied' } }
    await expect(http.get('/users')).rejects.toMatchObject({ code: 403, message: 'permission denied' })
    expect(localStorage.getItem('workflowx_access_token')).toBe('valid-token')
    expect(push).not.toHaveBeenCalled()
  })

  it('409/422/429 归一化透传后端安全消息', async () => {
    const cases = [
      { status: 409, message: 'username 已存在' },
      { status: 422, message: 'validation failed' },
      { status: 429, message: '登录尝试次数过多，请稍后再试' },
    ]
    for (const c of cases) {
      responder = { status: c.status, body: { code: c.status, message: c.message } }
      await expect(http.get('/users')).rejects.toMatchObject({ code: c.status, message: c.message })
    }
  })

  it('500 归一化为安全消息，不暴露 Java 堆栈', async () => {
    responder = {
      status: 500,
      body: { code: 500, message: 'internal server error', exception: 'java.lang.NullPointerException ...' },
    }
    let captured: Record<string, unknown> | null = null
    try {
      await http.get('/users')
    } catch (e) {
      captured = e as Record<string, unknown>
    }
    expect(captured).toMatchObject({ code: 500, message: 'internal server error' })
    // 归一化错误对象只含 code/message/traceId，不携带堆栈类字段
    expect(Object.keys(captured ?? {}).every((k) => ['code', 'message', 'traceId'].includes(k))).toBe(true)
  })

  it('network error（无响应）归一化为 network error', async () => {
    http.defaults.adapter = async () => {
      throw new Error('connection refused (simulated)')
    }
    await expect(http.get('/users')).rejects.toMatchObject({ code: 500, message: 'network error' })
  })
})
