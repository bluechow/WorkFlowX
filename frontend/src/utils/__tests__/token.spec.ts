import { beforeEach, describe, expect, it } from 'vitest'
import { clearToken, getToken, setToken } from '../token'

describe('token storage (P2-21 Unit)', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('初始状态无 token', () => {
    expect(getToken()).toBeNull()
  })

  it('setToken 后可读取，key 为 workflowx_access_token', () => {
    setToken('token-abc')
    expect(getToken()).toBe('token-abc')
    expect(localStorage.getItem('workflowx_access_token')).toBe('token-abc')
  })

  it('clearToken 清除后不可读', () => {
    setToken('token-abc')
    clearToken()
    expect(getToken()).toBeNull()
    expect(localStorage.getItem('workflowx_access_token')).toBeNull()
  })

  it('重复 setToken 覆盖旧值（单会话覆盖语义的本地表现）', () => {
    setToken('token-1')
    setToken('token-2')
    expect(getToken()).toBe('token-2')
  })
})
