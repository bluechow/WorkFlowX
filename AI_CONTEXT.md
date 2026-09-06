# WorkFlowX 项目上下文（AI_CONTEXT）

| 项 | 值 |
|---|---|
| Version | 1.2 |
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
| 后端 | Java 21 + Spring Boot 3.5.16 + MyBatis-Plus 3.5.17 + Flyway + Redis + SpringDoc（Security/JWT 属 Phase 2） |
| 前端 | Vue 3 + TypeScript + Vite + Vue Router + Pinia + Axios + Element Plus（ECharts 属 Phase 10） |
| 数据层 | MySQL 8.x + Redis + MinIO（统一 Docker Compose 管理，ADR-007） |
| 测试 | JUnit 5 + MockMvc ｜ Vitest ｜ Python + Pytest + httpx（Playwright/Allure/JMeter 属 Phase 12–14） |
| DevOps | Git + Docker Compose + GitHub Actions（CI 属 Phase 17） |

---

## 2. 当前阶段

**Phase 2 — Authentication & User：进行中（2026-09-06）**

- 决策 D1–D5 用户已确认：保持 users 表命名 / 无 Refresh Token / JWT 2h + Redis 单会话 / 失败 5 次锁 15 分钟 / 种子仅 dev
- **P2-01 ✅ DONE**：User 实体（映射 V1 真实结构）+ UserStatus 枚举 + UserVO（无密码字段）+ dev 种子迁移 V2（db/seed/dev location 隔离，prod 不执行）+ 测试 16 个（mvn test 20/20 全绿含 P1 回归）
- **P2-02 ✅ DONE**：UserMapper（BaseMapper 极简）+ MybatisPlusConfig 分页插件（MySQL；MP 3.5.9+ 已补 mybatis-plus-jsqlparser 依赖）+ UserMapperTest 11 用例（真实 MySQL，SQL 实证 ORDER BY/LIMIT/count 正确）；mvn test 31/31 全绿
- 下一步：P2-03（用户 Service），**等用户指令后执行**
- Phase 1 — Project Foundation 已于 2026-09-06 完成并通过验收（DoD 8/8）

---

## 3. 业务模块状态

| 模块 | 说明 | 状态 |
|---|---|---|
| auth | 认证（登录 / 登出 / JWT） | ⬜ 未开始（Phase 2，包占位已建） |
| user | 用户管理 | ⬜ 未开始（Phase 2，包占位已建） |
| organization | 组织管理 | ⬜ 未开始（Phase 4，包占位已建） |
| rbac | 角色权限（User-Role-Permission） | 🔵 身份域 5 表已建（V1），功能属 Phase 3 |
| project | 项目管理 | ⬜ 未开始（Phase 5，包占位已建） |
| issue | Issue 管理 | ⬜ 未开始（Phase 6，包占位已建） |
| workflow | Issue 状态机 | ⬜ 未开始（Phase 7） |
| comment | 评论 | ⬜ 未开始（Phase 8） |
| attachment | 附件（MinIO） | ⬜ 未开始（Phase 8；MinIO 基础设施已就绪） |
| notification | 通知 | ⬜ 未开始（Phase 9，包占位已建） |
| audit | 审计日志 | ⬜ 未开始（Phase 10，包占位已建） |
| dashboard | 数据统计 | ⬜ 未开始（Phase 10，包占位已建） |
| system | 系统管理 | ✅ 基础能力已落地（健康检查 GET /api/v1/health） |
| common | 统一响应 / 异常 / traceId / OpenAPI 配置 | ✅ 骨架已落地 |

---

## 4. 环境状态

### 本机工具链（2026-09-06 实测）

| 工具 | 版本 / 状态 |
|---|---|
| JDK | ✅ 21.0.12.1 LTS（Temurin，D:\develop\Java\jdk-21），系统 JAVA_HOME 已切换（P1-ENV-1 方案 A） |
| Maven | ✅ 3.9.11（Aliyun 镜像已配置于 ~/.m2/settings.xml） |
| Node / npm | ✅ v22.22.3 / 10.9.8（npmmirror 源已配置） |
| Python / pip | ✅ 3.11.4 / 26.1.2（pytest + httpx 已装，TUNA 源） |
| WSL | ✅ 2.7.13.0（Ubuntu 22.04 发行版，D:\WSL\Ubuntu；vmIdleTimeout 已延长，见 ~/.wslconfig） |
| Docker | ✅ Docker CE 29.8.0 + Compose v5.5.1（WSL 内 systemd 管理；镜像加速三源已配 /etc/docker/daemon.json） |
| 遗留服务 | 本机 MySQL80（3306）与 redis 缺失等历史状态保留原样，WorkFlowX 不使用（ADR-007） |

### 项目环境

| 环境 | 状态 |
|---|---|
| Git 仓库 | ✅ master，Phase 0+1 全部语义化提交 |
| 后端骨架 | ✅ 可构建可启动（workflowx-backend-0.1.0-SNAPSHOT.jar，:8080） |
| 前端骨架 | ✅ 可构建可启动（Vite :5173，/api 代理 8080） |
| 数据库 | ✅ Flyway V1 身份域 5 表已落地；业务表随 Phase 2–10 迁移 |
| 基础设施 | ✅ compose 三容器 healthy（MySQL :3307 / Redis :6379 / MinIO :9000,9001 + workflowx bucket） |
| 测试体系 | ✅ 三层可运行（mvn test 4/4 · npm test 2/2 · pytest 2/2） |
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
├── AI_DECISIONS.md         # 技术决策记录（ADR，含 ADR-007）
├── README.md               # 项目说明与快速启动
├── compose.yaml            # 本地基础设施（MySQL/Redis/MinIO，ADR-007）
├── .env.example            # 环境变量模板（.env 不提交）
├── .gitignore / .gitattributes / pytest.ini
├── backend/                # Spring Boot 后端（Maven，Java 21）
│   └── src/main/java/com/workflowx/{common,system,auth,user,...}
├── frontend/               # Vue 3 + TS + Vite
│   └── src/{api,components,views,stores,router,utils,types}
├── deploy/                 # 部署配置（Phase 16 填充）
├── tests/                  # 自动化测试（api/ui/performance/fixtures/data/utils/config）
└── docs/                   # 文档树（requirements/architecture/database/api/development/testing/deployment）
```

---

## 6. 最近变更

| 日期 | 变更 | 关联 |
|---|---|---|
| 2026-09-06 | P2-01 完成：User 实体/UserStatus/UserVO + V2 dev 种子（admin/user1 + ADMIN/MEMBER，BCrypt 双重验证）+ 16 测试，mvn 20/20 全绿 | Phase 2 |
| 2026-09-06 | Phase 2.0 规划：现状核查 + P2-01~P2-25 任务拆解 + 认证方案基线 | Phase 2 |
| 2026-09-06 | 任务 1-8 完成：README.md + getting-started.md（按实测过程编写）；Phase 1 全部 8 任务 DONE，输出最终验收报告 | Phase 1 |
| 2026-09-06 | 任务 1-7 完成：三层测试能力（mvn 4/4 · npm 2/2 · pytest 2/2） | Phase 1 |
| 2026-09-06 | 任务 1-6 完成：前端基础工程（lint/test/build/代理链路实测通过） | Phase 1 |
| 2026-09-06 | 任务 1-5 完成：SpringDoc + API 设计约定文档 | Phase 1 |
| 2026-09-06 | 任务 1-4 完成：ER 模型/数据字典/迁移计划 + Flyway V1 身份域 5 表 | Phase 1 |
| 2026-09-06 | 任务 1-3 完成：后端基础工程（构建/启动/health/404 实测通过） | Phase 1 |
| 2026-09-06 | 任务 1-2 完成：compose 基础设施（三容器 healthy + 连通性验证 + 自动恢复验证） | Phase 1 |
| 2026-09-06 | 任务 1-1 完成：目录结构与文档树 | Phase 1 |
| 2026-09-06 | 环境安装：JDK 21.0.12.1（JAVA_HOME 切换）、WSL 2.7.13 + Ubuntu + docker-ce 29.8（TUNA/gh-proxy 镜像方案） | P1-ENV |
| 2026-09-06 | 记录环境决策 P1-ENV-1/2（方案 A），新增 ADR-007 | P1-ENV |
| 2026-09-06 | 完成 Phase 1 环境检查与执行计划细化（8 任务/31 Subtask） | Phase 1 |
| 2026-09-06 | 建立 AI 总控体系（Phase 0） | Phase 0 |
