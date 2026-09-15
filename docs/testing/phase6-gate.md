# Phase 6 Release Gate 记录 — Issue Management

> 时间: 2026-09-15 ｜ 基线: 0da3610（P5-04）→ 40bb5c0（P5 Gate）｜ 结论: **GATE PASS**

## Scope

P6-01 数据模型/V9 → P6-02 领域服务 → P6-03 REST API+RBAC → P6-04 查询筛选 → P6-05 状态边界 → P6-06 Java 集成测试 → P6-07 Python 自动化 → P6-08 前端 → P6-09 E2E 回归 → P6-10 本 Gate。

## Implemented Features

- Issue CRUD（嵌套 /projects/{projectId}/issues）+ issue_no 服务端分配 + reporter 自动绑定 + assignee 项目成员约束 + severity 仅 BUG + 分页筛选（keyword/issueNo/四枚举/reporter/assignee/组合）+ 状态与分派端点
- 前端 IssuesView（列表/五维筛选/业务编号/创建编辑对话框/状态下拉/分派下拉/权限 UX）+ 项目行 Issues 入口

## DB Migration

| Migration | 内容 | 验证 |
|---|---|---|
| V9 | ALTER projects ADD issue_seq + issues 表（四枚举/UK(project,issue_no)/5 索引） | flyway success=1，DESCRIBE 与 data-dictionary §10 一致 |
| V10 | issue×5 权限种子 + ADMIN 绑定（系统权限 31→36） | ADMIN 36 权限实测 |

## API（9 端点）

GET / POST /projects/{projectId}/issues；GET / PUT / PATCH …/{issueId}/status；PATCH …/{issueId}/assignee

## RBAC + 数据级权限（双层）

| 场景 | 实测 |
|---|---|
| 未认证 | 401 ✅ |
| 无 issue:* authority | 403 ✅ |
| 有 authority 非项目成员（写） | 403 ✅（数据级，运行时+MockMvc 双验证） |
| ADMIN（成员+全权限） | 200/201 ✅ |
| assignee 非项目成员 | 400 ✅（组织成员不够，须项目成员） |
| assignee 为项目成员 | 201/200 ✅ |

## 测试结果

| 线 | 数量 | 结果 |
|---|---|---|
| Java mvn | **263/263**（Issue 领域 12 + Controller 7 + 既有 244） | PASS |
| Python pytest | **48/48 连续 2 轮**（issue 10 用例真实 HTTP） | PASS |
| 前端 Vitest | **84/84**（IssuesView 7） | PASS |
| lint / build | PASS / PASS | |

## E2E 真实 HTTP 冒烟（新 jar 重建后）

org 873 → user1 入组织+项目 → create BUG/URGENT/S1 **issueNo=1, reporter=1(自动), assignee=2** → get → update → status IN_PROGRESS → filter(type+status) → **assign** → **unassign（修复后验证）** → logout → **cascade cleanup 200**（org 级联删 project+issues+members）

## 修复的真实 Bug

1. **取消分派不生效**（P6-05）：`assigneeId=null` 被 MP `updateById` 非 null 策略忽略 → 改 LambdaUpdateWrapper 显式 set null；补写库断言回归测试；重建 jar 实测 null 落库
2. 两个全局异常处理器改写：非法枚举反序列化 → 422（对齐 P6-06"非法 enum→422"）

## Security checks

未认证 401 ✅｜无权限 403 ✅｜数据级 403 ✅｜非法枚举 422 ✅｜traceId 存在 ✅｜无堆栈/SQL/密码泄漏 ✅｜reporter 无法伪造（DTO 无字段+服务端绑定）✅｜SQL 全参数绑定 ✅

## Known limitations（记录待决策）

- 归档项目建 Issue 未限制（ADR-016.6 待决策项）
- 状态流转矩阵属 Phase 7（本阶段任意合法枚举可设）
- assignee 清空使用哨兵 0（HTTP 层 JSON null→Controller 转 0），Phase 8+ 若开放 API 给第三方建议改 JSON null 语义

## Git commits

`15e981e` P6-01 → `ad55f00` P6-02/04/05 领域 → `68fcd72` P6-03/04/05/06/07 API+Python+测试 → `3b8e856` P6-08 前端 → `f48c5de` fix 取消分派

## Final DoD checklist

V9 Flyway ✅｜CRUD ✅｜issue_no 项目内唯一+并发安全 ✅｜不同项目同号 ✅｜reporter 自动绑定不可伪造 ✅｜assignee 项目成员约束 ✅｜归档待决策已记录 ✅｜权限双层 ✅｜矩阵 401/403/200/201 ✅｜查询筛选全维 ✅｜不可变字段 ✅｜Python 真实 HTTP ✅｜前端真实 API ✅｜Swagger ✅｜清理干净 ✅｜Git clean ✅

# Phase 6 PASS ✅
