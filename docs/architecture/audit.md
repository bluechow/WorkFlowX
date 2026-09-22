# Audit 审计架构（Phase 10; ADR-020）

## 职责边界

| 概念 | 职责 |
|---|---|
| **Audit（本模块）** | 记录系统层面高价值操作事实（谁/何时/对什么/做了什么/结果） |
| Issue History | （不存在的表）Issue 领域字段历史——当前由 Audit 的操作事实兼承 |
| Workflow | 定义允许的状态转换（WorkflowService 内存矩阵，ADR-017） |
| Notification | 告知用户发生了什么（收件箱视角） |
| Dashboard | 统计真实业务数据（聚合视角） |

`issue_status_transitions`（数据字典 §14）**不建表**：三个候选职责均已被覆盖，无独立消费者（ADR-020 决策 D）。

## 数据模型（V14；数据字典 §15 + 扩展）

`audit_logs`: user_id（UNSIGNED，匿名/系统占位归一化为 NULL）/ module / action / http_method / uri / ip / target（"issue:123"）/ summary（白名单业务摘要）/ success / trace_id（MDC）/ user_agent / created_at。
索引：`idx_audit_user_time(user_id, created_at)`、`idx_audit_module(module)`、`idx_audit_created(created_at)`。

## 事务双语义（ADR-020）

- `record`：同事务（REQUIRED）——业务回滚不留"成功"审计。
- `recordStandalone`：REQUIRES_NEW——登录失败等"失败事实本身必须幸存"场景。
- `login` 无 @Transactional（无多表原子性需求；且并发失败路径 REQUIRES_NEW 会耗尽连接池——真实缺陷已修）。

## 接线点（16 个，Service 层显式）

AUTH（LOGIN/LOGIN_FAIL/LOGOUT）、USER（CREATE/STATUS）、ORG（CREATE/DELETE）、PROJECT（CREATE/STATUS/ASSIGN_MEMBER）、ISSUE（CREATE/TRANSITION）、COMMENT（CREATE/DELETE）、ATTACHMENT（UPLOAD/DELETE）。读操作不记。

## 安全红线

summary 仅白名单字段；密码/JWT/Authorization/会话键/凭据绝不入库；登录失败摘要不含用户输入的密码。测试断言守护（`sensitiveDataMustNeverBeRecorded`、Python `test_login_failure_is_audited_without_password`）。

## 权限与数据范围

audit:list / audit:get（V14 种子，仅 ADMIN 绑定）。审计是全系统高敏感资源，不提供项目级子集授权；普通角色一律 403。
