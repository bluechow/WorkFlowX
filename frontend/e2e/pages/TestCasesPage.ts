import { expect, type Page } from '@playwright/test'

/** 测试用例库 Page Object（Phase 20）。 */
export class TestCasesPage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  async goto(projectId: number): Promise<void> {
    await this.page.goto(`/system/projects/${projectId}/testcases`)
    await this.page.getByRole('heading', { name: '测试用例库' }).waitFor()
  }

  async expectCaseVisible(title: string): Promise<void> {
    await expect(this.page.locator('tbody tr', { hasText: title }).first()).toBeVisible()
  }
}
