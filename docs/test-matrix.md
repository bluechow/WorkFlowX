# WorkFlowX 测试矩阵

> 各层数量为 Phase 18 Final QA 实测结果（两轮一致）。

| 测试层 | 工具 | 当前资产 | 运行方式 |
|---|---|---|---|
| 后端集成 | JUnit 5 + MockMvc + 真实 MySQL/MinIO | **311** | `cd backend && mvn test` |
| 前端单元 | Vitest + jsdom | **136** | `cd frontend && npm run test -- --run` |
| API 自动化 | Pytest + httpx（真实 HTTP） | **115** | `cd tests/api && pytest` |
| UI 自动化 | Playwright（真实浏览器） | **25** | `cd frontend && npx playwright test` |
| 安全专项 | Pytest(security marker) 47 + Playwright 7 | **54** | `pytest -m security` |
| 性能 | JMeter 5.6.3 | **四级负载**（L0 85/s → 平台 354/s → 拐点 40 线程） | `tests/performance`（手动） |
| E2E 全链 | API + UI | 业务链 15 环节（pytest 1 + Playwright chain 1） | 全量含 |
| 部署冒烟 | Bash + curl | **15 断言** | `bash scripts/deploy/smoke.sh` |

## 分层标记（pytest）

`-m smoke`（10）/ `-m regression`（8）/ `-m security`（54，含 smoke 交集）/ full = 115。

## 覆盖要点

- **API**：Result/PageVO 契约、认证/会话/JWT 篡改、RBAC 矩阵、IDOR 三层隔离、SQLi 探测、文件安全、通知/审计/仪表盘
- **UI**：登录/权限渲染/各模块/通知/404/全链
- **并发**：Workflow 8 线程仅 1 成功、issue_no 唯一递增、登录失败计数
- **数据生命周期**：全框架清理兜底，跑后终态归零

> 性能数据来自开发者笔记本环境（8C16T），仅代表相对基线，非服务器容量结论。
