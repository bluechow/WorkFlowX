# WorkFlowX 组织架构设计（Phase 4）

> 任务 P4-01/P4-02 ｜ ADR-013 ｜ 后续: Project（V6）将引用 org_id

## 1. 模型（V4__organization_core.sql）

```text
organizations 1 ──< organization_members >── users
      │ 1            (org+user 复合PK, 成员角色/部门归属)
      └────< departments（org_id FK CASCADE; parent_id 自引用 SET NULL）
```

- **organizations**: name / code(UK) / owner_id（创建者，逻辑引用 users）/ description
- **departments**: org_id(FK CASCADE) / parent_id（自引用 FK，NULL=根，删除父部门时子部门 parent 置 NULL）/ name / code（**组织内唯一** UK(org_id, code)）
- **organization_members**: 复合 PK(org_id, user_id)；role ENUM(OWNER/ADMIN/MEMBER)；department_id（FK SET NULL，可空=未分配）；users 删除级联移除成员关系——**users 表零改动**

## 2. 业务规则（P4-02 落地于 Service/API）

| 规则 | 处理 |
|---|---|
| org code 全局唯一 | DB UK + Service 预检 → 409 |
| 创建者即 OWNER | 创建组织事务内写入 organization_members(role=OWNER)；OWNER 唯一且不可在成员管理中移除/降级 |
| 删除组织 | 仅组织 OWNER（operator==owner_id，否则 403）；级联删除部门与成员关系 |
| 部门树防环 | parent 必须同组织、≠自身、且不为自身后代（沿 parent 链上溯检查）→ 400 |
| 部门删除 | 子部门 parent 置 NULL（提升为根），成员 department_id 置 NULL（SET NULL） |
| 成员重复添加 | 幂等（已存在则更新部门/角色不覆盖 OWNER）→ 或 409？设计：添加已存在成员 → 409，避免语义混淆 |
| 数据不存在 | org/department/user 缺失 → 404 |

## 3. 权限（V5 种子，{resource}:{action}，ADR-012 规范）

org:list / org:get / org:create / org:update / org:delete / org:assign_member
department:list / department:get / department:create / department:update / department:delete

- ADMIN 角色绑定全部；系统权限保护清单同步扩充（RbacConstants）
- 组织 OWNER 附加约束（org:delete 的 owner 校验）是权限之外的**数据级业务规则**，两者叠加

## 4. 对后续模块的支撑

- Project（V6）将引用 org_id（项目归属组织）
- Issue/Workflow 经 Project 间接关联组织
- Notification/Audit 引用 user_id，与组织无直接外键
