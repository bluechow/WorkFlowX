# WorkFlowX 项目上下文（AI_CONTEXT）

| 项 | 值 |
|---|---|
| Version | 1.1 |
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

**Phase 1 — Project Foundation：进行中（2026-09-06）**

- 执行计划已细化：8 个任务（1-1 ~ 1-8）含 Subtask / 验收标准 / 依赖关系 / 验证方式，见 `AI_TASKS.md` 第 3 节
- 环境决策已确认并执行：JDK 21 LTS 已安装（JAVA_HOME=D:\develop\Java\jdk-21）；WSL 2.7.13 已安装；Ubuntu 发行版与 Docker 引擎安装进行中
- 任务进度：1-1 ✅ → 1-2 ~ 1-8 待执行

---

## 3. 业务模块状态

| 模块 | 说明 | 状态 |
|---|---|---|
| auth | 认证（登录 / 登出 / JWT） | ⬜ 未开始（Phase 1 仅建包占位） |
| user | 用户管理 | ⬜ 未开始（Phase 1 仅建包占位） |
| organization | 组织管理 | ⬜ 未开始（Phase 1 仅建包占位） |
| rbac | 角色权限（User-Role-Permission） | ⬜ 未开始（Phase 1 建表） |
| project | 项目管理 | ⬜ 未开始（Phase 1 仅建包占位） |
| issue | Issue 管理 | ⬜ 未开始（Phase 1 仅建包占位） |
| workflow | Issue 状态机 | ⬜ 未开始 |
| comment | 评论 | ⬜ 未开始 |
| attachment | 附件（MinIO） | ⬜ 未开始 |
| notification | 通知 | ⬜ 未开始 |
| audit | 审计日志 | ⬜ 未开始 |
| dashboard | 数据统计 | ⬜ 未开始 |
| system | 系统管理 | 🔵 Phase 1 将落地健康检查等基础能力 |
| common | 统一响应 / 异常 / 基础设施 | 🔵 Phase 1 将落地骨架 |

---

## 4. 环境状态

### 本机工具链（2026-09-06 检查）

| 工具 | 版本 / 状态 |
|---|---|
| JDK | ✅ 21.0.12.1 LTS（Temurin，D:\develop\Java\jdk-21），系统 JAVA_HOME 已切换，Maven 验证通过（P1-ENV-1 方案 A 已执行） |
| Maven | ✅ 3.9.11 |
| Node / npm | ✅ v22.22.3 / 10.9.8（另有 pnpm 11.5.1） |
| Python / pip | ✅ 3.11.4 / 26.1.2 |
| MySQL | ✅ 8.0.43 本机服务 `MySQL80` 运行中（遗留，WorkFlowX 不使用，见 ADR-007） |
| Redis | ❌ 未安装（由 compose 提供，任务 1-2） |
| WSL | ✅ 2.7.13.0（内核 6.18.33.2-2，GitHub MSI 经 gh-proxy 代理安装，VirtualMachinePlatform 原生已启用，无需重启） |
| Docker | ⏳ WSL 内 docker-ce + compose-plugin 安装中（Docker Desktop 官方 CDN 在本网络不可达，采用 WSL 内 docker-ce 替代，功能满足 ADR-007 与 §24，已在验收报告说明） |
| Git | ✅ 2.50.1 |

### 项目环境

| 环境 | 状态 |
|---|---|
| Git 仓库 | ✅ 已初始化（master，2 次提交） |
| 后端骨架 | ⬜ 未搭建（Phase 1 任务 1-3） |
| 前端骨架 | ⬜ 未搭建（Phase 1 任务 1-6） |
| 数据库 | ⬜ 无 schema（Phase 1 任务 1-4 建立 Flyway + V1） |
| 基础设施（MySQL / Redis / MinIO） | ⬜ compose 未建立（Phase 1 任务 1-2，路径待 P1-ENV-2） |
| 测试体系 | ⬜ 未搭建（Phase 1 任务 1-7） |
| CI/CD | ⬜ 未搭建（Phase 17） |

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
├── .gitignore / .gitattributes
├── backend/                # Spring Boot 后端（1-3 填充）
├── frontend/               # Vue 3 前端（1-6 填充）
├── deploy/                 # 部署配置（Phase 16 填充）
├── tests/                  # 自动化测试（api/ui/performance/fixtures/data/utils/config）
└── docs/                   # 文档树（requirements/architecture/database/api/development/testing/deployment）
```

---

## 6. 最近变更

| 日期 | 变更 | 关联 |
|---|---|---|
| 2026-09-06 | 任务 1-1 完成：目录结构与文档树建立并提交（9f502fa） | Phase 1 |
| 2026-09-06 | 环境安装：JDK 21.0.12.1 LTS（JAVA_HOME 切换验证通过）、WSL 2.7.13（无重启）；Docker 引擎安装进行中 | P1-ENV |
| 2026-09-06 | 建立 AI 总控体系：5 份总控文件 + AGENTS.md + .gitignore，初始化 Git 仓库 | Phase 0 |
