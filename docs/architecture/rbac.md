# WorkFlowX RBAC 设计（Phase 3）

> 任务 P3-01 ｜ 上位规则: Master Prompt §7、ADR-003/008、ADR-012 ｜ 关联: docs/architecture/security.md

## 1. 模型（沿用 V1 五表，零结构变更）

```text
users ──< user_roles >── roles ──< role_permissions >── permissions
        (复合PK,FK CASCADE)      (复合PK,FK CASCADE)
```

- users / roles / permissions / user_roles / role_permissions 均为 V1 定稿表
- roles: code(UK, 50) / name / description
- permissions: code(UK, 100) / name / type ENUM(MENU, API, BUTTON) / description
- 关联表复合主键 + ON DELETE CASCADE（删角色/权限级联清理绑定）

## 2. 权限编码规范

```
{resource}:{action}
```

- resource / action 均为小写 snake_case（`^[a-z][a-z0-9_]{1,49}:[a-z][a-z0-9_]{1,49}$`）
- action 词汇表: list / get / create / update / delete / status / assign_role / assign_permission
- 示例: `user:list`、`issue:delete`、`user:assign_role`
- 禁止三段式编码；资源新增时先在本文档登记再落库

## 3. 系统角色

| code | 名称 | 约束 |
|---|---|---|
| ADMIN | 系统管理员 | 系统角色：不可删除、code 不可改；绑定全部系统权限 |
| MEMBER | 普通成员 | 系统角色：不可删除；Phase 3 暂不绑定管理权限（业务权限随 Phase 4+ 扩展） |

系统角色清单由代码常量守护（RoleService.SYSTEM_ROLE_CODES），不依赖数据库标记列。

## 4. 系统权限（V3__rbac_permissions.sql 种子，全部 API 类型）

| code | 说明 |
|---|---|
| user:list / user:get / user:create / user:update / user:status | 用户管理 |
| user:assign_role | 给用户分配/回收角色 |
| role:list / role:get / role:create / role:update / role:delete | 角色管理 |
| role:assign_permission | 给角色分配/回收权限 |
| permission:list / permission:get | 权限查询（只读） |

- 14 项为**系统权限**（PermissionService.SYSTEM_PERMISSION_CODES 守护，禁止删除）
- 初始化绑定: ADMIN → 全部 14 项；MEMBER → 无
- 种子幂等（NOT EXISTS 守卫），位于公共 migration（产品数据，dev/prod 均执行）

## 5. 与 Spring Security 的衔接（现状 → 演进）

**现状（Phase 2，保持不动）**:

```text
登录 → AuthRoleQueryMapper.findRoleCodesByUserId → JWT roles claim
请求 → JwtAuthenticationFilter: roles → authorities = ROLE_{code}
端点 → @PreAuthorize("hasRole('ADMIN')")
```

**本阶段新增（P3-02 领域能力）**:

- `PermissionService.findPermissionCodesByUserId(userId)` — user_roles ⋈ role_permissions ⋈ permissions 实时查询
- Role/Permission/UserRole 三个领域 Service（CRUD/绑定，事务 + 唯一约束 409 + 系统保护）

**演进路径（P3-03 已落地）**:

```text
JwtAuthenticationFilter（每个认证请求）
    ├─ roles claim → authorities += ROLE_{code}        （角色 authority，兼容 hasRole）
    └─ PermissionService.findPermissionCodesByUserId
        (user_roles ⋈ role_permissions ⋈ permissions 实时查询)
        → authorities += {resource}:{action}            （权限 authority，配 hasAuthority）
端点注解: @PreAuthorize("hasAuthority('user:create')") 等（UserController 已全部迁移；
          RbacController 全部使用 hasAuthority；无任何端点保留 hasRole 作为唯一防线）
```

- 权限以服务端实时查询为准——**授权/收权即时生效，无需重签 token**（运行时实测：同一 token 授予权限 403→200、回收 200→403）
- AuthRoleQueryMapper 已删除统一到 rbac 模块 UserRoleService（登录 roles 数据源，SQL 等价，AuthLogin 集成测试全量回归验证）

## 6. 约束汇总

| 约束 | 层 | 说明 |
|---|---|---|
| roles.code / permissions.code 唯一 | DB UK + Service 预检 | 并发冲突 DuplicateKeyException → 409（沿用 UserServiceImpl 模式） |
| 角色删除级联清理绑定 | DB FK CASCADE | 系统角色代码层拒绝删除（400） |
| 系统权限禁止删除 | Service 常量守护 | 14 项种子权限 |
| 关联操作幂等 | Service | 重复分配角色/权限不报错（INSERT IGNORE 语义，查重后插入） |
| code 不可修改 | Service | update 仅允许 name/description |
