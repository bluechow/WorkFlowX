import { expect, type Page } from '@playwright/test'

/**
 * 应用外壳 Page Object（P13-02）：顶栏菜单/通知铃铛/用户区/登出。
 * 菜单按权限渲染（隐藏菜单 ≠ 后端授权，后端仍是最终权限边界）。
 */
export class AppShell {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  menu(name: string) {
    return this.page.getByRole('menuitem', { name })
  }

  bell() {
    return this.page.getByRole('button', { name: '通知中心' })
  }

  async logout(): Promise<void> {
    await this.page.getByRole('button', { name: '退出登录' }).last().click()
    await expect(this.page).toHaveURL(/\/login/)
  }

  async expectMenuVisibility(expected: string[], forbidden: string[]): Promise<void> {
    for (const name of expected) {
      await expect(this.menu(name)).toBeVisible()
    }
    for (const name of forbidden) {
      await expect(this.menu(name)).toHaveCount(0)
    }
  }
}

/** 组织管理页（P13-10）：列表/新建/详情入口。 */
export class OrganizationsPage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  async goto(): Promise<void> {
    await this.page.goto('/system/organizations')
    await this.page.getByRole('heading', { name: '组织管理' }).waitFor()
  }

  async openDetailByName(name: string): Promise<void> {
    const row = this.page.locator('tbody tr', { hasText: name }).first()
    await row.getByRole('button', { name: '管理' }).click()
    await this.page.waitForURL(/\/system\/organizations\/\d+/)
  }

  async expectOrgVisible(name: string): Promise<void> {
    await expect(this.page.locator('tbody tr', { hasText: name }).first()).toBeVisible()
  }

  /** UI 真实新建组织（对话框），等待列表出现新行 */
  async createOrgViaDialog(name: string, code: string): Promise<void> {
    await this.page.getByRole('button', { name: '新建组织' }).click()
    const dialog = this.page.locator('.el-dialog')
    await dialog.getByPlaceholder('组织名称').fill(name)
    await dialog.getByPlaceholder('如 ACME_HQ').fill(code)
    await dialog.getByRole('button', { name: '保存' }).click()
    await this.expectOrgVisible(name)
  }
}

/** 组织详情页（部门树/成员）。 */
export class OrganizationDetailPage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  async expectDepartmentVisible(name: string): Promise<void> {
    await expect(this.page.getByRole('heading', { name: '部门' })).toBeVisible()
    await expect(this.page.getByText(name).first()).toBeVisible()
  }

  async expectOwnerRemoveDisabled(): Promise<void> {
    // 等成员表加载；定位含"移除"按钮的 OWNER 成员行
    // （页面 el-descriptions 的信息行也含 "OWNER" 文本——须用 has: 按钮存在来消歧）
    await this.page.getByRole('heading', { name: '成员' }).waitFor()
    const ownerRow = this.page
      .locator('tbody tr', { hasText: 'OWNER' })
      .filter({ has: this.page.getByRole('button', { name: '移除' }) })
      .first()
    await expect(ownerRow.getByRole('button', { name: '移除' })).toBeDisabled()
  }
}

/** 项目页（P13-11）。 */
export class ProjectsPage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  async goto(): Promise<void> {
    await this.page.goto('/system/projects')
    await this.page.getByRole('heading', { name: '项目管理' }).waitFor()
  }

  async expectProjectVisible(name: string): Promise<void> {
    await expect(this.page.locator('tbody tr', { hasText: name }).first()).toBeVisible()
  }
}

/** Issues 页 + 业务抽屉（P13-12/13/14/15）。 */
export class IssuesPage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  async goto(projectId: number): Promise<void> {
    await this.page.goto(`/system/projects/${projectId}/issues`)
    await this.page.getByRole('heading', { name: /Issues —/ }).waitFor()
  }

  async openIssueDrawer(issueNoLabel: string): Promise<void> {
    await this.page.getByRole('button', { name: issueNoLabel }).first().click()
    // Phase A-③ 抽屉：评论/附件为常显分区（无 tab）
    await this.page.locator('.el-drawer').getByText('评论', { exact: true }).waitFor()
  }

  async expectIssueVisible(title: string): Promise<void> {
    await expect(this.page.locator('tbody tr', { hasText: title }).first()).toBeVisible()
  }

  async addComment(content: string): Promise<void> {
    const editor = this.page.getByPlaceholder('写下评论…')
    await editor.fill(content)
    await this.page.getByRole('button', { name: '发布评论' }).click()
    await expect(this.page.getByText(content).first()).toBeVisible()
  }

  async gotoAttachmentTab(): Promise<void> {
    // Phase A-③ 抽屉：附件区常显，等待其上传按钮即可
    await this.page.getByRole('button', { name: '上传附件' }).waitFor({ state: 'visible' })
  }

  async uploadAttachment(filePath: string): Promise<void> {
    const chooserPromise = this.page.waitForEvent('filechooser')
    await this.page.getByRole('button', { name: '上传附件' }).click()
    const chooser = await chooserPromise
    await chooser.setFiles(filePath)
  }

  async expectAttachmentVisible(fileName: string): Promise<void> {
    await expect(this.page.locator('.attachments-panel__item', { hasText: fileName }).first()).toBeVisible()
  }

  /** 行内状态下拉（按当前状态文本区分于经办人下拉） */
  statusSelectInRow(title: string, currentStatus: string) {
    const row = this.page.locator('tbody tr', { hasText: title }).first()
    return row.locator('.el-select').filter({ hasText: currentStatus })
  }

  /** UI 真实状态流转：点状态下拉 → 选目标 → 等行刷新为目标状态 */
  async workflowTransition(title: string, from: string, to: string): Promise<void> {
    await this.statusSelectInRow(title, from).click()
    const option = this.page.getByRole('option', { name: to })
    await option.waitFor({ state: 'visible' })
    await option.click()
    await expect(
      this.page.locator('tbody tr', { hasText: title }).first(),
    ).toContainText(to, { timeout: 10_000 })
  }
}

/** 审计页（P13-17）。 */
export class AuditPage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  async goto(): Promise<void> {
    await this.page.goto('/system/audit')
    await this.page.getByRole('heading', { name: '审计日志' }).waitFor()
  }

  async expectNoPermissionHint(): Promise<void> {
    await expect(this.page.locator('[data-test="no-permission"]')).toBeVisible()
  }

  async expectFactVisible(text: string): Promise<void> {
    await expect(this.page.locator('tbody tr', { hasText: text }).first()).toBeVisible()
  }
}

/** 登录页 Page Object（P13-06）。定位器优先 role/placeholder（LoginView 语义明确）。 */
export class LoginPage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  async goto(): Promise<void> {
    await this.page.goto('/login')
    await this.page.getByRole('textbox', { name: '用户名' }).waitFor({ state: 'visible' })
  }

  async login(username: string, password: string): Promise<void> {
    await this.page.getByRole('textbox', { name: '用户名' }).fill(username)
    await this.page.getByRole('textbox', { name: '密码' }).fill(password)
    await this.page.getByRole('button', { name: '登 录' }).click()
  }

  async expectDashboard(): Promise<void> {
    await expect(this.page).toHaveURL(/\/dashboard/)
    // Phase A-⑥ 起首页为问候+统计+待办；问候语含昵称，这里断言稳定副标语
    await expect(this.page.getByText('今天也要顺利交付')).toBeVisible()
  }

  async expectError(message: string): Promise<void> {
    await expect(this.page.getByText(message).first()).toBeVisible()
  }
}
