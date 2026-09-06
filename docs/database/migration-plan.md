# Flyway 迁移脚本归属计划

> 原则: 渐进式演进 —— 每个业务 Phase 通过新增迁移脚本落地对应表结构，
> 禁止在 Phase 1 一次性建全所有表（后续需求演进会推翻未验证的设计）。
> 命名: `V<序号>__<描述>.sql`，序号全局递增，已提交脚本禁止修改。

| 迁移 | 所属 Phase | 内容 | 状态 |
|---|---|---|---|
| V1__identity_core.sql | Phase 1 | 身份域 5 张表：users / roles / permissions / user_roles / role_permissions | ✅ 已落地 |
| V2（Phase 2 时定稿命名） | Phase 2 | users 演进字段（如手机号、头像）+ 初始角色/权限种子数据 | 待定 |
| V3 | Phase 4 | organizations / organization_members | 待定 |
| V4 | Phase 5 | projects / project_members | 待定 |
| V5 | Phase 6 | issues | 待定 |
| V6 | Phase 7 | issue_status_transitions | 待定 |
| V7 | Phase 8 | issue_comments / attachments | 待定 |
| V8 | Phase 9 | notifications | 待定 |
| V9 | Phase 10 | audit_logs | 待定 |

## 执行规则

1. 脚本位置: `backend/src/main/resources/db/migration/`
2. 应用启动时自动执行（`spring.flyway.enabled=true`，见 application-{dev,prod}.yml）
3. 破坏性变更（删表/删列/改列类型）属于 AI_WORKFLOW.md STOP 条件 S2，必须先经用户确认
4. 迁移脚本变更必须同步更新 `data-dictionary.md` 与 `er-model.md`
