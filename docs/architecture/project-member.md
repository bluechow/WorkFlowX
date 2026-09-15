# WorkFlowX 项目成员设计（Phase 5）

> 任务 P5-04 ｜ ADR-015 ｜ 前置: projects(V6)/organization_members(V4)

## 模型（V8__project_members.sql）

```text
projects 1 ──< project_members >── users
              (project+user 复合PK, role, FK CASCADE)
```

- role ENUM(OWNER/MANAGER/MEMBER)：创建项目时 owner_id 用户自动写入 OWNER（同事务，唯一，不可移除/降级）
- **前置规则**：用户必须已是项目所属组织的成员（organization_members 存在）才能被添加为项目成员 → 否则 400
- 删除项目/用户 → 成员关系级联清理（FK CASCADE）

## API（/api/v1/projects/{id}/members）

| 端点 | 方法 | authority | 数据级 |
|---|---|---|---|
| 成员列表 | GET | project:get | —（企业内可见） |
| 添加成员 | POST | project:assign_member | 操作者须为组织成员 |
| 移除成员 | DELETE | project:assign_member | 操作者须为组织成员 |

错误语义：项目/用户不存在 404；重复成员 409；非组织成员/OWNER 保护 400；无 authority 403。

## 后续

Issue（V9）的可见/分配范围以 project_members 为基础；MANAGER/MEMBER 的细粒度能力随 Issue 权限需求再扩展。
