import type { APIRequestContext, Page } from '@playwright/test'

/**
 * API 数据工厂（P13/Phase 12 思路复用）：
 - 全部经真实 HTTP 创建；命名空间 AA（组织/项目）与 api_test_（用户）
 - cleanup 级联（org 删除级联 project/issue/comment）
 - 用于 UI 测试的数据准备（setup via API）与 UI 行为验证分层
 */
export class ApiFactory {
  private token: string
  private orgs: number[] = []
  private projects: number[] = []
  private users: { id: number; username: string; password: string }[] = []
  private roles: number[] = []

  constructor(private request: APIRequestContext) {
    // 工厂以 admin 身份建数据（token 在首次使用时懒加载——单会话仅登录一次）
    this.token = ''
  }

  async ensureToken(): Promise<string> {
    if (!this.token) {
      // P13-06 铁律: 复用 setup project 生成的 storageState token（禁止再次登录 seed admin
      // ——单会话策略下任何重复登录都会顶掉 setup 会话，导致后续 adminPage 全 401）
      const fs = await import('node:fs')
      const path = await import('node:path')
      const stateFile = path.resolve('test-results/.auth/admin.json')
      const state = JSON.parse(fs.readFileSync(stateFile, 'utf-8'))
      this.token = state.origins[0].localStorage[0].value
    }
    return this.token
  }

  private async api(method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE', url: string, data?: unknown) {
    const token = await this.ensureToken()
    return this.request.fetch(`http://localhost:8080${url}`, {
      method,
      headers: { Authorization: `Bearer ${token}` },
      data,
    })
  }

  async createOrg(): Promise<{ id: number; name: string; code: string }> {
    const suffix = Date.now().toString(36).toUpperCase() + Math.floor(Math.random() * 1e4)
    const resp = await this.api('POST', '/api/v1/orgs', {
      name: `AA 组织 ${suffix}`,
      code: `AAORG${suffix}`,
      description: null,
    })
    if (resp.status() !== 201) {
      throw new Error(`factory createOrg 失败: ${resp.status()} ${await resp.text()}`)
    }
    const org = (await resp.json()).data
    this.orgs.push(org.id)
    return org
  }

  /** 登记 UI 侧创建的资源 id（如 OrganizationsPage 对话框建的组织），纳入 cleanup 轨迹 */
  trackOrg(orgId: number): void {
    this.orgs.push(orgId)
  }

  async addOrgMember(orgId: number, userId: number, role = 'MEMBER'): Promise<void> {
    await this.api('POST', `/api/v1/orgs/${orgId}/members`, {
      userId,
      role,
      departmentId: null,
    })
  }

  async createDepartment(orgId: number, name: string, parentId: number | null = null) {
    const suffix = Date.now().toString(36).toUpperCase()
    const resp = await this.api('POST', `/api/v1/orgs/${orgId}/departments`, {
      name,
      code: `AAD${suffix}`,
      parentId,
    })
    return (await resp.json()).data
  }

  async createProject(orgId: number): Promise<{ id: number; key: string; name: string }> {
    const suffix = Date.now().toString(36).toUpperCase()
    const resp = await this.api('POST', '/api/v1/projects', {
      name: `AA 项目 ${suffix}`,
      key: `AAP${suffix}`,
      orgId,
      description: null,
    })
    if (resp.status() !== 201) {
      throw new Error(`factory createProject 失败: ${resp.status()} ${await resp.text()}`)
    }
    const project = (await resp.json()).data
    this.projects.push(project.id)
    return project
  }

  async addProjectMember(projectId: number, userId: number, role = 'MEMBER'): Promise<void> {
    await this.api('POST', `/api/v1/projects/${projectId}/members`, {
      userId,
      role,
    })
  }

  async createIssue(projectId: number, overrides: Record<string, unknown> = {}) {
    const payload = {
      title: `AA Issue ${Date.now().toString(36)}`,
      description: 'ui-e2e',
      type: 'TASK',
      priority: 'MEDIUM',
      severity: null,
      assigneeId: null,
      ...overrides,
    }
    const resp = await this.api('POST', `/api/v1/projects/${projectId}/issues`, payload)
    return (await resp.json()).data
  }

  async createUser(suffix?: string): Promise<{ id: number; username: string; password: string }> {
    const sfx = suffix ?? Date.now().toString(36)
    const username = `api_test_${sfx}`
    const resp = await this.api('POST', '/api/v1/users', {
      username,
      email: `${username}@test.local`,
      password: 'ApiTest@123',
      nickname: 'ui-factory',
    })
    if (resp.status() !== 201) {
      throw new Error(`factory createUser 失败: ${resp.status()} ${await resp.text()}`)
    }
    const data = (await resp.json()).data
    const user = { id: data.id, username, password: 'ApiTest@123' }
    this.users.push(user)
    return user
  }

  /** 用页面现有 token 构造 API 会话（**绝不 relogin**——重登会顶掉该用户当前 UI 会话） */
  sessionWith(token: string) {
    return {
      post: (url: string, opts: { json: unknown }) =>
        this.request.fetch(`http://localhost:8080${url}`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${token}` },
          data: opts.json,
        }),
      close: async () => undefined,
    }
  }

  /** 绑定系统角色（如 ADMIN）到工厂用户 */
  async addUserRole(userId: number, roleCode: string): Promise<void> {
    await this.api('POST', `/api/v1/users/${userId}/roles`, { roleCode })
  }

  /** seed user1 的固定 id（已知系统结构，非业务数据 ID） */
  seedMemberId(): number {
    return 2
  }

  /** 在指定 context 注入工厂用户登录态（API 登录 → localStorage；单会话安全） */
  async loginContextAs(context: {
    newPage(): Promise<{ goto(url: string): Promise<void>; evaluate(fn: (t: string) => void, arg: string): Promise<void> }>
  }, user: { username: string; password: string }): Promise<void> {
    const resp = await this.request.post('/api/v1/auth/login', {
      data: { username: user.username, password: user.password },
    })
    const token = (await resp.json()).data.accessToken
    const page = await context.newPage()
    await page.goto('http://localhost:5173/login')
    await page.evaluate((t) => localStorage.setItem('workflowx_access_token', t), token)
  }

  async cleanup(): Promise<void> {
    let orgDeleteFailed = false
    for (const roleId of this.roles) {
      await this.api('DELETE', `/api/v1/roles/${roleId}`).catch(() => undefined)
    }
    for (const orgId of this.orgs) {
      // 组织删除仅 OWNER 可操作（ADR-013）——UI 建组织的 owner 可能是动态用户而非 seed admin：
      // 依次以 admin 与各动态用户身份尝试（P13-30 可追溯清理；删除失败必须可见）
      let deleted = false
      let lastStatus = 0
      for (const identity of await this.identities()) {
        const resp = await this.request.fetch(`http://localhost:8080/api/v1/orgs/${orgId}`, {
          method: 'DELETE',
          headers: { Authorization: `Bearer ${identity.token}` },
        })
        if (resp.status() === 200) {
          deleted = true
          break
        }
        lastStatus = resp.status()
      }
      if (!deleted) {
        console.warn(`WARNING: factory cleanup 未能删除组织 ${orgId}（最后状态 ${lastStatus}）`)
        orgDeleteFailed = true
      }
    }
    for (const user of this.users) {
      // 组织删除失败时保留动态用户（owner 仍在，重跑 cleanup 可再试删）——
      // 避免产生 owner 缺失的"无主组织"化石（仅 OWNER 可删，届时 API 无法清理）
      const resp = await this.api('DELETE', `/api/v1/users/${user.id}`)
      if (resp.status() >= 300 && !orgDeleteFailed) {
        console.warn(`WARNING: cleanup 删除用户 ${user.username} 返回 ${resp.status()}`)
      }
    }
    this.orgs = []
    this.projects = []
    this.roles = []
    if (!orgDeleteFailed) this.users = []
  }

  /** 可用清理身份：seed admin token + 各动态用户登录 token */
  private async identities(): Promise<{ token: string }[]> {
    const identities: { token: string }[] = [{ token: await this.ensureToken() }]
    for (const user of this.users) {
      const resp = await this.request.post('/api/v1/auth/login', {
        data: { username: user.username, password: user.password },
      })
      if (resp.status() === 200) {
        identities.push({ token: (await resp.json()).data.accessToken })
      }
    }
    return identities
  }
}
