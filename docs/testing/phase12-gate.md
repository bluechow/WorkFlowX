# Phase 12 Release Gate 记录 — API Automation

> 时间: 2026-09-23 ｜ 基线: 4fe8dc5（Phase 11 Gate）→ 本 Gate 提交｜ 结论: **GATE PASS**

## 1. API Coverage Matrix（P12-01）

后端 60+ 端点全枚举 vs 现有 13 测试文件（97 用例）逐模块核对。覆盖良好：AUTH/Users/RBAC/Comment/Attachment/Notification/Workflow（矩阵+并发）/Audit/Dashboard/Project lifecycle/Org 部门树。**真正缺口**（不重复补测）：
1. Response Contract（envelope/PageVO 结构——P11 解析缺陷的防回归网）
2. Issue 字段边界（Python 层：empty/max/over-max title、invalid enum、severity on non-BUG）
3. 跨组织数据隔离显式断言
4. issue_no 并发唯一递增（Python 层）
5. 完整业务链端到端
6. 分层 markers（smoke/regression/full）

## 2. 架构（P12-02~07，渐进式不破坏既有）

`clients.py`（ApiSession：独立 token/multipart/relogin/脱敏）｜`factories.py`（Factory：真实 API 工厂+轨迹+级联清理）｜`assertions.py`（assert_success/status/error/page/envelope）｜pytest.ini markers。既有 13 文件与 conftest fixture 保持兼容运行。

## 3. 认证与隔离（P12-04）

**铁律落地：禁止重登 seed 用户**。全部动态用户（api_test_ 前缀）+ `factory.login_as()` 独立会话。建设期实际踩坑并修复：login_contract 曾用 api_client 重登 admin → 单会话覆盖 → conftest.admin_token 失效 → 69 用例连锁 401（**这正是 Phase 10/11 教训在测试体系内的复现**，架构修复后消灭）。

## 4-7. Contract / Negative / Boundary / Concurrency

| 类别 | 文件 | 用例 | 要点 |
|---|---|---|---|
| Contract | test_contract_api.py | 10 | envelope 完整性（安全层 401/403 无 data 真实契约）/PageVO 四字段/User·Org·Project·Issue·Notification·Audit·Dashboard 结构/敏感词扫描 |
| Boundary+Isolation+Concurrency | test_issue_boundary_api.py | 7 | title empty/max200/over201、invalid enum 422、severity on non-BUG 400、四类型、跨组织读写 403、**8 线程并发创建 issue_no=1..8 排列** |
| Business Chain | test_business_chain_api.py | 1 | 用户创建→登录→组织→部门→角色→项目→成员→Issue→分派→流转→评论→附件（下载逐字节+删除双清）→通知 3 类→审计 target 一致→Dashboard 自洽→**SQL 终态核验** |

Negative（401/403/404/409/422/429）与既有用例已系统性覆盖（auth/user/rbac/project/issue/workflow/comment/attachment 各文件），未重复造数。

## 8. 测试数量与两轮回归（P12-28）

| 套件 | 第一轮 | 第二轮 |
|---|---|---|
| pytest -m smoke | 10 passed | 10 passed |
| pytest -m regression | 8 passed | 8 passed |
| pytest（full） | **115 passed** | **115 passed** |
| mvn test | 311/311 | 311/311 |
| Vitest | 136/136 | 136/136 |
| lint / build | PASS / PASS | PASS / PASS |

## 9. 真实环境与业务链（P12-29）

真实 Backend/MySQL/Redis/MinIO。完整业务链单测贯穿 15 环节全部经 HTTP（SQL 仅终态核验：issues.status=IN_PROGRESS、通知行数≥3、附件行删除后=0、下载 bytes==上传 bytes）。

## 10. 数据清理与终态（P12-30，实测）

**终态自然归零达成**：pytest full 跑后无需手动干预——audit_logs=0 / notifications=0 / users=2(seed) / orgs=0 / projects=0 / issues=0 / comments=0 / attachments=0 / Redis auth:*=0 / MinIO objects=0。

清理策略（非全表 DELETE）：
1. 工厂用户命名空间（api_test_%）通知/审计/绑定/用户；
2. seed 账号（admin/user1）审计（测试期操作记录跨轮无价值）；
3. 匿名失败审计（LOGIN_FAIL user_id=NULL）；
4. AA 前缀摘要；
5. Redis auth:fail:* 全清 + auth:session:*；
6. **清理失败可见化**（禁止静默 except pass）。

## 11. 发现的问题

**真实产品缺陷：0 个**（后端行为全程稳定）。
测试侧问题 4 个（全部修复）：
1. login_contract 重登 seed admin → 单会话覆盖 → 69 用例连锁 401（改工厂用户）；
2. auth:fail 键 TTL 900s 跨轮累积 → 401 变 429（cleanup 全清修复）；
3. cleanup_orgs 删除失败被静默吞 → 历史 186 组织化石累积（可见化 + 一次性清化石 + 根因修复后验证零累积）；
4. 安全层 403 响应无 data 字段——envelope 契约按真实行为修正（401/403 data 缺省为合法契约）。

## 12. Known Issues

- Java mvn 运行产生的审计由 Gate 前清理承接（Java 端无命名空间回收机制）
- 既有 97 用例未回溯打 marker（默认 full；可渐进补标）
- Allure 未引入（当前规模 pytest 输出+失败摘要足够定位；引入时机留待 Phase 13/14 评估）

## 13. Git commits

`4ae5bbc` P12 架构+18 新用例 → `7331a8c` 清理体系修复 → 本 Gate 提交（文档）

## 14. Working tree

clean @ 本 Gate 提交

## 15. Gate checklist

[x] coverage matrix｜[x] Client｜[x] Session/auth（单会话铁律）｜[x] user isolation｜[x] factories｜[x] cleanup（自然归零）｜[x] assertions｜[x] response contract｜[x] authentication｜[x] users｜[x] RBAC｜[x] organization｜[x] department｜[x] project｜[x] project members｜[x] issue｜[x] workflow｜[x] comments｜[x] attachment｜[x] notification｜[x] audit｜[x] dashboard｜[x] negative｜[x] boundary｜[x] concurrency｜[x] smoke｜[x] regression｜[x] full｜[x] contract suite｜[x] real MySQL/Redis/MinIO｜[x] two-round regression｜[x] business chain｜[x] data cleanup｜[x] documentation｜[x] working tree clean

## 16. Final

# Phase 12 PASS ✅
