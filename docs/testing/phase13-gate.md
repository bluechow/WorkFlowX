# Phase 13 Release Gate 记录 — UI Automation

> 时间: 2026-09-23 ｜ 基线: 7c00f34（Phase 12 Gate）→ 本 Gate 提交｜ 结论: **GATE PASS**

## 1. P13-01 Gap Analysis

Playwright 从零建设（P11 浏览器验证为 IAB 手工驱动，非可维护资产）；selector 基础 13 个 data-test + EP role 语义；**核心 gap = 可长期维护的框架（Page Object/storageState 认证/数据工厂/分层/诊断）全部缺失**。Phase 11 的 21+8 步不复制——升级为 Page Object 化自动化并新增权限分层/会话失效/数据隔离场景。

## 2. P13-02~05 架构 / Page Objects / Selectors

`playwright.config.ts`（webServer vite 复用/trace retain-on-failure/截图仅失败）｜`e2e/auth.setup.ts`（storageState）｜`fixtures/ui.ts`（adminPage/userPage/publicPage/factory）｜`pages/index.ts`（7 个 Page Object）｜`utils/api-factory.ts`（真实 HTTP 工厂）。Selector：role/data-test 优先，** Owner 行用 `filter({ has: locator })` 消歧**（el-descriptions 信息行同词陷阱）。

## 3. P13-06 认证（单会话铁律）

setup 每 seed 用户登录一次生成 storageState；**每次 playwright 调用重新登录刷新**（陈旧 state 短路曾致整轮假失败——已修）；**测试主体禁止重登 seed**（登录用例/登出用例/链路全部改工厂或动态 ADMIN 用户）。实测验证：登录/错密/未登录拦截/已登录访问 /login 回跳/刷新保持/登出/401 自动跳转（会话被顶场景）/切换用户无残留。

## 4-20. 模块 specs（20 用例）

auth 6（@smoke @critical）｜core-modules 8（@regression @permission：Dashboard 图表/组织部门树/项目/Issue 列表+抽屉/审计/权限渲染×3/404）｜notification 2（badge→中心→已读归零/切换隔离）｜chain 1（@full @critical 15 环节全链）。**P11 三处列表解析缺陷防回归用例**（org/project/issue 列表必须真实出数）落地。

## 21. 真实端到端业务链（chain.spec）

UI 登录（动态 ADMIN 用户，单会话安全）→ UI 新建组织 → API 部门/用户/ADMIN 角色绑定/项目/成员 → Issue 分派 → **UI Workflow 流转 OPEN→IN_PROGRESS（等待行刷新）** → UI 评论 → 通知（badge）→ 审计（issue:{id} 事实）→ Dashboard（卡片自洽）→ 登出。

## 22. 诊断能力

失败自动产出 trace.zip/截图/error-context.md（ARIA 快照）/HTML report；本轮多次失败定位全部依赖 error-context 页面快照完成（零截图人肉比对）。

## 23-25. 分层

smoke 8 / regression 13 / full 3（grep tag 实现，无复制）。

## 26-27. 稳定性

组织 OWNER 断言间歇失败（定位器歧义：descriptions 行与成员行同词）→ has-filter 消歧后 3 连跑稳定；workflowTransition 封装（option waitFor+行刷新断言）消除下拉竞态。

## 28. 两轮回归（最终）

| 套件 | 第一轮 | 第二轮 |
|---|---|---|
| Playwright（full 20） | 20 passed | 20 passed |
| Vitest | 136/136 | 136/136 |
| lint / build | PASS / PASS | PASS / PASS |
| mvn test | 311/311 | 311/311 |
| pytest full | 115/115 | 115/115 |
| Playwright 分层 | smoke 8 / regression 13 / full 3 | — |

## 29. 文档

docs/testing/ui-automation.md + phase13-gate.md；AI_TASKS/AI_CONTEXT 同步。

## 30. 数据清理与终态（实测）

users=2(seed) / organizations=0 / departments=0 / projects=0 / project_members=0 / issues=0 / comments=0 / attachments=0 / notifications=0 / audit=0 / Redis auth:*=0 / MinIO objects=0。Playwright artifacts（test-results/playwright-report）已 gitignore。

## 31. 发现的问题

**真实产品缺陷 1 个（已修复+防回归）**：
- **ADMIN 刷新后系统管理菜单全部消失**：`fetchMe()` 刷新恢复路径不恢复 roles（仅 login 响应携带），App.vue `isAdmin=roles.includes('ADMIN')` 刷新后恒 false。修复：菜单显隐改由权限码驱动（hasAnySystemMenu，权限码经 /auth/me/permissions 刷新恢复，与后端 authority 语义一致）。UI 自动化防回归：权限渲染用例（user1 隐/admin 显）。

**测试侧缺陷 5 个（已修）**：
1. storageState existsSync 短路 → 陈旧 token 整轮假失败（改每次刷新）；
2. chain UI 登录 seed admin 顶会话（改动态 ADMIN 用户）；
3. factory ensureToken 重复 API 登录 seed admin（改复用 setup token）；
4. 登出用例销毁 seed 会话（改工厂用户）；
5. Owner 行定位歧义（el-descriptions 同词行）+ 行内双下拉 strict 冲突（has-text 过滤）。

**数据清理缺陷 1 个（测试体系，已修）**：chain 的 UI 建组织不入工厂轨迹 + owner 删除失败仍删动态用户 → 22 个无主组织化石。修复：trackOrg 轨迹 + org 删除失败保留 owner + pytest 跨框架 AA% 兜底清理。

## 32. Environment Issues

Playwright chromium 经 npmmirror 二进制镜像安装（境外源不可用，既定策略）。

## 33. Git commits

`83900a5`~`c57aace`（P11）→ `4ae5bbc`/`7331a8c`（P12）→ Playwright 架构+specs → 菜单权限缺陷修复 → 清理修复 → 本 Gate 提交

## 34. Working tree / 终态

clean @ 本 Gate 提交。终态全零 + seed users=2。

## 35. Final

# Phase 13 PASS ✅
