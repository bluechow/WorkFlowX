import { test, expect } from '../fixtures/ui'
import { LoginPage, AppShell } from '../pages/index'

/**
 * Authentication（P13-06）: @smoke @critical
 * 单会话铁律下 seed 用户仅 setup 登录一次；错误路径用工厂用户。
 */
test.describe('Authentication @smoke @critical', () => {
  let loginPage: LoginPage

  test.beforeEach(({ publicPage }) => {
    loginPage = new LoginPage(publicPage)
  })

  test('正确登录进入 Dashboard @smoke', async ({ publicPage, factory }) => {
    // 用工厂用户真实登录（P12-04 铁律：重登 seed admin 会顶掉 setup storageState 会话）
    const user = await factory.createUser()
    await loginPage.goto()
    await loginPage.login(user.username, user.password)
    await loginPage.expectDashboard()
  })

  test('错误密码 → 统一 401 提示（不泄露存在性）', async ({ publicPage, factory }) => {
    const user = await factory.createUser()
    await loginPage.goto()
    await loginPage.login(user.username, 'WrongPass@999')
    await loginPage.expectError('用户名或密码错误')
    await expect(publicPage).toHaveURL(/\/login/)
  })

  test('未登录访问受保护路由 → /login?redirect', async ({ publicPage }) => {
    await publicPage.goto('/system/projects')
    await expect(publicPage).toHaveURL(/\/login\?redirect=/)
  })

  test('已登录访问 /login → 回 /dashboard', async ({ adminPage }) => {
    await adminPage.goto('/login')
    await expect(adminPage).toHaveURL(/\/dashboard/)
  })

  test('刷新后保持登录', async ({ adminPage }) => {
    await adminPage.goto('/dashboard')
    await adminPage.reload()
    await expect(adminPage).toHaveURL(/\/dashboard/)
    await expect(adminPage.getByRole('button', { name: '退出登录' }).last()).toBeVisible()
  })

  test('登出 → /login，且保护路由重新拦截', async ({ publicPage, factory }) => {
    // 工厂用户真实 UI 登录→登出（P13-06 铁律：seed 会话仅 setup 登录一次，登出用例不得销毁 seed 会话）
    const user = await factory.createUser()
    await loginPage.goto()
    await loginPage.login(user.username, user.password)
    await loginPage.expectDashboard()
    const shell = new AppShell(publicPage)
    await shell.logout()
    await publicPage.goto('/system/roles')
    await expect(publicPage).toHaveURL(/\/login/)
  })
})
