import { test, expect } from '../fixtures/ui'
import { LoginPage } from '../pages'

/**
 * 测试用例库 UI 回归（Phase 20）: @security @regression
 * 数据经 API 工厂预置；浏览器验证目录树 + 用例表格 + 创建流。
 * 动态 ADMIN 用户（P13-06 铁律）。
 */
test.describe('测试用例库 @regression', () => {
  test('登录 → 用例库 → 新建目录 → 新建用例 → 列表渲染', async ({ page, factory }) => {
    const deployAdmin = await factory.createUser()
    await factory.addUserRole(deployAdmin.id, 'ADMIN')
    const org = await factory.createOrg()
    const project = await factory.createProject(org.id)
    // 用例库读写均要求项目成员（ADR-022 数据级，对齐 chain/notification spec 先例）：
    // 工厂以 seed admin 建项目（owner=1），须把页面用户拉入组织与项目
    await factory.addOrgMember(org.id, deployAdmin.id)
    await factory.addProjectMember(project.id, deployAdmin.id)

    const loginPage = new LoginPage(page)
    await loginPage.goto()
    await loginPage.login(deployAdmin.username, deployAdmin.password)
    await loginPage.expectDashboard()

    await page.goto(`/system/projects/${project.id}/testcases`)
    await expect(page.getByRole('heading', { name: '测试用例库' })).toBeVisible()

    // 新建根目录
    await page.getByRole('button', { name: '新建根目录' }).click()
    const dirDialog = page.locator('.el-dialog:visible')
    await dirDialog.getByPlaceholder('目录名').fill('登录模块')
    await dirDialog.getByRole('button', { name: '保存' }).click()
    await expect(page.getByText('登录模块').first()).toBeVisible()

    // 新建用例
    await page.getByRole('button', { name: '新建用例' }).click()
    const caseDialog = page.locator('.el-dialog:visible')
    await caseDialog.getByPlaceholder('用例标题').fill('登录成功用例')
    await caseDialog.getByRole('button', { name: '保存' }).click()
    await expect(page.locator('tbody tr', { hasText: '登录成功用例' }).first()).toBeVisible()
    await expect(page.locator('tbody tr', { hasText: '登录成功用例' })).toContainText('TC-1')
  })
})
