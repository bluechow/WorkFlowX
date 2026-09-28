import { test, expect } from '../fixtures/ui'
import { AppShell, AuditPage, IssuesPage, LoginPage } from '../pages'

/**
 * 前端安全（Phase 15; P15-20）: @security @regression
 * 验证前端权限控制只是 UX 层 + XSS 渲染不执行 + token 失效行为。
 * 单会话铁律: 不重登 seed 用户。
 */
test.describe('前端安全 @security @regression', () => {
  test('XSS payload 存储后 UI 以纯文本渲染，不执行脚本', async ({ adminPage, factory }) => {
    const xssTitle = 'SECURITY <script>alert(1)</script> title'
    const xssComment = 'SECURITY <img src=x onerror=document.body.dataset.xss=1> comment'
    const org = await factory.createOrg()
    const project = await factory.createProject(org.id)
    await factory.createIssue(project.id, { title: xssTitle })
    const issuesPage = new IssuesPage(adminPage)
    await issuesPage.goto(project.id)
    // 真实文本节点渲染（非 HTML 注入——无 alert 执行、无注入的 script/img 元素）
    await expect(adminPage.getByText(xssTitle).first()).toBeVisible()
    await expect(adminPage.locator('script', { hasText: 'alert(1)' })).toHaveCount(0)
    await expect(adminPage.locator('img[src="x"]')).toHaveCount(0)

    // 抽屉评论同样纯文本渲染
    await issuesPage.openIssueDrawer(/-\d+/.exec(await adminPage.locator('tbody tr').first().textContent())?.[0] ?? '')
    await issuesPage.addComment(xssComment)
    await expect(adminPage.getByText(xssComment).first()).toBeVisible()
    await expect(adminPage.locator('img[src="x"]')).toHaveCount(0)
    await expect(adminPage.locator('script', { hasText: 'alert' })).toHaveCount(0)
  })

  test('ADMIN 刷新后系统菜单保持（P13 缺陷防回归——权限码驱动）', async ({ adminPage }) => {
    await adminPage.goto('/dashboard')
    await adminPage.reload()
    const shell = new AppShell(adminPage)
    await shell.expectMenuVisibility(
      ['组织管理', '项目管理', '用户管理', '用户角色', '角色管理', '权限管理', '审计日志'], [])
  })

  test('user1 直访审计/用户管理路由 → 权限提示而非数据', async ({ userPage }) => {
    const auditPage = new AuditPage(userPage)
    await auditPage.goto()
    await auditPage.expectNoPermissionHint()
    await userPage.goto('/system/users')
    await expect(userPage.locator('[data-test="no-permission"]')).toBeVisible()
  })

  test('后端会话失效（storageState token 被顶）→ 401 自动回登录', async ({ browser, factory }) => {
    // 模拟单会话覆盖: 同一动态用户在新 context 登录 → 旧 context token 失效
    const user = await factory.createUser()
    const oldContext = await browser.newContext()
    const oldPage = await oldContext.newPage()
    const loginPage = new LoginPage(oldPage)
    await loginPage.goto()
    await loginPage.login(user.username, user.password)
    await loginPage.expectDashboard()

    // 新登录（覆盖 auth:session）
    const newContext = await browser.newContext()
    const newPage = await newContext.newPage()
    const newLogin = new LoginPage(newPage)
    await newLogin.goto()
    await newLogin.login(user.username, user.password)
    await newLogin.expectDashboard()

    // 旧 context 刷新 → 401 → 自动回 /login
    await oldPage.reload()
    await oldPage.waitForTimeout(1500)
    await expect(oldPage).toHaveURL(/\/login/)

    await oldContext.close()
    await newContext.close()
  })

  test('localStorage 不存密码，仅存 token', async ({ adminPage }) => {
    await adminPage.goto('/dashboard')
    const stored = await adminPage.evaluate(() => JSON.stringify(localStorage))
    expect(stored).not.toContain('Admin@123456')
    expect(stored).not.toContain('Member@123456')
    expect(stored).not.toContain('password')
  })
})
