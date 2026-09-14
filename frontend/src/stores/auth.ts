import { defineStore } from 'pinia'
import { fetchMe, fetchMyPermissions, login as loginApi, logout as logoutApi } from '@/api/auth'
import { clearToken, getToken, setToken } from '@/utils/token'
import type { ApiError, LoginRequest, LoginResponse, UserVO } from '@/types/api'

type MeStatus = 'idle' | 'loading' | 'success' | 'failed'

interface AuthState {
  accessToken: string | null
  tokenType: string
  expiresIn: number
  userId: number | null
  username: string | null
  roles: string[]
  /** 当前用户资料: 唯一来源为 GET /auth/me（数据库实时数据），登录响应仅作过渡展示 */
  currentUser: UserVO | null
  /** /me 校验状态: idle=未校验（刷新恢复中） success=已确认 failed=已失效 */
  meStatus: MeStatus
  /** 实时权限编码（P3-05，/auth/me/permissions）：仅用于按钮级 UX 展示，非安全边界 */
  permissionCodes: string[]
}

/**
 * 认证状态 store（P2-19，P3-05 扩展权限态）。
 * - token 持久化于 localStorage（workflowx_access_token，见 utils/token.ts 安全说明）
 * - currentUser 仅来自 /me；login 响应中的 username/roles 只作为过渡值
 * - permissionCodes 来自 /auth/me/permissions 实时查询，仅供 UX；后端 authority 才是安全边界
 * - logout/clearAuth 不保存、不接收任何密码信息
 */
export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    accessToken: getToken(),
    tokenType: 'Bearer',
    expiresIn: 0,
    userId: null,
    username: null,
    roles: [],
    currentUser: null,
    meStatus: 'idle',
    permissionCodes: [],
  }),
  getters: {
    /** token 存在仅代表"曾登录"；有效性必须经 fetchMe 确认 */
    isAuthenticated: (state) => !!state.accessToken,
  },
  actions: {
    async login(request: LoginRequest): Promise<LoginResponse> {
      const response = await loginApi(request)
      this.accessToken = response.accessToken
      this.tokenType = response.tokenType
      this.expiresIn = response.expiresIn
      this.userId = response.userId
      this.username = response.username
      this.roles = response.roles
      setToken(response.accessToken)
      // 最终用户资料以 /me 为准
      await this.fetchMe()
      return response
    },
    async fetchMe(): Promise<void> {
      if (!this.accessToken) {
        throw { code: 401, message: 'not authenticated' } satisfies ApiError
      }
      this.meStatus = 'loading'
      try {
        this.currentUser = await fetchMe()
        this.meStatus = 'success'
        // 权限码加载失败不阻塞认证（UX 数据，后端 authority 兜底）
        this.permissionCodes = await fetchMyPermissions().catch(() => this.permissionCodes)
      } catch (e) {
        // token 无效/过期/会话被覆盖/用户被禁用: 清理本地状态并上抛（守卫/拦截器负责回登录页）
        this.meStatus = 'failed'
        this.clearAuth()
        throw e
      }
    },
    /** 按钮级 UX 判断（P3-05）；安全边界在后端 authority（Master Prompt §7） */
    hasPermission(code: string): boolean {
      return this.permissionCodes.includes(code)
    },
    /** 登出: 后端调用尽力而为（401 等视为已失效），本地状态无条件清理 */
    async logout(): Promise<void> {
      try {
        await logoutApi()
      } catch {
        // 会话可能已被覆盖/失效; 用户必须能回到登录页
      }
      this.clearAuth()
    },
    clearAuth(): void {
      this.accessToken = null
      this.userId = null
      this.username = null
      this.roles = []
      this.currentUser = null
      this.meStatus = 'idle'
      this.permissionCodes = []
      clearToken()
    },
  },
})
