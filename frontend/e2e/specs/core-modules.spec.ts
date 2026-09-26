import { test, expect } from '../fixtures/ui'
import { AppShell, OrganizationsPage, OrganizationDetailPage, ProjectsPage, IssuesPage, AuditPage } from '../pages'

/**
 * 核心业务模块 UI 回归（P13-07~18）: @regression
 * 数据经 API 工厂预置（真实 HTTP），浏览器负责真实 UI 行为验证。
 * 特别防回归: Phase 11 修复的列表解析缺陷（org/project/issue 列表必须真实出数）。
 */
test.describe('核心模块 UI 回归 @regression', () => {

  test('Dashboard 统计卡片与图表渲染（真实聚合数据）', async ({ adminPage, factory }) => {
    const org = await factory.createOrg()
    const project = await factory.createProject(org.id)
    await factory.createIssue(project.id, { title: 'AA 图表数据' })
    await adminPage.goto('/dashboard')
    await adminPage.getByRole('button', { name: '加载数据统计' }).click()
    await expect(adminPage.locator('[data-test="card-projects"]')).toContainText(/\d+/)
    await expect(adminPage.locator('canvas')).toHaveCount(2)
  })

  test('组织列表/详情/部门树（P11 解析缺陷防回归）', async ({ adminPage, factory }) => {
    const org = await factory.createOrg()
    await factory.createDepartment(org.id, 'AA 研发部')
    const orgsPage = new OrganizationsPage(adminPage)
    await orgsPage.goto()
    await orgsPage.expectOrgVisible(org.name)
    await orgsPage.openDetailByName(org.name)
    const detail = new OrganizationDetailPage(adminPage)
    await detail.expectDepartmentVisible('AA 研发部')
    await detail.expectOwnerRemoveDisabled()
  })

  test('项目列表（P11 解析缺陷防回归）', async ({ adminPage, factory }) => {
    const org = await factory.createOrg()
    const project = await factory.createProject(org.id)
    const projectsPage = new ProjectsPage(adminPage)
    await projectsPage.goto()
    await projectsPage.expectProjectVisible(project.name)
  })

  test('Issue 列表真实出数 + 抽屉附件 tab（P11 解析缺陷防回归）', async ({ adminPage, factory }) => {
    const org = await factory.createOrg()
    const project = await factory.createProject(org.id)
    const issue = await factory.createIssue(project.id, {
      title: 'AA 回归目标', type: 'BUG', priority: 'HIGH', severity: 'S1',
    })
    const issuesPage = new IssuesPage(adminPage)
    await issuesPage.goto(project.id)
    await issuesPage.expectIssueVisible('AA 回归目标')
    await issuesPage.openIssueDrawer(`${project.key}-${issue.issueNo}`)
    await issuesPage.gotoAttachmentTab()
  })

  test('审计页渲染业务事实（admin）', async ({ adminPage, factory }) => {
    const org = await factory.createOrg()
    const project = await factory.createProject(org.id)
    const auditPage = new AuditPage(adminPage)
    await auditPage.goto()
    await auditPage.expectFactVisible(`project:${project.id}`)
  })
})

/** 权限分层（P13-19）: 按钮隐藏 ≠ 真正权限；API 层 403 由 Phase 12 契约锁定。 */
test.describe('权限渲染与隔离 @regression @permission', () => {
  test('user1: 系统菜单全部隐藏', async ({ userPage }) => {
    const shell = new AppShell(userPage)
    await userPage.goto('/dashboard')
    await shell.expectMenuVisibility(
      [],
      ['组织管理', '项目管理', '用户管理', '用户角色', '角色管理', '权限管理', '审计日志'],
    )
  })

  test('admin: 7 项系统菜单可见', async ({ adminPage }) => {
    const shell = new AppShell(adminPage)
    await adminPage.goto('/dashboard')
    await shell.expectMenuVisibility(
      ['组织管理', '项目管理', '用户管理', '用户角色', '角色管理', '权限管理', '审计日志'],
      [],
    )
  })

  test('user1 直访审计页 → 权限提示（非空白页）', async ({ userPage }) => {
    const auditPage = new AuditPage(userPage)
    await auditPage.goto()
    await auditPage.expectNoPermissionHint()
  })
})

/** 404 兜底（P13-18）。 */
test.describe('404 @regression', () => {
  test('未知路由 → 404 页（返回首页，登录态保持）', async ({ adminPage }) => {
    await adminPage.goto('/definitely/not/a/page')
    await expect(adminPage.getByText('页面不存在或已被移动')).toBeVisible()
    await adminPage.getByRole('button', { name: '返回首页' }).click()
    await expect(adminPage).toHaveURL(/\/dashboard/)
  })
})
