# WorkFlowX 数据字典

> 阶段: Phase 1 任务 1-4 ｜ 状态: V1 为已落地设计，其余为初稿
> 标注: ✅ = 已由 Flyway 落地 ｜ 📋 = 设计稿（DDL 归属见 migration-plan.md）

## 通用约定

| 项 | 约定 |
|---|---|
| 主键 | `id BIGINT UNSIGNED AUTO_INCREMENT` |
| 创建时间 | `created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` |
| 更新时间 | `updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` |
| 字符集 | utf8mb4 / utf8mb4_0900_ai_ci |
| 引擎 | InnoDB |

## 1. users ✅

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK, AI | 主键 |
| username | VARCHAR(50) | NOT NULL, UNIQUE | 登录名 |
| email | VARCHAR(100) | NOT NULL, UNIQUE | 邮箱 |
| password_hash | VARCHAR(100) | NOT NULL | 密码散列（bcrypt），禁止明文 |
| nickname | VARCHAR(50) | NULL | 显示昵称 |
| status | ENUM | NOT NULL DEFAULT 'ACTIVE' | ACTIVE / DISABLED / LOCKED |
| last_login_at | DATETIME(3) | NULL | 最后登录时间 |
| created_at / updated_at | DATETIME(3) | NOT NULL | 通用时间字段 |

索引：`uk_users_username`、`uk_users_email`、`idx_users_status`

## 2. roles ✅

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK, AI | 主键 |
| code | VARCHAR(50) | NOT NULL, UNIQUE | 角色编码（ADMIN / MANAGER / MEMBER…） |
| name | VARCHAR(50) | NOT NULL | 角色名称 |
| description | VARCHAR(200) | NULL | 描述 |

## 3. permissions ✅

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK, AI | 主键 |
| code | VARCHAR(100) | NOT NULL, UNIQUE | 权限编码 `{resource}:{action}`（规范见 docs/architecture/rbac.md §2，正则 `^[a-z][a-z0-9_]{1,49}:[a-z][a-z0-9_]{1,49}$`） |
| name | VARCHAR(50) | NOT NULL | 权限名称 |
| type | ENUM | NOT NULL DEFAULT 'API' | MENU / API / BUTTON |
| description | VARCHAR(200) | NULL | 描述 |

系统权限 14 项由 V3 迁移种子（ADR-012），代码层禁止删除（RbacConstants.SYSTEM_PERMISSION_CODES）；ADMIN 角色全量绑定。

## 4. user_roles ✅

| 字段 | 类型 | 说明 |
|---|---|---|
| user_id | BIGINT UNSIGNED, PK, FK→users(id) CASCADE | 用户 |
| role_id | BIGINT UNSIGNED, PK, FK→roles(id) CASCADE | 角色 |

索引：复合主键 (user_id, role_id)；`idx_user_roles_role(role_id)` 支持反向查询

## 5. role_permissions ✅

| 字段 | 类型 | 说明 |
|---|---|---|
| role_id | BIGINT UNSIGNED, PK, FK→roles(id) CASCADE | 角色 |
| permission_id | BIGINT UNSIGNED, PK, FK→permissions(id) CASCADE | 权限 |

索引：复合主键 (role_id, permission_id)；`idx_role_permissions_perm(permission_id)`

## 6. organizations 📋（Phase 4）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT UNSIGNED, PK | 主键 |
| name | VARCHAR(100), NOT NULL | 组织名称 |
| code | VARCHAR(50), UNIQUE, NOT NULL | 组织编码 |
| owner_id | BIGINT UNSIGNED, NOT NULL | 所有者（逻辑引用 users.id，不建外键） |
| description | VARCHAR(500), NULL | 描述 |

## 7. organization_members 📋（Phase 4）

| 字段 | 类型 | 说明 |
|---|---|---|
| org_id | BIGINT UNSIGNED, PK, FK | 组织 |
| user_id | BIGINT UNSIGNED, PK, FK | 成员 |
| role | ENUM, NOT NULL DEFAULT 'MEMBER' | OWNER / ADMIN / MEMBER |

## 8. projects 📋（Phase 5）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT UNSIGNED, PK | 主键 |
| name | VARCHAR(100), NOT NULL | 项目名称 |
| `key` | VARCHAR(20), UNIQUE, NOT NULL | 项目标识（issue 编号前缀，如 WFX） |
| org_id | BIGINT UNSIGNED, NOT NULL, FK | 所属组织 |
| description | VARCHAR(500), NULL | 描述 |
| status | ENUM, NOT NULL DEFAULT 'ACTIVE' | ACTIVE / ARCHIVED |

## 9. project_members 📋（Phase 5）

| 字段 | 类型 | 说明 |
|---|---|---|
| project_id | BIGINT UNSIGNED, PK, FK | 项目 |
| user_id | BIGINT UNSIGNED, PK, FK | 成员 |
| role | ENUM, NOT NULL DEFAULT 'MEMBER' | 项目内角色（随 Phase 5 细化） |

## 10. issues 📋（Phase 6，枚举对齐 Master Prompt §14）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT UNSIGNED, PK | 主键 |
| project_id | BIGINT UNSIGNED, NOT NULL, FK | 所属项目 |
| issue_no | BIGINT UNSIGNED, NOT NULL | 项目内序号（与 project `key` 组成业务编号，如 WFX-1；(project_id, issue_no) 唯一） |
| title | VARCHAR(200), NOT NULL | 标题 |
| description | TEXT, NULL | 描述 |
| type | ENUM, NOT NULL DEFAULT 'TASK' | BUG / TASK / FEATURE / IMPROVEMENT |
| priority | ENUM, NOT NULL DEFAULT 'MEDIUM' | LOW / MEDIUM / HIGH / URGENT |
| severity | ENUM, NULL | S1 / S2 / S3 / S4（仅 type=BUG） |
| status | ENUM, NOT NULL DEFAULT 'OPEN' | OPEN / IN_PROGRESS / RESOLVED / TESTING / CLOSED / REOPENED |
| reporter_id | BIGINT UNSIGNED, NOT NULL | 报告人（索引，不建外键） |
| assignee_id | BIGINT UNSIGNED, NULL | 经办人（索引，不建外键） |

索引：`uk_issues_project_no(project_id, issue_no)`、`idx_issues_project_status(project_id, status)`、`idx_issues_assignee(assignee_id)`、`idx_issues_reporter(reporter_id)`

## 11. issue_comments 📋（Phase 8）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT UNSIGNED, PK | 主键 |
| issue_id | BIGINT UNSIGNED, NOT NULL, FK | 所属 Issue |
| author_id | BIGINT UNSIGNED, NOT NULL | 作者（索引，不建外键） |
| content | TEXT, NOT NULL | 内容 |

索引：`idx_comments_issue(issue_id, created_at)`

## 12. attachments 📋（Phase 8）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT UNSIGNED, PK | 主键 |
| issue_id | BIGINT UNSIGNED, NOT NULL, FK | 所属 Issue |
| object_key | VARCHAR(200), NOT NULL | MinIO 对象键（库存元数据，MinIO 存文件，Master Prompt §13） |
| file_name | VARCHAR(255), NOT NULL | 原始文件名 |
| file_size | BIGINT UNSIGNED, NOT NULL | 字节数 |
| content_type | VARCHAR(100), NOT NULL | MIME 类型 |
| uploader_id | BIGINT UNSIGNED, NOT NULL | 上传者 |

## 13. notifications 📋（Phase 9）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT UNSIGNED, PK | 主键 |
| recipient_id | BIGINT UNSIGNED, NOT NULL | 接收人（索引，不建外键） |
| type | VARCHAR(30), NOT NULL | 通知类型（随 Phase 9 定义） |
| title | VARCHAR(200), NOT NULL | 标题 |
| content | VARCHAR(1000), NULL | 内容 |
| is_read | TINYINT(1), NOT NULL DEFAULT 0 | 已读标记 |

索引：`idx_notifications_recipient(recipient_id, is_read)`

## 14. issue_status_transitions 📋（Phase 7，状态机审计）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT UNSIGNED, PK | 主键 |
| issue_id | BIGINT UNSIGNED, NOT NULL, FK | Issue |
| from_status | ENUM, NULL | 原状态（OPEN 创建时为 NULL） |
| to_status | ENUM, NOT NULL | 目标状态 |
| operator_id | BIGINT UNSIGNED, NOT NULL | 操作人 |
| comment | VARCHAR(500), NULL | 转换说明（如测试失败原因） |

约束：非法状态转换由应用层状态机拒绝（Master Prompt §15），本表仅记录合法历史。

## 15. audit_logs 📋（Phase 10）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT UNSIGNED, PK | 主键 |
| user_id | BIGINT UNSIGNED, NULL | 操作人（匿名请求为 NULL） |
| module | VARCHAR(30), NOT NULL | 业务模块 |
| action | VARCHAR(30), NOT NULL | 操作 |
| http_method | VARCHAR(10), NOT NULL | HTTP Method |
| uri | VARCHAR(200), NOT NULL | 请求 URI |
| ip | VARCHAR(45), NOT NULL | 客户端 IP（兼容 IPv6） |
| target | VARCHAR(100), NULL | 目标对象（如 issue:123） |
| success | TINYINT(1), NOT NULL | 操作结果 |

索引：`idx_audit_user_time(user_id, created_at)`、`idx_audit_module(module)`

## 枚举汇总（对齐 Master Prompt §14）

| 枚举 | 取值 |
|---|---|
| issue type | BUG / TASK / FEATURE / IMPROVEMENT |
| priority | LOW / MEDIUM / HIGH / URGENT |
| bug severity | S1 / S2 / S3 / S4 |
| issue status | OPEN / IN_PROGRESS / RESOLVED / TESTING / CLOSED / REOPENED |
| user status | ACTIVE / DISABLED / LOCKED |

## dev 种子数据说明（V2__seed_dev.sql，仅 dev location）

- 内容：roles 种子（ADMIN=系统管理员 / MEMBER=普通成员）；测试账号 admin（ADMIN 角色）、user1（MEMBER 角色），status 均为 ACTIVE
- 密码存储：仅 BCrypt(strength 10) 哈希；哈希已用 Spring Security 6.5.2 BCryptPasswordEncoder 与 Python bcrypt 双重验证
- 明文凭据与使用范围见 `docs/development/getting-started.md`——**仅 DEV/测试环境有效，生产环境禁止使用**
- 幂等性：全部 INSERT 带 NOT EXISTS 守卫；Flyway 版本化保证单次执行
