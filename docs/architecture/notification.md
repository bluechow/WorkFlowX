# Notification 架构（Phase 9; ADR-019）

## 定位

站内通知是 Issue 协作的伴随能力：业务成功即产生通知，收件箱为 **self 资源**（currentUser == recipient）。

## 数据模型（V13；数据字典 §13）

`notifications`：recipient_id（逻辑引用 users.id，无 FK）/ type / title / content / related_type + related_id（当前仅 `ISSUE`）/ is_read + read_at / created_at。
通知是**历史记录**：Issue 删除不级联删除通知；跳转失效由 VO 的 `projectId=null` 传给前端兜底（仅标记已读不跳转）。

## 触发点（共事务）

| 业务方法 | 通知类型 | 收件人 |
|---|---|---|
| IssueServiceImpl.create（创建即分派） | ISSUE_ASSIGNED | assignee |
| IssueServiceImpl.update（变更分派） | ISSUE_ASSIGNED | 新 assignee |
| WorkflowServiceImpl.transition | ISSUE_STATUS_CHANGED | assignee + reporter |
| CommentServiceImpl.create | ISSUE_COMMENTED | assignee + reporter |

统一规则：收件人 Set 去重、排除操作者本人；触发在主业务事务内（REQUIRED），失败上抛回滚。

## 权限与数据范围

- 无 `notification:*` 权限码（self 资源，对齐 /auth/me 先例）；Controller 仅要求认证。
- 数据隔离收敛在 SQL/查询条件（`recipient_id = currentUser`），跨用户访问统一 404 不泄露存在性。
- ADMIN 无全局查看后门。

## API

GET /api/v1/notifications（read 筛选 + 分页，created_at DESC）｜GET /notifications/unread-count｜PATCH /notifications/{id}/read（幂等）｜PATCH /notifications/read-all（条件 UPDATE，仅本人未读）。

## 前端

Header 铃铛（el-badge 未读数）→ 通知列表 popover：点击通知 = 标记已读 + 跳转 `/system/projects/{projectId}/issues`。未读数策略为**页面初始化 + 操作后刷新**（无轮询/无 WebSocket）；登出/切换用户时 store reset 防残留。

## 已知限制

- 无实时推送（拉取式）；无单条删除 API；通知正文为标题快照不回溯更新；`issue_status_transitions`（数据字典 §14，标注 Phase 7）未建表——遗留缺口待决策。
