import { test as base, expect, type Page } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'
import { LoginPage } from '../pages/index'
import { ApiFactory } from '../utils/api-factory'

const AUTH_DIR = path.resolve('test-results/.auth')

/**
 * UI 测试 fixtures（P13-03）。
 * - adminPage/userPage: 注入 storageState 的已认证页面（seed 用户各登录一次，绝不重登）
 * - factory: API 数据工厂（动态用户/组织/项目/Issue——命名空间 AA，cleanup 级联）
 * - publicPage: 未认证页面
 */
export type WorkerFixtures = {
  adminPage: Page
  userPage: Page
  publicPage: Page
  factory: ApiFactory
}

export const test = base.extend<WorkerFixtures>({
  adminPage: async ({ browser }, use) => {
    const context = await browser.newContext({
      storageState: path.join(AUTH_DIR, 'admin.json'),
      viewport: { width: 1366, height: 800 },
    })
    const page = await context.newPage()
    await use(page)
    await context.close()
  },
  userPage: async ({ browser }, use) => {
    const context = await browser.newContext({
      storageState: path.join(AUTH_DIR, 'user.json'),
      viewport: { width: 1366, height: 800 },
    })
    const page = await context.newPage()
    await use(page)
    await context.close()
  },
  publicPage: async ({ browser }, use) => {
    const context = await browser.newContext({ viewport: { width: 1366, height: 800 } })
    const page = await context.newPage()
    await use(page)
    await context.close()
  },
  factory: async ({ playwright }, use) => {
    const request = await playwright.request.newContext({ baseURL: 'http://localhost:8080' })
    const factory = new ApiFactory(request)
    await use(factory)
    await factory.cleanup()
    await request.dispose()
  },
})

export { expect }

/** 登录态可用性自检：storageState 缺失时给出明确指引。 */
export function requireAuthState(file: string): void {
  if (!fs.existsSync(path.join(AUTH_DIR, file))) {
    throw new Error(`storageState 缺失: ${file}——请先运行 setup project（npx playwright test）`)
  }
}

export { LoginPage }
