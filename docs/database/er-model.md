# WorkFlowX ER 模型（初稿）

> 阶段: Phase 1 任务 1-4 ｜ 状态: 初稿（随 Phase 2–10 演进，变更须走 Flyway 迁移并同步本文件）
> 规则: Master Prompt §8 ｜ 关联 ADR-003（RBAC）

## 设计约定

1. 命名统一 snake_case；表名复数
2. 主键 `id BIGINT UNSIGNED AUTO_INCREMENT`
3. 时间字段统一 `created_at` / `updated_at`（DATETIME(3)，默认当前时间，updated_at 随更新自动刷新）
4. 状态字段统一 ENUM，取值见数据字典
5. 关联表使用复合主键 + 外键（ON DELETE CASCADE），业务表外键按需评估
6. 字符集 utf8mb4 / utf8mb4_0900_ai_ci

## ER 图

> 实线标注的 5 张表（users / roles / permissions / user_roles / role_permissions）已在 V1 落地；
> 其余为 Phase 2–10 的设计稿（DDL 归属见 migration-plan.md）。

```mermaid
erDiagram
    users ||--o{ user_roles : "拥有"
    roles ||--o{ user_roles : "被分配"
    roles ||--o{ role_permissions : "拥有"
    permissions ||--o{ role_permissions : "被授予"

    users {
        bigint id PK
        varchar username UK
        varchar email UK
        varchar password_hash
        varchar nickname
        enum status "ACTIVE|DISABLED|LOCKED"
        datetime last_login_at
    }
    roles {
        bigint id PK
        varchar code UK "ADMIN|MANAGER|MEMBER..."
        varchar name
        varchar description
    }
    permissions {
        bigint id PK
        varchar code UK "resource:action"
        varchar name
        enum type "MENU|API|BUTTON"
    }
    user_roles {
        bigint user_id PK,FK
        bigint role_id PK,FK
    }
    role_permissions {
        bigint role_id PK,FK
        bigint permission_id PK,FK
    }

    organizations ||--o{ organization_members : "包含"
    users ||--o{ organization_members : "属于"
    organizations ||--o{ projects : "拥有"
    projects ||--o{ project_members : "包含"
    users ||--o{ project_members : "属于"
    projects ||--o{ issues : "包含"
    users ||--o{ issues : "reporter/assignee"
    issues ||--o{ issue_comments : "包含"
    users ||--o{ issue_comments : "发表"
    issues ||--o{ attachments : "包含"
    users ||--o{ notifications : "接收"
    issues ||--o{ issue_status_transitions : "状态历史"
    users ||--o{ audit_logs : "产生"

    organizations {
        bigint id PK
        varchar name
        varchar code UK
        bigint owner_id FK
    }
    organization_members {
        bigint org_id PK,FK
        bigint user_id PK,FK
        enum role "OWNER|ADMIN|MEMBER"
    }
    projects {
        bigint id PK
        varchar name
        varchar key UK "项目标识(如 WFX)"
        bigint org_id FK
        enum status
    }
    project_members {
        bigint project_id PK,FK
        bigint user_id PK,FK
        enum role
    }
    issues {
        bigint id PK
        bigint project_id FK
        varchar title
        text description
        enum type "BUG|TASK|FEATURE|IMPROVEMENT"
        enum priority "LOW|MEDIUM|HIGH|URGENT"
        enum severity "S1|S2|S3|S4(仅BUG)"
        enum status "OPEN|IN_PROGRESS|RESOLVED|TESTING|CLOSED|REOPENED"
        bigint reporter_id FK
        bigint assignee_id FK
    }
    issue_comments {
        bigint id PK
        bigint issue_id FK
        bigint author_id FK
        text content
    }
    attachments {
        bigint id PK
        bigint issue_id FK
        varchar object_key "MinIO 对象键"
        varchar file_name
        bigint file_size
        varchar content_type
        bigint uploader_id FK
    }
    notifications {
        bigint id PK
        bigint recipient_id FK
        varchar type
        varchar title
        varchar content
        tinyint is_read
    }
    issue_status_transitions {
        bigint id PK
        bigint issue_id FK
        enum from_status
        enum to_status
        bigint operator_id FK
        varchar comment
    }
    audit_logs {
        bigint id PK
        bigint user_id FK
        varchar module
        varchar action
        varchar http_method
        varchar uri
        varchar ip
        varchar target
        tinyint success
    }
```

## 关键关系说明

| 关系 | 基数 | 说明 |
|---|---|---|
| users ↔ roles | 多对多 | user_roles 关联表（V1） |
| roles ↔ permissions | 多对多 | role_permissions 关联表（V1） |
| organizations ↔ projects | 一对多 | 项目归属组织 |
| projects ↔ issues | 一对多 | Issue 必属于项目 |
| users ↔ issues | 一对多×2 | reporter 与 assignee 两个引用（不做外键，索引查询） |
| issues ↔ 状态历史 | 一对多 | issue_status_transitions 支撑状态机审计（Phase 7） |

## 与 Master Prompt 的一致性核对

- §14 Issue 类型/优先级/Severity/状态枚举 → issues 表 ENUM（见数据字典）
- §7 RBAC 五层结构 → users/roles/permissions + 两张关联表（V1 已落地）
- §8 避免大 JSON 字段、避免单表堆业务 → 全部实体关系化设计
