# API 自动化测试体系（Phase 12）

## 架构

```
tests/api/
├── conftest.py            # 共享 fixture（api_client/db/redis/token/清理）；P12 扩展命名空间清理
├── clients.py             # ApiSession——独立 token 的 httpx 会话封装（P12-03）
├── factories.py           # Factory——真实 API 数据工厂 + 轨迹 + 级联清理（P12-05/06）
├── assertions.py          # assert_success/assert_status/assert_error/assert_page/assert_envelope（P12-07）
├── test_*.py              # 16 个测试文件（按模块）
└── pytest.ini             # markers: smoke / regression / contract / concurrency（P12-23）
```

**分层运行**：
```
pytest -m smoke        # 快速核心可用性（contract 关键路径 + health），<2s
pytest -m regression   # 核心业务回归（边界/隔离/并发/业务链）
pytest                 # full=全部（smoke+regression+既有 97 用例）
```

## Client（P12-03）

`ApiSession`：每用户独立实例（token 注入 Authorization；互不覆盖）；`relogin()` 仅用于工厂用户；`upload()` 支持 multipart；调试头信息经 `redact_headers` 脱敏（Authorization 掩码）。

## 认证策略（P12-04——单会话安全铁律）

- **禁止重登 seed 用户（admin/user1）**：单会话策略下重登会顶掉 conftest 会话级 token，污染后续全部测试文件（P12 建设期实际踩坑：contract 的 login_contract 曾重登 admin 导致 69 用例连锁 401）。
- 动态用户（`api_test_` 前缀）+ `factory.login_as()` 独立会话；角色变更后重登仅针对工厂用户自身。

## Factory（P12-05/06）

`Factory(client)`：create_user / create_org / create_department / create_project / add_project_member / create_issue / transition / create_role_with_permissions；uuid 唯一后缀；`cleanup()` 逆序回收（org 删除级联 project/issue/comment/attachment；角色解绑依赖 conftest 用户清理）。

## 清理（P12-06/30——终态自然归零）

conftest session 级 cleanup（每轮 pytest 结束自动执行）：
1. 工厂用户（api_test_%）的通知/审计/user_roles/用户；
2. AA 前缀摘要审计；
3. **seed 账号（admin/user1）全部审计**（测试期以 seed 身份产生的操作记录，跨轮无保留价值）；
4. 匿名失败审计（LOGIN_FAIL user_id=NULL）；
5. Redis `auth:fail:*` 全清（**防跨轮 429 污染**——TTL 900s 累积曾致 401 变 429）+ `auth:session:*`。

**清理失败必须可见**（print WARNING），禁止静默 except pass（历史教训：静默吞 401 致 180+ 组织跨轮累积）。

## Contract（P12-08/22）

`test_contract_api.py`（smoke+contract）：锁定 Result envelope（code/message/data/timestamp/traceId；**安全层 401/403 无 data 字段**）+ PageVO（list/total/page/size）+ 各资源关键字段——针对 Phase 11 暴露的"包装层级解析错误且 mock 掩盖"问题的防回归网。

## 并发（P12-24）

Workflow 8 线程 transition（仅 1 成功）、并发登录失败不绕过锁定、同项目并发创建 issue_no 唯一递增（1..8 排列断言）。

## 已知限制

- Java mvn 测试运行产生的审计（admin AUTH 等）由 Gate 前清理承接（Java 端无命名空间回收）
- markers 未回溯标记既有 97 用例（默认进 full；后续按需补标）
