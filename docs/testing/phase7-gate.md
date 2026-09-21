# Phase 7 Release Gate 记录 — Workflow

> 时间: 2026-09-21 ｜ 基线: ba90b1d（Phase 6 Gate）→ f7776c7（P7-08）｜ 结论: **GATE PASS**

## Scope

P7-01 状态矩阵（ADR-017）→ P7-02 WorkflowService 领域层 → P7-03 issue:transition 权限（V11）→ P7-04 Transition API → P7-05 并发保护 → P7-06 Java 集成测试 → P7-07 Python 自动化 → P7-08 前端 Workflow UX → P7-09 E2E → P7-10 本 Gate。

## Implemented Features

- **状态转换矩阵**（ADR-017，唯一合法集合 6 条）：OPEN→IN_PROGRESS→RESOLVED→TESTING→CLOSED 主链 + TESTING→REOPENED→IN_PROGRESS 失败回路；CLOSED 唯一终态（无出边）；禁自环、禁跳转、禁相同状态重复流转
- **WorkflowService 领域服务**：状态机收敛于 Service 层（Controller 零状态机逻辑）；非法流转 409 并携带当前状态与允许目标列表
- **issue:transition authority**：V11 种子 + ADMIN 绑定（系统权限 36→37）；PATCH status 从 issue:update 迁移为独立受控权限；数据级仍要求操作者为项目成员（双层校验）
- **并发保护**：条件 UPDATE（`WHERE status = fromStatus`）乐观并发，无 version 字段；fromStatus 必填（缺省 422）；并发冲突 → 409
- **前端 Workflow UX**：状态列按 `allowedTargets(from)` 渲染合法目标下拉（镜像常量 VALID_TRANSITIONS）；issue:transition 权限 gating；transitioning 防重复提交
- **E2E 脚本归档**：tests/e2e/phase7_workflow_e2e.sh（严格断言 22 项 + 幂等预清理）

## DB Migration

| Migration | 内容 | 验证 |
|---|---|---|
| V11 | issue:transition 权限种子 + ADMIN 绑定（系统权限 36→37） | ADMIN 37 权限实测；RbacDtoValidationTest 计数同步 |

## API

PATCH /api/v1/projects/{projectId}/issues/{issueId}/status

请求体 `{fromStatus, toStatus}`（均必填，非法枚举 422；fromStatus 缺失 422——并发保护前提）。

## 状态矩阵与实测

| 流转 | 结果 |
|---|---|
| OPEN→IN_PROGRESS→RESOLVED→TESTING→CLOSED 主链逐步 | 200 ✅ |
| TESTING→REOPENED→IN_PROGRESS 回路 | 200 ✅ |
| TESTING→CLOSED（回路上继续闭合） | 200 ✅ |
| IN_PROGRESS→CLOSED（跳过 RESOLVED/TESTING） | **409** ✅ |
| OPEN→CLOSED / 非法枚举目标 | 409 / 422 ✅ |
| 同状态自环（from==to） | **409** ✅ |
| CLOSED→IN_PROGRESS（终态出边） | **409** ✅ |
| fromStatus=OPEN 但当前已 IN_PROGRESS（8 并发 stale-from） | **8×409，0×200** ✅ |

## RBAC + 数据级权限（双层）

| 场景 | 实测 |
|---|---|
| 未认证 | 401 ✅ |
| 项目成员但无 issue:transition authority | **403** ✅（E2E + Python 双验证） |
| 有 authority 非项目成员 | **403** ✅（数据级先于目标校验） |
| 有 authority + 项目成员 | 200 ✅（授权后重新登录生效，E2E 9a/9b 实证） |
| 跨项目 issueId | 404 ✅ |

## 并发保护实现

`IssueMapper.transitionStatus`：`UPDATE issues SET status=#{toStatus}, updated_at=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND status=#{fromStatus}`——affected=0 即并发冲突 → 409。Java 侧 WorkflowConcurrencyTest 8 线程同时 OPEN→IN_PROGRESS：**successes=1, conflicts=7**；E2E 侧 8 请求 stale-from：**8×409**。无 MAX+1 类方案，无 version 字段。

## 测试结果（关键测试连续两轮）

| 线 | 第一轮 | 第二轮 | 结果 |
|---|---|---|---|
| Java mvn | **264/264**（含 WorkflowConcurrencyTest + 矩阵/权限迁移用例） | **264/264** | PASS |
| Python pytest | **60/60**（workflow 12 用例真实 HTTP） | **60/60** | PASS |
| 前端 Vitest | **89/89**（IssuesWorkflow spec 5 用例） | **89/89** | PASS |
| lint / build | PASS / PASS | — | PASS |
| E2E（tests/e2e/phase7_workflow_e2e.sh） | **22/22 ALL PASS** | —（幂等设计） | PASS |

## E2E 真实 HTTP 冒烟（22 断言全绿）

预清理残留角色 → admin/member 登录 → org → org member → project → project member → issue（issueNo=1, reporter=1, status=OPEN）→ **无权限 403** → 授予 issue:transition（role+permission+binding）→ 重新登录 → **流转 200** → 8× stale-from **全 409** → 跳转 **409** → 主链 7 步全 200（含 REOPENED 回路）→ CLOSED 终态 **409** → cleanup（org 删除 + 角色解绑/删除，残留=0）

## 过程中修复的问题

1. **E2E 脚本 project key 含下划线**（`P7E2EP_$SS`）→ 422 静默失败 → PID/IID 空值 → 后续路径畸变返回 401 假象。修复：key 改无下划线 + 全步骤严格断言（失败立即暴露，禁止空值向后传播）。
2. **测试残留角色污染权限场景**：历次运行绑定在 user1 上的 issue:transition 测试角色使"无权限 403"前置失效。修复：脚本内置幂等预清理（解绑+删角色）与结束后回收，并断言残留=0。
3. 以上均为**测试脚本自身缺陷**，产品代码未发现缺陷（矩阵/并发/权限逻辑一次通过）。

## Security checks

未认证 401 ✅｜无 authority 403 ✅｜数据级（非项目成员）403 ✅｜非法流转 409（不泄露内部状态机以外信息）✅｜非法枚举 422 ✅｜traceId 存在 ✅｜无堆栈/SQL/密码泄漏 ✅｜SQL 全参数绑定 ✅｜状态机服务端强制（前端 allowedTargets 仅为 UX，绕过 UI 直调 API 仍受矩阵约束）✅

## Known limitations（记录待决策）

- 流转暂无 reason/备注字段（ADR-017.7 决策不新增；Phase 8 Comment / Phase 10 Audit 承接）
- 流转暂无审计日志（Phase 10 Audit & Dashboard 覆盖）
- 前端 VALID_TRANSITIONS 为后端矩阵的镜像常量，矩阵变更时需双处同步（单源 API 方案留待 Phase 11 评估）

## Git commits

`6fff430` P7-01～05 矩阵+API → `fa90a06` P7-05 并发测试 → `549d68f` P7-07 Python 12 用例 → `f7776c7` P7-08 前端修复 → 本 Gate 提交（gate 文档+E2E 归档+总控收口）

## Final DoD checklist

ADR-017 正式矩阵 ✅｜状态机收敛 Service 层 ✅｜issue:transition 权限+双层校验 ✅｜fromStatus 并发保护（条件 UPDATE）✅｜非法流转 409（无 200 假成功）✅｜CLOSED 终态/REOPENED 回路/禁跳转/禁自环 ✅｜Java 矩阵+权限+隔离+并发+错误全覆盖 ✅｜Python 真实 HTTP 12 用例 ✅｜前端合法目标下拉+权限 UX ✅｜E2E 全链 22 断言 ✅｜两轮关键测试全绿 ✅｜Swagger ✅｜清理干净（users=2、角色残留=0）✅｜Git clean（本 Gate 提交后）✅

# Phase 7 PASS ✅
