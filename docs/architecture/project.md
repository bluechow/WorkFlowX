# WorkFlowX 项目管理设计（Phase 5）

> 任务 P5-01/P5-02 ｜ ADR-014 ｜ 上游: organizations（V4）/ RBAC（Phase 3）

## 1. 模型（V6__project_core.sql）

```text
organizations 1 ──< projects
users（owner_id 逻辑引用，负责人/创建者合一）
```

| 字段 | 说明 |
|---|---|
| `key` VARCHAR(20) UK | 项目标识，全局唯一，Issue 编号前缀（如 WFX-1 的 WFX）；正则 `^[A-Z][A-Z0-9]{1,19}$` |
| org_id FK CASCADE | 项目归属组织；组织删除级联删除项目 |
| status ENUM(ACTIVE/ARCHIVED) | **归档策略**：无物理删除；归档后只读（后续 Issue 等模块继承） |
| owner_id | 创建者/负责人（逻辑引用） |

索引：UK(key)、idx(org_id, status)（组织内按状态过滤是主查询路径）、idx(owner_id)。

## 2. 业务规则

| 规则 | 处理 |
|---|---|
| key 全局唯一 | DB UK + Service 预检 → 409 |
| key 不可修改 | update 仅 name/description |
| org 必须存在 | 404 |
| **数据级归属** | create/update/归档要求操作者为项目所属组织成员（organization_members 存在，含 OWNER）→ 否则 403；authority（project:*）与归属叠加（ADR-014） |
| 归档 | PATCH /projects/{id}/status；归档后可恢复 ACTIVE（同端点） |
| 排序 | created_at DESC, id DESC 稳定排序；keyword 匹配 name/`key`；status 过滤 |

## 3. 权限（V7 种子）

project:list / project:get / project:create / project:update / project:delete

- ADMIN 全量绑定；`project:delete` 保留 authority 位（当前无物理删除端点，预留给后续清理策略）
- **数据级规则叠加**（ADR-014）：写操作（create/update/status）额外要求操作者是目标组织成员

## 4. 对后续模块支撑

- project_members（P5 后续）、issues（V8）将引用 project_id；`key`+issue_no 组成业务编号
