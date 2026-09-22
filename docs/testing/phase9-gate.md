# Phase 9 Release Gate 记录 — Notification

> 时间: 2026-09-22 ｜ 基线: 1bfe252（Phase 8 Gate）→ 本 Gate 提交｜ 结论: **GATE PASS**

## 1. Scope

P9-01 影响分析 → P9-02 数据模型（V13）→ P9-03 通知类型 → P9-04 NotificationService → P9-05 REST API → P9-06 数据级安全 → P9-07 业务触发 → P9-08 去重与事务 → P9-09 前端通知中心 → Java/Python/Vitest 测试 → 真实浏览器 E2E → 本 Gate。

## 2. Notification 数据模型（数据字典 §13 + ADR-019 最小扩展）

`notifications`: id / recipient_id（逻辑引用 users.id，不建 FK）/ type VARCHAR(30) / title VARCHAR(200) / content VARCHAR(1000) / related_type / related_id / is_read TINYINT(1) / read_at / created_at。
索引: `idx_notifications_recipient(recipient_id, is_read)`、`idx_notifications_recipient_created`、`idx_notifications_related`。
扩展字段 read_at（与 is_read 同步写）/ related_type+related_id（业务跳转必需）/ created_at——ADR-019 记录。无事件表、无幂等表（最小实现）。

## 3. 通知类型（最小集 3 类）

`ISSUE_ASSIGNED` / `ISSUE_STATUS_CHANGED` / `ISSUE_COMMENTED`——仅覆盖既有业务的真实触发点，未预设未来类型。通知正文含真实业务上下文（业务编号 projectKey-issueNo、Issue 标题、操作者 #id），无"您有一条新消息"式假通知。

## 4. NotificationService

create（业务触发内部接口）/ listMy（read 筛选+分页，created_at DESC, id DESC 稳定排序）/ unreadCount / markRead（幂等，ownership 收敛在 SQL 条件）/ markAllRead（**数据库条件 UPDATE**，只影响本人未读，非内存遍历）。列表 projectId 批量解析（单次 IN 查询，无 N+1）。

## 5. REST API（4 端点，self 资源）

GET `/api/v1/notifications?read=&page=&size=`｜GET `/notifications/unread-count`｜PATCH `/notifications/{id}/read`｜PATCH `/notifications/read-all`。无 notification:* 权限码（对齐 /auth/me 先例，ADR-019）；仅要求认证；Swagger 完整。

## 6. RBAC/数据级权限

**recipient ownership 收敛在 SQL/查询条件**（recipient_id 等值），不只依赖 Controller；跨用户访问/修改 → **404**（不泄露他人通知存在性）；不给 ADMIN 任何"查看他人通知"后门。

## 7. unread count / mark read / mark all read

未读数 = 数据库实时 COUNT（is_read=0）；单条已读幂等（二次已读 affected=0 仍成功）；全部已读返回 updated 条数且只影响当前用户（Java 集成测试实证：他人通知不受影响）。

## 8. 业务触发点（共事务）

| 触发点 | 类型 | 收件人 |
|---|---|---|
| IssueService.create（创建即分派） | ISSUE_ASSIGNED | assignee（排除操作者） |
| IssueService.update（变更分派；assign 端点复用 update，触发天然收敛） | ISSUE_ASSIGNED | 新 assignee（取消分派不通知/同值不重复） |
| WorkflowService.transition（条件 UPDATE 成功后） | ISSUE_STATUS_CHANGED | assignee + reporter（排除操作者，Set 去重） |
| CommentService.create | ISSUE_COMMENTED | assignee + reporter（排除评论者） |

事务策略（ADR-019）: 触发在主业务 @Transactional 内（REQUIRED 共事务）——主业务回滚通知同回滚；通知 insert 失败上抛回滚主业务（不吞异常）。Java 测试实证"主业务失败不产生通知"。

## 9. 前端 Notification Center

Header 铃铛（el-badge 未读数）→ popover 通知列表（NotificationList）：类型标签/标题/内容/时间线、点击=标记已读+跳转 `/system/projects/{projectId}/issues`（projectId 缺失=Issue 已删，仅标记已读不跳转防异常）、单条已读、全部已读、加载更多分页、loading/error/empty 状态。notification store：unreadCount 页面初始化+操作后刷新（无轮询/无 WebSocket）；**登出/切号 reset 防残留**。全部数据真实 API，零本地模拟。

## 10-12. Java / Python / 前端测试

| 线 | 数量 | 覆盖 |
|---|---|---|
| Java mvn | **299/299 ×2 轮**（新增 NotificationServiceIntegrationTest 12：数据/隔离 404/幂等/空列表/分页/稳定排序/三业务触发/事务回滚；真实 MySQL） |
| Python pytest | **89/89 ×2 轮**（新增 test_notification_api.py 8：三业务触发/read 筛选/幂等/read-all 归零/跨用户 404/分页稳定序/401；真实 HTTP） |
| Vitest | **120/120 ×2 轮**（新增 notification store 7 + NotificationBell/List 8：badge/列表/点击跳转/无 projectId 兜底/全部已读/error/empty/reset 防残留） |
| lint / build | PASS / PASS ×2 |

## 13. 真实浏览器 E2E（IAB Chromium + 后端 + MySQL + Redis，两轮）

第一轮全链路: user1 登录 → **铃铛 badge=3**（API 预置分派/流转/评论三动作）→ 通知中心渲染 3 条真实业务内容（业务编号/操作者/DESC 时间线）→ **点击"评论"通知 → 标记已读 + 跳转 /system/projects/1360/issues** → badge 3→2 → 全部已读 → **badge 消失** → **刷新页面 badge 仍无（持久化）** → 登出 → **登录 admin 无 badge（user1 的通知不出现=隔离+切号不残留）**。
第二轮复验: API 触发新分派 → user1 登录 **badge=1**。

## 14. 测试数据清理（终态实测）

| 存储 | 终态 |
|---|---|
| MySQL | notifications=**0** / users=2 / orgs=0 / projects=0 / issue_comments=0 / attachments=0 ✅（org 级联删除带走 project/issue/comment 实证；通知无 FK 由测试 fixture 显式清空） |
| Redis | auth:* = **0** ✅ |
| MinIO | 本阶段无新增对象（Phase 8 终态保持）✅ |

## 15. Security checks

跨用户读/改 404 ✅｜recipient 服务端决定不可伪造 ✅｜无 ADMIN 全局后门 ✅｜markAllRead 条件 UPDATE 只影响本人 ✅｜未认证 401 ✅｜无 WebSocket/无轮询风暴 ✅｜SQL 全参数绑定 ✅｜通知正文不含敏感信息（仅业务编号/标题/操作者 id）✅

## 16. Bugs found / fixed

本阶段产品代码一次通过，**未发现真实产品缺陷**。过程中修复测试侧问题 3 个：
1. CommentServiceImpl.requireIssueInProject 返回类型 void→Issue（触发点需要 issue 引用）；
2. E2E 前端 jar 为 thin jar（repackage 未执行）→ 完整 package 修复；
3. Vitest 模块级 NOTE 常量被前序用例原地修改（isRead 污染）→ 工厂函数每次新建。

## 17. Known limitations（记录待决策）

- **数据字典 §14 `issue_status_transitions`（标注 Phase 7）未在任何迁移中建表**——Phase 7 遗留缺口，Phase 7 已关 Gate 未擅动，需用户决定是否在后续阶段补建
- 通知无删除/清空单条 API（最小实现；产品上以全部已读覆盖）
- 未读数为拉取式（初始化+操作后刷新）；实时推送（WebSocket）按需求明确排除
- 通知正文含 Issue 标题快照，Issue 改题后历史通知不回溯更新（记录型语义）

## 18. Git commits

`dda3e41` P9-02~08 后端 → `a8a9317` Java 12 测试 → `2ca361e` Python 8 测试 → `4ede808` P9-09 前端+Vitest 15 → 本 Gate 提交

## 19. Working tree

clean @ 本 Gate 提交

## 20. DoD / Gate checklist

[x] Notification migration (V13)｜[x] NotificationService｜[x] REST API｜[x] 数据级权限（SQL 收敛 ownership）｜[x] unread count｜[x] mark read（幂等）｜[x] mark all read（条件 UPDATE）｜[x] Issue assign 通知｜[x] Workflow 通知｜[x] Comment 通知｜[x] 前端通知中心｜[x] Header 未读数｜[x] 通知跳转（含失效兜底）｜[x] Java tests｜[x] Python tests｜[x] frontend tests｜[x] lint｜[x] build｜[x] 真实浏览器 E2E ×2｜[x] 测试数据清理（终态全零）｜[x] docs｜[x] ADR-019｜[x] working tree clean｜[x] 关键测试连续两轮（299/89/120 ×2）

## 21. Final

# Phase 9 PASS ✅
