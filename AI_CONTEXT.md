# WorkFlowX 项目上下文（AI_CONTEXT）

| 项 | 值 |
|---|---|
| Version | 1.0 |
| 最后更新 | 2026-09-06 |
| 定位 | 项目当前状态的**唯一事实来源**，AI 每次开始工作前必须先读本文件 |
| 更新规则 | 每个任务完成后必须更新本文件（第 2/3/4/6 节），由 AI_WORKFLOW.md 第 5 节约束 |

---

## 1. 项目快照

| 项 | 值 |
|---|---|
| 项目名称 | WorkFlowX |
| 类型 | 企业级项目协作与工单管理平台 |
| 定位 | 面向中小型团队的项目管理、Issue 管理、团队协作、权限控制、通知、审计和数据统计平台 |
| 架构 | Modular Monolith（模块化单体），详见 ADR-001 |
| 后端 | Java 21 + Spring Boot 3.x + Spring Security + MyBatis-Plus + JWT |
| 前端 | Vue 3 + TypeScript + Vite + Vue Router + Pinia + Axios + Element Plus + ECharts |
| 数据层 | MySQL 8.x + Redis + MinIO |
| 测试 | Python + Pytest + Requests/HTTPX + Playwright + Allure + JMeter（独立于业务代码） |
| DevOps | Git + Docker Compose + GitHub Actions |

---

## 2. 当前阶段

**Phase 0 — Project Governance：已完成（2026-09-06）**

本阶段产出：五份总控文件 + `AGENTS.md` 入口 + `.gitignore` + Git 仓库初始化。

**下一阶段：Phase 1 — Project Foundation**（项目骨架与开发环境搭建，任务清单见 `AI_TASKS.md`）。

---

## 3. 业务模块状态

| 模块 | 说明 | 状态 |
|---|---|---|
| auth | 认证（登录 / 登出 / JWT） | ⬜ 未开始 |
| user | 用户管理 | ⬜ 未开始 |
| organization | 组织管理 | ⬜ 未开始 |
| rbac | 角色权限（User-Role-Permission） | ⬜ 未开始 |
| project | 项目管理 | ⬜ 未开始 |
| issue | Issue 管理 | ⬜ 未开始 |
| workflow | Issue 状态机 | ⬜ 未开始 |
| comment | 评论 | ⬜ 未开始 |
| attachment | 附件（MinIO） | ⬜ 未开始 |
| notification | 通知 | ⬜ 未开始 |
| audit | 审计日志 | ⬜ 未开始 |
| dashboard | 数据统计 | ⬜ 未开始 |
| system | 系统管理 | ⬜ 未开始 |

---

## 4. 环境状态

| 环境 | 状态 |
|---|---|
| Git 仓库 | ✅ 已初始化 |
| 后端骨架 | ⬜ 未搭建 |
| 前端骨架 | ⬜ 未搭建 |
| 数据库 | ⬜ 未初始化（无 schema / 迁移脚本） |
| 基础设施（Docker：MySQL / Redis / MinIO） | ⬜ 未搭建 |
| 测试体系 | ⬜ 未搭建 |
| CI/CD | ⬜ 未搭建 |

---

## 5. 目录结构（当前）

```text
WorkFlowX/
├── AGENTS.md               # AI 工具入口（自动加载）
├── AI_MASTER_PROMPT.md     # 永久规则
├── AI_WORKFLOW.md          # AI 工作方式与 STOP 条件
├── AI_CONTEXT.md           # 本文件：当前状态
├── AI_TASKS.md             # 任务清单
├── AI_DECISIONS.md         # 技术决策记录（ADR）
└── .gitignore
```

---

## 6. 最近变更

| 日期 | 变更 | 关联 |
|---|---|---|
| 2026-09-06 | 建立 AI 总控体系：5 份总控文件 + AGENTS.md + .gitignore，初始化 Git 仓库 | Phase 0 |
