# UI 自动化测试体系（Phase 13）

## 目标

浏览器端端到端回归资产：真实 Chromium → 真实 Vue 前端 → 真实 HTTP → 真实后端 → MySQL/Redis/MinIO。零 mock、零固定业务 ID、零固定 sleep。

## 架构

```
frontend/
├── playwright.config.ts   # baseURL=:5173 / webServer(vite, reuseExistingServer) / setup project / trace retain-on-failure
├── e2e/
│   ├── auth.setup.ts      # storageState 生成（seed admin/user1 各登录一次）
│   ├── fixtures/ui.ts     # adminPage / userPage / publicPage / factory
│   ├── pages/index.ts     # Page Objects（LoginPage/AppShell/Organizations/OrgDetail/Projects/Issues/Audit）
│   ├── utils/api-factory.ts # ApiFactory——真实 HTTP 数据工厂 + 多身份清理
│   └── specs/             # auth / core-modules / notification / chain
```

**运行**：
```
npx playwright test                    # full（20 用例）
npx playwright test --grep @smoke      # 8（登录核心+组织/项目/Issue 防回归）
npx playwright test --grep @regression # 13
npx playwright test --grep @full       # 3（完整业务链）
npm run test:e2e[:smoke|:regression|:full]
```
前置：docker compose 基础设施 + 后端 jar（:8080）；vite 由 webServer 自动启动/复用。

## 认证（单会话铁律）

- setup project 每 seed 用户**登录一次**生成 storageState（test-results/.auth/，已 gitignore，token 不进 Git）；
- **每次 playwright 调用重新登录刷新 state**（不做 existsSync 短路——陈旧 state 会导致整轮假失败）；
- **任何测试禁止重登 seed 用户**（重登=顶会话=后续用例连锁 401）；
- 动态用户（api_test_ 前缀）经 API 创建/登录，独立会话互不影响。

## Page Objects / Selector 策略

Page Object 持定位器+动作+状态验证；spec 只写业务意图与断言。定位优先级：role（menuitem/button/option/tab/textbox）→ data-test → placeholder/稳定文本 → CSS（.el-select/.el-dialog 等 EP 顶层容器）→ 禁 XPath。**消歧经验**：el-descriptions 信息行与数据行可能含同词（如 OWNER）——用 `filter({ has: locator })` 按子元素消歧；行内双下拉（状态/经办人）按当前值文本过滤。

## 数据与清理

- API 工厂预置数据（AA 命名空间）+ **UI 创建的资源经 trackOrg 登记进清理轨迹**；
- cleanup 多身份重试（seed admin → 各动态用户——组织删除仅 OWNER）；
- **org 删除失败时保留动态用户**（避免 owner 缺失的无主组织化石）；
- 跨框架兜底：pytest conftest 回收 AA% 组织与 api_test_ 用户（Playwright 残留由 pytest 一轮清零）；
- Gate 终态：Java mvn 产生的审计按既定策略 Gate 前清理。

## 浏览器诊断

trace（retain-on-failure）+ 失败截图 + error-context.md（页面 ARIA 快照）+ HTML report（test-results/report）。敏感信息：Authorization/密码不入日志与断言输出。

## 覆盖矩阵（20 用例）

| 模块 | spec | 关键场景 |
|---|---|---|
| AUTH | auth.spec（6）@smoke @critical | 正确登录（工厂用户）/错密统一提示/未登录拦截/已登录访问 /login/刷新保持/登出+重新拦截 |
| Dashboard | core-modules | 卡片+双 canvas（真实聚合） |
| Organization/Department | core-modules | 列表/详情/部门树/OWNER 移除禁用（P11 防回归） |
| Project | core-modules | 列表真实出数（P11 防回归） |
| Issue | core-modules | 列表真实出数+抽屉附件 tab（P11 防回归） |
| Audit | core-modules | 业务事实渲染 |
| Permission | core-modules | user1 菜单全隐/直访审计提示/admin 7 菜单 |
| 404 | core-modules | 兜底页+返回首页+登录态保持 |
| Notification | notification.spec（2） | 分派→badge→中心→全部已读归零；切换用户状态不残留 |
| 全链 | chain.spec（1）@full @critical | UI 登录（动态 ADMIN）→UI 建组织→部门/用户/角色/项目/成员（API）→Issue 分派→UI Workflow 流转→评论→通知→审计→Dashboard→登出 |

## Known Issues

- 顺序执行（fullyParallel=false）：seed 会话与共享后端下保证稳定；并行化留待 CI 阶段评估
- UI 层不覆盖并发压力（Phase 12 API 层已覆盖；Phase 14 承接性能）
- 附件下载断言（浏览器下载事件）未覆盖——下载链路由 API 层逐字节断言兜底，UI 层验证入口与列表
