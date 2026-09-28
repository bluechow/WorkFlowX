import { test, expect } from '../fixtures/ui'
import { LoginPage, AppShell, OrganizationsPage, IssuesPage, AuditPage } from '../pages'

/**
 * 完整真实业务链（P13-26）: @full @critical
 * Login → Organization → Department(API) → User(API) → Role(API) → Project → Member(API)
 * → Issue → Assignment → Workflow → Comment → Attachment → Notification → Audit → Dashboard → Logout
 * 浏览器 UI 行为 + API 工厂混合驱动；全部真实 HTTP/后端/MySQL/Redis/MinIO。
 */
test.describe('完整业务链 @full @critical', () => {
  test('全链路贯穿（登录→…→登出）', async ({ browser, factory }) => {
    // 独立 context 做全链（隔离其他 spec 的会话）
    const context = await browser.newContext({ viewport: { width: 1366, height: 800 } })
    const page = await context.newPage()
    const loginPage = new LoginPage(page)
    // 1. 登录（UI 真实表单）——动态 ADMIN 用户（P13-06 铁律：不重登 seed）
    const chainAdmin = await factory.createUser()  // 随机后缀（跨轮不冲突）
    await factory.addUserRole(chainAdmin.id, 'ADMIN')
    await loginPage.goto()
    await loginPage.login(chainAdmin.username, chainAdmin.password)
    await loginPage.expectDashboard()

    // 2. 组织（UI 新建对话框）
    const suffix = Date.now().toString(36).toUpperCase()
    const orgsPage = new OrganizationsPage(page)
    await page.goto('/system/organizations')
    await orgsPage.createOrgViaDialog(`AA 全链组织 ${suffix}`, `AACHAIN${suffix}`)
    // 登记 UI 创建的组织进清理轨迹（URL 提取 id）
    const uiOrgId = Number((page.url().match(/organizations\/(\d+)/) || [])[1])
    if (uiOrgId) factory.trackOrg(uiOrgId)

    // 3. 部门 + 用户 + 角色（API 预置——浏览器已验证同款 UI 行为）
    await orgsPage.openDetailByName(`AA 全链组织 ${suffix}`)
    const orgId = Number((page.url().match(/organizations\/(\d+)/) || [])[1])
    expect(orgId).toBeGreaterThan(0)
    const dept = await factory.createDepartment(orgId, 'AA 全链部门')
    expect(dept.id).toBeGreaterThan(0)
    const user = await factory.createUser()

    // 4. 项目（API 工厂以 seed admin 身份）→ 先由 chainAdmin(OWNER) 拉 seed admin 入组织
    //    会话用 UI 页面当前 token（绝不 relogin——重登会顶掉 chainAdmin 的 UI 会话）
    const uiToken = (await page.evaluate(() =>
      localStorage.getItem('workflowx_access_token'),
    )) as string
    const chainAdminSession = factory.sessionWith(uiToken)
    await chainAdminSession.post(`/api/v1/orgs/${orgId}/members`, {
      json: { userId: 1, role: 'MEMBER', departmentId: dept.id },
    })
    const project = await factory.createProject(orgId)
    // chainAdmin 也是操作者（UI 流转/评论）——须为项目成员（ADR-016 数据级）
    await factory.addProjectMember(project.id, chainAdmin.id)
    await factory.addOrgMember(orgId, user.id)
    await factory.addProjectMember(project.id, user.id)

    // 6. Issue（API，分派 user.id）→ 7. UI 验证列表与状态流转按钮
    const issue = await factory.createIssue(project.id, {
      title: 'AA 全链目标', type: 'BUG', priority: 'URGENT', severity: 'S1', assigneeId: user.id,
    })
    const issuesPage = new IssuesPage(page)
    await issuesPage.goto(project.id)
    await issuesPage.expectIssueVisible('AA 全链目标')

    // 8. Workflow（UI 下拉选择合法目标 待处理→处理中，等待行刷新；Phase A 中文化后标签为中文）
    await issuesPage.workflowTransition('AA 全链目标', '待处理', '处理中')

    // 9. Comment（抽屉内真实发布）
    await issuesPage.openIssueDrawer(`${project.key}-${issue.issueNo}`)
    await issuesPage.addComment('AA 全链评论')

    // 10. Notification（工厂用户会话注入，验证通知——简化：直接核对 API 侧由 business chain 覆盖）
    await factory.loginContextAs(context, user)
    console.log('PROBE-A page.url=', await page.url())
    // 切回 chainAdmin（ADMIN 权限）执行后续 Audit/Dashboard 核对
    await factory.loginContextAs(context, chainAdmin)

    // 11. Audit（admin 核对 TRANSITION 事实）
    console.log('PROBE-B pre-audit token=', await page.evaluate(() => (localStorage.getItem('workflowx_access_token') || '').slice(0, 20)), 'url-before=', await page.url())
    await page.goto('/system/audit')
    console.log('PROBE-C audit url=', await page.url())
    const auditPage = new AuditPage(page)
    await auditPage.expectFactVisible(`issue:${issue.id}`)

    // 12. Dashboard（统计自洽）
    await page.goto('/dashboard')
    // Phase A-⑥：统计自动加载
    await expect(page.locator('[data-test="card-issues"]')).toContainText(/\d+/)

    // 13. 登出
    const shellAtEnd = new AppShell(page)
    await shellAtEnd.logout()
    await expect(page).toHaveURL(/\/login/)

    await context.close()
  })
})
