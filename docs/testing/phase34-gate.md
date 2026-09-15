# Phase 3 + Phase 4 Release Gate 记录

> 时间: 2026-09-15 ｜ 基线: 86d4372（P4-03）｜ 结论: **GATE PASS**

## Gate 范围

Phase 3（RBAC: P3-01~P3-05）与 Phase 4（组织架构: P4-01~P4-03）全部交付物的正式验收。

## 验证矩阵

| 维度 | 项 | 结果 |
|---|---|---|
| Java | mvn test 全量 | **214/214**（RBAC 46 + 组织 26 + 既有 142 全绿） |
| Python | pytest 全量 | **43/43 连续 2 轮**（RBAC 10 + 组织 6 + Phase 2 27） |
| 前端 | Vitest | **70/70**（新增组织 12） |
| 前端 | lint / build | PASS / PASS |
| 真实 HTTP | 组织冒烟 | 201 创建(OWNER 自动)→member 403→部门自环 400→OWNER 删 200；RBAC 授权/收权即时生效（历史实测保持） |
| 真实 HTTP | 认证链 | 登录/me/登出 正常，权限矩阵不变 |
| Swagger | OpenAPI 同步 | org/departments 6 路径生成；login/health 空 security；bearerAuth=JWT；无 passwordHash 泄漏 |
| MySQL | 数据残留 | organizations=0、departments=0、members=0、users=2（仅 seed）、测试角色=0 |
| Redis | 键残留 | auth:* 全部清零（session/fail key） |
| Git | 工作树 | clean |

## Phase 3 核对

RBAC CRUD/Permission/RolePermission/UserRole ✅｜authority 实时接线（收权即时生效）✅｜系统角色/权限保护（400）✅｜前端权限 UX（按钮级 + /me/permissions）✅｜Python API ✅｜Java 回归 ✅

## Phase 4 核对

Organization/Department/OrganizationMember CRUD ✅｜OWNER 数据级权限（authority 之外叠加 403）✅｜部门树防环 ✅｜唯一性（org code UK / dept(org,code) UK）✅｜删除规则（级联+SET NULL）✅｜前端组织管理（列表/详情/部门树/成员）✅｜Python API ✅｜Java integration ✅｜Phase 2/3 回归 ✅

## 过程问题与修复

| # | 问题 | 处理 |
|---|---|---|
| 1 | pytest org 树用例 KeyError：`parentId=null` 被 NON_NULL 序列化省略 | 测试改 `.get('parentId') is None`（产品行为正确） |
| 2 | cleanup_orgs 用 admin 删"非 owner 组织"403 被吞 → 7 个残留组织 | 用例改为 OWNER 自删（补 owner 路径断言）+ SQL 清残留；以后“他人组织”一律 owner 自清 |
| 3 | 前端 org spec 的 vue-router 全量 mock 破坏 @/router→http 依赖链 | 改最小 stub（createRouter/createWebHistory） |

## 结论

**Phase 3 与 Phase 4 均 PASS，放行 Phase 5 — Project Management。**
