# WorkFlowX 任务清单（AI_TASKS）

| 项 | 值 |
|---|---|
| Version | 1.1 |
| 定位 | 当前任务清单的**唯一事实来源** |
| 关联规则 | 任务完成标准见 `AI_MASTER_PROMPT.md` §28，状态流转约束见 `AI_WORKFLOW.md` |

---

## 1. 任务维护规则

1. 任务状态：`TODO` / `DOING` / `BLOCKED` / `DONE`
2. 每个时刻**至多一个** `DOING` 任务
3. `BLOCKED` 必须注明原因（通常对应 `AI_WORKFLOW.md` 的 STOP 条件 S1–S7）
4. 任务标记 `DONE` 必须满足 Master Prompt §28 Definition of Done；未满足的只能保持 `DOING`
5. 任务完成时同步更新 `AI_CONTEXT.md`
6. 禁止删除历史任务记录：完成的任务保留在对应 Phase 清单中，作为项目演进痕迹

---

## 2. Phase 0 — Project Governance（已完成 ✅）

- **目标**：建立可约束整个项目生命周期的 AI 总控体系
- **输入**：用户提供的 Master Prompt 与治理层设计
- **验收标准**：总控文件齐备、AGENTS.md 入口生效、Git 仓库初始化完成

| # | 任务 | 状态 | 备注 |
|---|---|---|---|
| 0-1 | 编写 `AI_MASTER_PROMPT.md`（永久规则） | ✅ DONE | 2026-09-06 |
| 0-2 | 编写 `AI_WORKFLOW.md`（工作流 + STOP 条件 + 自主边界） | ✅ DONE | 2026-09-06 |
| 0-3 | 编写 `AI_CONTEXT.md`（项目状态快照） | ✅ DONE | 2026-09-06 |
| 0-4 | 编写 `AI_TASKS.md`（本文件） | ✅ DONE | 2026-09-06 |
| 0-5 | 编写 `AI_DECISIONS.md`（初始 ADR） | ✅ DONE | 2026-09-06 |
| 0-6 | 编写 `AGENTS.md` 入口 + `.gitignore` | ✅ DONE | 2026-09-06 |
| 0-7 | 初始化 Git 仓库并完成首次提交 | ✅ DONE | 2026-09-06 |

---

## 3. Phase 1 — Project Foundation（进行中：执行计划已细化，编码未启动）

- **目标**：搭建可运行的前后端基础工程与本地基础设施，形成"一键启动、可验证"的开发环境底座
- **输入**：Phase 0 总控体系；Master Prompt §3/§4/§8/§9/§11/§19/§24/§27；ADR-001/002/005
- **明确排除**（ADR-001，禁止引入）：Nacos、Sentinel、Seata、Kafka/RocketMQ、Kubernetes 及任何微服务组件；Nginx 反向代理延后至 Phase 16；ECharts 延后至 Phase 10（Product First，禁止装而不用）

### Phase 级验收标准（Definition of Done）

1. `docker compose up -d` 一键启动 MySQL / Redis / MinIO，三容器健康检查通过（或按 3.0 决策走原生路径）
2. 后端 `mvn spring-boot:run` 可启动；`GET /api/v1/health` 返回 ADR-005 统一响应结构（code/message/data/timestamp/traceId）
3. 前端 `npm run dev` 可启动；健康状态页可展示后端状态，覆盖 Loading / Success / Error 三态（§17）
4. Flyway 首次迁移自动执行成功，表结构与设计文档一致
5. 三层基础验证全绿：`mvn test`、`npm run lint && npm run build && npm run test`、`pytest tests/api/test_health.py`（对运行中的后端）
6. README.md 与 getting-started 文档按步骤可复现
7. `docs/` 七个子目录建立且各有实质内容；`tests/` 骨架符合 §19
8. 无任何 ADR 之外的技术组件引入

### 3.0 环境前置确认（2026-09-06 本机检查结果）

| 项 | 状态 | 影响 |
|---|---|---|
| JDK | ❗ 仅 JDK 17.0.11（JAVA_HOME）与 JDK 22，**无 Java 21 LTS** | 任务 1-3 编译目标 Java 21，需先落定（决策项 P1-ENV-1） |
| Maven | ✅ 3.9.11 | — |
| Node / npm | ✅ v22.22.3 / 10.9.8（另有 pnpm 11.5.1） | — |
| Python / pip | ✅ 3.11.4 / 26.1.2 | — |
| MySQL | ✅ 8.0.43 本机服务 `MySQL80` 正在运行 | 无 Docker 也可做数据库联调 |
| Redis | ❌ 未安装 | 依赖 1-2 提供实例 |
| Docker / WSL | ❌ 均未安装 | 任务 1-2 的 compose 验收被阻塞（决策项 P1-ENV-2） |
| Git | ✅ 2.50.1 | — |

**决策结果（2026-09-06 用户确认，均为方案 A）：**

- **P1-ENV-1 → 方案 A**：安装 JDK 21 LTS 并将 `JAVA_HOME` 配置为 JDK 21；不采用 JDK 22；不修改 ADR-002
- **P1-ENV-2 → 方案 A**：安装 WSL2 + Docker Desktop；MySQL / Redis / MinIO 统一优先通过 Docker Compose 管理；不采用 Windows 原生 Redis/MinIO 作为正式开发基础设施（已记录 **ADR-007**）
- 执行约束：编码前必须重新验证 JDK 21 / WSL2 / Docker 可用性；验证通过后任务 1-2、1-3 按 compose 路径执行

---

### 1-1 仓库目录结构与文档树

- 状态: ✅ DONE（2026-09-06）｜ 依赖: 无 ｜ 验证方式: `git ls-files` 与目录树检查
- Subtask DoD: 目录结构符合 Master Prompt §27；无业务代码混入

| # | Subtask | 验收标准 | 状态 |
|---|---|---|---|
| 1-1.1 | 顶层目录 `backend/ frontend/ tests/ docs/ deploy/` | 全部建立并纳入 git 跟踪（占位 README） | ✅ |
| 1-1.2 | `tests/` 子结构 | 按 §19：api / ui / performance / fixtures / data / utils / config 七目录 | ✅ |
| 1-1.3 | `docs/` 七子目录 + 各目录 README | requirements / architecture / database / api / development / testing / deployment，每个 README 说明职责与当前状态 | ✅ |
| 1-1.4 | `.gitattributes` | 统一 eol 策略（规避 Windows CRLF 问题） | ✅ |

### 1-2 Docker Compose 本地基础设施（MySQL / Redis / MinIO）

- 状态: ✅ DONE（2026-09-06）｜ 依赖: 1-1 ｜ 验证方式: `docker compose ps` 三服务 healthy + 连通性命令（mysql 连接 / `redis-cli ping` / MinIO health 端点）
- Subtask DoD: 一键启动、可健康检查、可看日志、可重启（§24 基础设施部分）

| # | Subtask | 验收标准 | 状态 |
|---|---|---|---|
| 1-2.1 | 根目录 `compose.yaml`（dev profile） | 从项目根 `docker compose up -d` 即可启动 | ✅ |
| 1-2.2 | MySQL 8 服务 | 3307→3306 端口（本机 3306 被遗留服务占用，ADR-007）、utf8mb4、时区、数据卷、healthcheck（mysqladmin ping） | ✅ |
| 1-2.3 | Redis 7 服务 | 6379 端口、healthcheck（redis-cli ping）、appendonly 持久化 | ✅ |
| 1-2.4 | MinIO 服务 | 9000 API / 9001 Console、数据卷、healthcheck（mc ready）、minio-init 自动创建 `workflowx` bucket | ✅ |
| 1-2.5 | `.env.example` + 变量化配置 | 无硬编码密码（§29-9，compose 中使用 `${VAR:?}` 强制注入）；`.env` 已被 gitignore | ✅ |

验收实测（2026-09-06）：三容器 `healthy`；`redis-cli ping`=PONG；Windows mysql 客户端经 3307 连接成功且 `workflowx` 库存在；MinIO health 200；bucket 创建成功；经守护进程重启后容器自动恢复（restart: unless-stopped 生效）。
另注：WSL 虚拟机默认空闲 60s 被回收（vmIdleTimeout），已配置 `~/.wslconfig` 延长并在文档中说明。

### 1-3 后端基础工程（Java 21 + Spring Boot 3.x）

- 状态: ✅ DONE（2026-09-06）｜ 依赖: 1-1、1-2（联调）｜ 验证方式: `mvn clean package` + 本地启动 + `curl /api/v1/health` + `mvn test`
- Subtask DoD: 编译成功、可启动、健康检查符合 ADR-005、无硬编码配置

| # | Subtask | 验收标准 | 状态 |
|---|---|---|---|
| 1-3.1 | Maven 工程 + pom（Java 21 / SB 3.5.16 / web / validation / MyBatis-Plus 3.5.17（spring-boot3-starter）/ mysql-connector-j / data-redis / flyway + flyway-mysql / lombok / test） | `mvn clean package` 成功（42MB boot jar，一次通过） | ✅ |
| 1-3.2 | 模块分包骨架：common、system 有实现，auth/user/organization/project/issue/notification/audit/dashboard 为占位包（package-info 标注归属 Phase） | 包结构与 Master Prompt §4 一致 | ✅ |
| 1-3.3 | 统一响应 `Result<T>`（code/message/data/timestamp/traceId，record 实现）+ 全局异常处理 + 异常族（Base/Business/Authentication/Authorization/ResourceNotFound/Validation） | 任意异常返回统一结构；404 实测无堆栈泄漏 | ✅ |
| 1-3.4 | traceId：TraceIdFilter（复用上游 X-Trace-Id 或生成 UUID → MDC + 响应头） | 响应头与响应体 traceId 实测一致 | ✅ |
| 1-3.5 | 配置分层 application.yml / -dev / -prod，dev 默认值指向 compose 基础设施，prod 全环境变量强制注入 | 无硬编码环境配置（§29-9） | ✅ |
| 1-3.6 | logback-spring：四级日志、SizeAndTimeBased 滚动、traceId 进 pattern、UTF-8 | logs/workflowx-backend.log 正常产出 | ✅ |
| 1-3.7 | 健康检查：`GET /api/v1/health`（统一结构）+ Actuator health（show-details: never） | 实测 200 + 统一结构；/actuator/health = {"status":"UP"} | ✅ |
| 1-3.8 | DataSource / MyBatis-Plus / Redis 连接配置（dev 指向 compose 实例） | Actuator UP 隐含 DB+Redis 健康检查通过 | ✅ |

### 1-4 数据库设计初稿

- 状态: ✅ DONE（2026-09-06）｜ 依赖: 1-2（MySQL 实例）、1-3.8（DataSource）｜ 验证方式: 应用启动 Flyway 自动迁移成功 + `DESCRIBE` 核对表结构 + 文档一致性核对
- Subtask DoD: 设计与 Master Prompt §8 及 ADR-003（RBAC 结构）一致；如设计评审发现既有冲突，按 AI_WORKFLOW STOP 规则处理，禁止静默修改

| # | Subtask | 验收标准 | 状态 |
|---|---|---|---|
| 1-4.1 | ER 设计文档 `docs/database/er-model.md`（Mermaid ER 图） | 覆盖 15 张核心实体，关系与 §14 枚举、ADR-003 RBAC 对齐 | ✅ |
| 1-4.2 | 数据字典 `docs/database/data-dictionary.md` | 全部 15 表字段/约束/索引/枚举说明；V1 表与 DESCRIBE 实测一致 | ✅ |
| 1-4.3 | Flyway 机制 + `V1__identity_core.sql` | 迁移自动执行成功（flyway_schema_history success=1），users/roles/permissions/user_roles/role_permissions 五表建立 | ✅ |
| 1-4.4 | 后续表 DDL 归属计划 `docs/database/migration-plan.md` | Phase 2–10 各表 V2–V9 归属明确，破坏性变更列入 STOP 条件 | ✅ |

### 1-5 API 基础规范落地

- 状态: ✅ DONE（2026-09-06）｜ 依赖: 1-3 ｜ 验证方式: swagger-ui 访问 + health 接口规范核对
- Subtask DoD: API 契约与 ADR-005 完全一致，并有可查阅的约定文档

| # | Subtask | 验收标准 | 状态 |
|---|---|---|---|
| 1-5.1 | SpringDoc OpenAPI（swagger-ui）集成（dev 启用 / prod 默认关闭） | `/swagger-ui` 200；`/v3/api-docs` 输出 OpenAPI 3.1 | ✅ |
| 1-5.2 | `docs/api/api-conventions.md` | 方法语义/状态码/统一响应/分页排序过滤/错误映射/安全约定，与 §9、ADR-005 一致 | ✅ |

### 1-6 前端基础工程（Vue 3 + TypeScript + Vite）

- 状态: TODO ｜ 依赖: 1-1、1-3.7（health 接口联调）｜ 验证方式: `npm run dev` / `npm run lint` / `npm run build` / `npm run test` + 浏览器冒烟
- Subtask DoD: 可启动、可构建、lint 通过、健康页三态可用、无硬编码 API 地址

| # | Subtask | 验收标准 |
|---|---|---|
| 1-6.1 | Vite + Vue 3 + TS 脚手架 | dev/build 均成功 |
| 1-6.2 | 目录约定：api / components / views / stores / router / utils / types | 结构落地 |
| 1-6.3 | Vue Router + Pinia + Element Plus 集成 | 应用壳可渲染基础布局 |
| 1-6.4 | Axios 封装：baseURL=`/api/v1`、统一响应解包、错误拦截、traceId 透传 | 类型化、统一错误处理骨架（§17） |
| 1-6.5 | 环境变量 `.env.development` / `.env.production` + Vite dev proxy | 无硬编码 API 地址；规避 CORS |
| 1-6.6 | 健康状态首页：调用 health 接口，覆盖 Loading / Success / Error 三态 | 三态可视（§17 页面状态要求） |
| 1-6.7 | ESLint + Prettier + Vitest（1 个冒烟测试） | lint 与 test 通过 |

### 1-7 基础测试运行能力

- 状态: TODO ｜ 依赖: 1-3、1-6 ｜ 验证方式: 三层测试命令分别执行并记录结果
- Subtask DoD: 测试体系骨架符合 §19，且每层至少 1 个可运行用例

| # | Subtask | 验收标准 |
|---|---|---|
| 1-7.1 | 后端：context 加载测试 + health 接口 MockMvc 测试（断言统一响应结构） | `mvn test` 绿 |
| 1-7.2 | 前端：Vitest 冒烟测试 | `npm run test` 绿 |
| 1-7.3 | Python 骨架：pytest + conftest（BASE_URL 可配置）+ `tests/api/test_health.py` | 后端运行时 `pytest tests/api/test_health.py` 绿 |
| 1-7.4 | `docs/testing/how-to-run-tests.md` | 三层测试运行说明与实际一致 |

### 1-8 README 与开发环境说明

- 状态: TODO ｜ 依赖: 1-1 ~ 1-7 全部 ｜ 验证方式: 以文档为准从零走一遍启动流程
- Subtask DoD: 文档可复现，含 Windows 环境细节

| # | Subtask | 验收标准 |
|---|---|---|
| 1-8.1 | `README.md`：项目简介 / 架构图 / 技术栈 / 快速启动 / 文档索引 | 步骤与实际一致 |
| 1-8.2 | `docs/development/getting-started.md`：环境要求 / 分步启动 / 常见问题 | 环境要求与 3.0 检查结果一致 |

---

### 实施顺序与依赖关系

```text
1-1 目录结构与文档树
 └→ 1-2 基础设施（compose 或原生，视 P1-ENV-2）
     └→ 1-3 后端基础工程（视 P1-ENV-1）
         ├→ 1-4 数据库设计初稿 ─┐
         ├→ 1-5 API 规范落地 ───┤（1-4 与 1-5 可并行）
         └→ 1-6 前端基础工程 ←──（仅依赖 1-3.7 健康接口，可与 1-4/1-5 并行）
             └→ 1-7 基础测试能力（依赖 1-3 + 1-6）
                 └→ 1-8 README 与文档（最后收口）
```

---

## 4. Phase 2–19 里程碑概览

| Phase | 名称 | 核心产出 |
|---|---|---|
| 2 | Authentication & User | 登录 / 登出 / JWT / 用户管理 API |
| 3 | RBAC | 用户-角色-权限模型与后端强制鉴权 |
| 4 | Organization | 组织管理 |
| 5 | Project Management | 项目管理 |
| 6 | Issue Management | Issue 全生命周期 |
| 7 | Workflow | Issue 状态机与状态转换约束 |
| 8 | Comment & Attachment | 评论与附件（MinIO） |
| 9 | Notification | 通知 |
| 10 | Audit & Dashboard | 审计日志与数据统计（ECharts 在此引入） |
| 11 | Frontend Completion | 前端完整可用 |
| 12 | API Automation | Pytest API 自动化测试体系 |
| 13 | UI Automation | Playwright UI 自动化 |
| 14 | Performance Testing | JMeter 性能测试与数据报告 |
| 15 | Security Testing | 安全测试 |
| 16 | Docker & Deployment | 完整 Docker 部署方案（含 Nginx） |
| 17 | CI/CD | GitHub Actions 流水线 |
| 18 | Final QA | 全面回归验证 |
| 19 | Final Delivery | 最终交付 |

> 每个 Phase 开始前，必须在对应节建立该阶段的详细任务清单（目标 / 输入 / 任务 / 输出 / 验收标准）。
