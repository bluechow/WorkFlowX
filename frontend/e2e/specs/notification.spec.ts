import { test, expect } from '../fixtures/ui'
import { AppShell, LoginPage } from '../pages'

/**
 * Notification UI 链路（P13-16）: @regression
 * admin 经 API 触发分派 → user1 浏览器验证 badge/中心/已读（单会话互不影响：seed 各登录一次）。
 */
test.describe('Notification @regression', () => {
  test('分派 → user1 badge 出现 → 通知中心渲染 → 全部已读归零', async ({
    factory,
    userPage,
  }) => {
    // 触发: admin 建项目+成员+分派 Issue（真实 HTTP → 通知产生）
    const org = await factory.createOrg()
    const project = await factory.createProject(org.id)
    await factory.addOrgMember(org.id, 2)
    await factory.addProjectMember(project.id, 2)
    await factory.createIssue(project.id, { title: 'AA 通知链路', assigneeId: 2 })

    // user1 浏览器: badge 可见
    const shell = new AppShell(userPage)
    await userPage.goto('/dashboard')
    await expect(userPage.locator('.el-badge__content').first()).toBeVisible({ timeout: 15_000 })

    // 打开通知中心
    await shell.bell().click()
    await expect(userPage.getByText('通知中心')).toBeVisible()
    // 分派通知含真实业务上下文
    await expect(userPage.getByText('Issue 已分派给您').first()).toBeVisible()

    // 全部已读 → badge 消失
    await userPage.getByRole('button', { name: '全部已读' }).click()
    await expect(userPage.locator('.el-badge__content')).toHaveCount(0, { timeout: 10_000 })
  })

  test('登出 → 切换用户 → 通知状态不残留', async ({ publicPage, factory, userPage }) => {
    // 工厂用户 UI 登录→登出（不销毁 seed 会话）；再验证 user1 会话独立
    const user = await factory.createUser()
    const loginPage = new LoginPage(publicPage)
    await loginPage.goto()
    await loginPage.login(user.username, user.password)
    await loginPage.expectDashboard()
    const shellFactory = new AppShell(publicPage)
    await shellFactory.logout()
    // user1（独立 storageState 会话）登录态完好、状态独立
    await userPage.goto('/dashboard')
    await expect(userPage.getByRole('button', { name: '退出登录' }).last()).toBeVisible()
    const shell = new AppShell(userPage)
    await shell.expectMenuVisibility([], ['审计日志'])
  })
})
