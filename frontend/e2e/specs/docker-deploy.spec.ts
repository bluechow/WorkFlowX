import { test, expect } from '../fixtures/ui'
import { AppShell, LoginPage } from '../pages'

/**
 * Docker 部署 UI 回归（Phase 16; P16-30）: @security @regression
 * 测试目标 = Docker 前端（Nginx 入口），后端经 /api 反代。
 * 运行: PLAYWRIGHT_BASEURL=http://localhost:8081 npx playwright test -g "Docker 部署"
 */
test.describe('Docker 部署 UI 回归', () => {
  test.use({ storageState: { cookies: [], origins: [] } }) // 未认证起点，走真实 UI 登录

  let shell: AppShell
  let loginPage: LoginPage

  test.beforeEach(({ page }) => {
    shell = new AppShell(page)
    loginPage = new LoginPage(page)
  })

  test('登录 → 菜单 → 各模块 → 登出（Docker Nginx 链路）', async ({ page, factory }) => {
    // P13-06 铁律: 动态 ADMIN 用户（seed admin UI 登录会顶掉 setup storageState，
    // 导致后续依赖 admin.json 的用例连锁 401）
    const deployAdmin = await factory.createUser()
    await factory.addUserRole(deployAdmin.id, 'ADMIN')
    await loginPage.goto()
    await loginPage.login(deployAdmin.username, deployAdmin.password)
    await loginPage.expectDashboard()
    await shell.expectMenuVisibility(
      ['组织管理', '项目管理', '用户管理', '用户角色', '角色管理', '权限管理', '审计日志'], [])

    await page.goto('/system/organizations')
    await expect(page.getByRole('heading', { name: '组织管理' })).toBeVisible()
    await page.goto('/system/projects')
    await expect(page.getByRole('heading', { name: '项目管理' })).toBeVisible()
    await page.goto('/system/audit')
    await expect(page.getByRole('heading', { name: '审计日志' })).toBeVisible()

    await shell.logout()
    await expect(page).toHaveURL(/\/login/)
  })
})
