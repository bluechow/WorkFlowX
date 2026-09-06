const TOKEN_KEY = 'workflowx_access_token'

/**
 * 访问令牌的浏览器持久化（P2-19）。
 * 方案: localStorage——当前架构无 Refresh Token，需要刷新页面后维持登录。
 * 安全说明: localStorage 非高安全等级存储，当前阶段（学习项目、无富文本 XSS 入口）接受该方案，
 * 决策记录见 AI_DECISIONS.md。禁止向此处写入密码/密码哈希/任何 secret。
 */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}
