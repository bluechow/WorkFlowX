import { defineConfig, devices } from '@playwright/test'

/**
 * UI 自动化配置（Phase 13）。
 *
 * 前置条件（测试外保证，文档见 docs/testing/ui-automation.md）:
 * - docker compose 基础设施（MySQL/Redis/MinIO）已启动
 * - 后端 jar 已运行于 :8080
 * - 本配置的 webServer 自动启动/复用 Vite dev server (:5173)
 *
 * 认证（P13-06）: setup project 登录一次生成 storageState（单会话铁律——
 * 全程每 seed 用户仅登录一次，绝不重复重登顶掉会话）。
 * 分层: 标题 tag @smoke/@regression/@critical + grep 运行（P13-23~25）。
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false, // 共享后端与 seed 会话，顺序执行保证稳定
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'test-results/report' }]],
  outputDir: 'test-results/artifacts',
  use: {
    baseURL: process.env.PLAYWRIGHT_BASEURL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
    locale: 'zh-CN',
  },
  projects: [
    // 认证 setup：一次性登录生成 storageState（不提交 Git）
    { name: 'setup', testMatch: /auth\.setup\.ts/ },
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
      dependencies: ['setup'],
      testIgnore: /auth\.setup\.ts/,
    },
  ],
  webServer: {
    command: 'npm run dev -- --port 5173 --strictPort',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 60_000,
  },
})
