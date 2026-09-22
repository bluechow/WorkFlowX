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

## 3. Phase 1 — Project Foundation（已完成 ✅，待用户验收）

- **目标**：搭建可运行的前后端基础工程与本地基础设施，形成"一键启动、可验证"的开发环境底座
- **输入**：Phase 0 总控体系；Master Prompt §3/§4/§8/§9/§11/§19/§24/§27；ADR-001/002/005/007
- **明确排除**（ADR-001，禁止引入）：Nacos、Sentinel、Seata、Kafka/RocketMQ、Kubernetes 及任何微服务组件；Nginx 反向代理延后至 Phase 16；ECharts 延后至 Phase 10（Product First，禁止装而不用）
- **Phase 级 DoD 实测结论（2026-09-06）：8/8 全部满足**，逐项证据见各任务验收记录与最终验收报告

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

- 状态: ✅ DONE（2026-09-06）｜ 依赖: 1-3、1-6 ｜ 验证方式: 三层测试命令分别执行并记录结果
- Subtask DoD: 测试体系骨架符合 §19，且每层至少 1 个可运行用例

| # | Subtask | 验收标准 | 状态 |
|---|---|---|---|
| 1-7.1 | 后端：HealthControllerTest（@WebMvcTest，断言统一结构 + traceId 复用/回写）+ ResultTest | `mvn test` 4/4 绿 | ✅ |
| 1-7.2 | 前端：Vitest 冒烟测试（HealthView 成功/错误两态，jsdom + Element Plus） | `npm run test` 2/2 绿 | ✅ |
| 1-7.3 | Python 骨架：pytest + conftest（WORKFLOWX_BASE_URL 可配置）+ tests/api/test_health.py | 后端运行时 `pytest` 2/2 绿（实测 0.30s） | ✅ |
| 1-7.4 | `docs/testing/how-to-run-tests.md` | 三层测试运行说明与实际一致 | ✅ |

### 1-8 README 与开发环境说明

- 状态: ✅ DONE（2026-09-06）｜ 依赖: 1-1 ~ 1-7 全部 ｜ 验证方式: 以文档为准从零走一遍启动流程
- Subtask DoD: 文档可复现，含 Windows 环境细节

| # | Subtask | 验收标准 | 状态 |
|---|---|---|---|
| 1-8.1 | `README.md`：项目简介 / 架构图 / 技术栈 / 快速启动 / 文档索引 | 步骤与实际执行过程一致（本 Phase 全程实测过） | ✅ |
| 1-8.2 | `docs/development/getting-started.md`：环境要求 / 分步启动 / 常见问题 | 环境要求与 3.0 检查结果一致，含 WSL/镜像加速等本机实测细节 | ✅ |

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

## 4. Phase 2 — Authentication & User（进行中：2.0 规划完成，编码未启动）

- **目标**：建立完整的「用户 + 认证」基础能力：Spring Security + JWT + Redis 登录状态 + 用户 CRUD + 前端登录闭环 + 三层测试
- **输入**：Phase 1 全部产物；Master Prompt §6（认证）/§7（授权）/§9（API）/§29（禁止事项）；ADR-003/005/007
- **认证方案基线**（2.0 已定，详见验收报告/规划记录）：Spring Security 6 + jjwt 0.12 + Redis 白名单单会话（key `auth:session:{userId}` → jti，TTL=JWT 有效期）；无 Refresh Token（理由：单会话白名单已覆盖登出/过期/重复登录/踢人，避免不必要的复杂度）；BCrypt(10)；登录失败 Redis 计数（15 分钟窗口 5 次 → 锁 15 分钟，429）；种子账号仅注入 dev Flyway location（prod 无默认凭据）
- **Phase 级 DoD**：
  1. 登录→me→用户 CRUD→登出→401 全链路实测通过（curl + pytest）
  2. 无 Token/非法 Token/过期 Token → 401 统一 JSON；越权 → 403；失败 5 次 → 429
  3. 密码 BCrypt 存储；JWT_SECRET 环境变量注入（prod 强制）；日志无密码
  4. 禁用用户立即无法登录且已有会话被踢
  5. 前端登录/登出/路由守卫/Token 注入可用
  6. 三层测试全绿且覆盖正/反/边界/权限用例
  7. 文档同步（api-conventions 认证章节、架构安全文档、getting-started 测试账号）
  8. 不删除/不破坏 Phase 1 任何能力（Phase 1 测试仍全绿）

### Phase 2 任务清单（P2-01 ~ P2-25）

| # | 任务 | 核心内容（文件/DB/API） | 验证方式 | 依赖 |
|---|---|---|---|---|
| P2-01 | 用户领域模型与数据库（V2 种子） | User 实体+VO/DTO 映射既有 V1 表（不改表结构）；V2 dev 种子（roles ADMIN/MEMBER + admin/user1 账号，放 db/seed/dev，prod 不执行）；更新 data-dictionary/migration-plan | 启动迁移成功；SELECT 种子数据 | - |
| P2-02 | 用户 Mapper 与分页 | UserMapper（MP BaseMapper）+ MybatisPlusInterceptor 分页插件 | mvn 编译 + 分页单测 | P2-01 |
| P2-03 | 用户 Service | UserService（create/update/get/page/updateStatus）+ Bean Validation DTO + 唯一性校验（409） | 单测 + 后续接口联调 | P2-02 |
| P2-04 | BCrypt 密码体系 | PasswordEncoder bean（strength 10）+ 集成到创建/登录 | 单测 encode/matches | P2-03 |
| P2-05 | Spring Security 基础配置 | SecurityFilterChain（无状态/CSRF off/路径规则）+ EntryPoint/DeniedHandler 输出统一 JSON | 未认证 401 JSON 实测 | P2-04 |
| P2-06 | JWT | JwtProperties（secret/expire 环境变量）+ JwtTokenService（生成/解析/校验，jjwt 0.12） | 单测：生成/过期/篡改 | P2-05 |
| P2-07 | Redis 登录状态 | SessionService：auth:session:{userId}→jti，TTL=2h；登录覆盖/登出删除/校验比对 | 单测 + redis-cli 实测 | P2-06 |
| P2-08 | 登录接口 | POST /api/v1/auth/login；状态检查+失败计数+BCrypt+签发+写会话+更新 last_login_at | curl/pytest 正反用例 | P2-07 |
| P2-09 | 登出接口 | POST /api/v1/auth/logout（认证后删除会话） | 登出后旧 token 401 | P2-08 |
| P2-10 | /me | GET /api/v1/auth/me 返回当前用户 VO | pytest | P2-08 |
| P2-11 | 用户 CRUD | GET/POST /users、GET/PUT /users/{id}（ADMIN 角色，分页/过滤/唯一性 409） | pytest 正反用例 | P2-08 |
| P2-12 | 启用/禁用 | PATCH /users/{id}/status；禁用同时踢会话；不能操作自己 | pytest + 踢线实测 | P2-11 |
| P2-13 | 登录失败限制 | Redis 计数 auth:fail:{username}（15min/5 次→锁 15min，429；成功清零） | pytest 计数/锁定/恢复用例 | P2-08 |
| P2-14 | Swagger Security | OpenAPI bearerAuth scheme + 接口 security 注解 | swagger-ui 显示 Authorize | P2-06 |
| P2-15 | 后端测试 | AuthService/Controller 测试（@SpringBootTest+MockMvc，连 compose 真实服务，自建数据自清理） | mvn test 全绿 | P2-13 |
| P2-16 | 前端登录页 | LoginView + 应用壳改造（顶部用户名/退出） | 手动 + 组件测试 | P2-08(API) |
| P2-17 | 路由鉴权 | router beforeEach + 401 跳转 | 组件/guard 测试 | P2-19 |
| P2-18 | Axios Token 注入 | 请求拦截 Authorization；401 响应拦截清会话跳登录 | 拦截器测试 | P2-19 |
| P2-19 | Pinia 登录状态 | stores/auth.ts（token/user 持久化 localStorage，login/logout/fetchMe） | store 测试 | P2-16 |
| P2-20 | Python API 自动化 | tests/api/test_auth.py + test_users.py + 登录/用户工厂 fixture | pytest 全绿 | P2-12 |
| P2-21 | 前端测试 | LoginView 组件 + auth store + 路由守卫测试 | npm run test 全绿 | P2-19 |
| P2-22 | 测试数据与账号 | dev 种子即测试账号；pytest 用户工厂（随机用户名，用后清理）；账号清单入文档 | 文档与 fixture 一致 | P2-01 |
| P2-23 | 完整认证链路测试 | E2E：登录→me→CRUD→禁用踢线→登出→401；失败锁定 429 | pytest E2E + 手动清单 | P2-20/21 |
| P2-24 | 文档 | api-conventions 认证章节 + docs/architecture/security.md + getting-started 账号表 + README | 文档与实现一致 | P2-23 |
| P2-25 | Phase 2 最终验收 | DoD 逐项核验 + 验收报告 + AI_CONTEXT/AI_TASKS 收口 | 全部证据可追溯 | P2-24 |

> 任务结构与用户 P2-01~P2-25 建议一致；两处说明：① P2-01 明确为「映射既有 V1 表 + dev 种子迁移」，Phase 1 已定稿的表结构不做变更；② P2-04/02/03 按依赖顺序串行实施，BCrypt 先于 Security 配置落地。
>
> **进度：P2-01 ✅ DONE（2026-09-06）**——User 实体/枚举/VO + dev 种子迁移（db/seed/dev location 隔离）+ 16 个测试；mvn test 20/20（含 P1 回归 4）；pytest/npm 回归全绿；MySQL 实查 users=2/roles=2/user_roles=2；BCrypt 经 Python + Spring BCryptPasswordEncoder 6.5.2 双重验证。决策 D1–D5 已按用户确认执行。
>
> **进度：P2-02 ✅ DONE（2026-09-06）**——UserMapper（BaseMapper，无冗余方法）+ MybatisPlusConfig 分页插件（PaginationInnerInterceptor/MySQL；MP 3.5.9+ 需补 mybatis-plus-jsqlparser 依赖）；UserMapperTest 11 用例真实 MySQL 验证（分页/模糊/状态/组合/越界/稳定排序/边界），SQL 日志实证 ORDER BY created_at DESC,id DESC + LIMIT 单次生成 + count 正确；测试数据 p2_mapper_test_ 前缀用后清理（users 回到 2 行）；mvn test 31/31，pytest/npm 回归全绿。
>
> **进度：P2-03 + P2-04 ✅ DONE（2026-09-06，批次提交）**——UserService（getById/page/create/update/updateStatus）+ PasswordService（BCrypt 10，全局唯一 PasswordEncoder Bean）；DTO Bean Validation（username 3-32 字符集/email≤100 含 local≤64 约束/password≥8 位字母+数字/分页参数上限 100）；唯一性预检+DB 约束双防线，DuplicateKeyException 精准转 409；新增测试 30 个（密码 6/DTO 校验 6/集成 18），mvn test 61/61；发现的坑：Hibernate @Email 隐含 local part≤64（RFC 5321），边界用例据此修正；pytest/npm 回归全绿，DB 零污染。
>
> **进度：P2-05 + P2-06 ✅ DONE（2026-09-06，批次提交）**——Spring Security 6（STATELESS/CSRF off/无 formLogin·httpBasic/公开端点白名单/其余全部 authenticated，401/403 统一 JSON）+ JWT 基础设施（jjwt 0.12.6，HS256 服务端固定，claims=sub/username/roles/jti/iat/exp，secret 环境变量化，prod 强制提供；JwtAuthenticationFilter 建立 Authentication，无 DB/Redis 依赖）；ADR-008 记录技术基线；新增测试 21 个（JWT 12 + Security 9），HealthControllerTest 升级为真实安全链路验证；mvn test 82/82，pytest/npm 回归全绿。**未实现**：Redis 会话/登录/登出/me/失败限制（属 P2-07+）。
>
> **进度：P2-07 + P2-08 ✅ DONE（2026-09-06，批次提交）**——AuthSessionService（auth:session:{userId}→jti，TTL 与 JWT 同源 2h，原子 SET+TTL，单会话后登录覆盖先登录，deleteSession 供 P2-09 复用）+ JwtAuthenticationFilter 会话校验（fail-closed：Redis 故障拒绝不放行）+ POST /api/v1/auth/login（白名单、Bean Validation、密码先验证后查状态防枚举、DISABLED/LOCKED 403、签发→写会话→last_login_at、LoginResponse 无密码字段）；JwtService 扩展 issueToken 返回 jti；新增测试 14 个（登录集成，真实 MySQL+Redis），mvn test 96/96；pytest 的未知路径用例按新安全行为修正（未认证 401 先于 404）；真实 curl E2E：登录→访问→二次登录覆盖→旧 token 401→统一错误全部实证；Redis TTL/jti/last_login_at 实查通过。**未实现**：logout(P2-09)/me(P2-10)/失败限制(P2-13)/前端登录(P2-16+)。
>
> **进度：P2-09 + P2-10 ✅ DONE（2026-09-06，批次提交）**——POST /api/v1/auth/logout（需认证；userId 取自 SecurityContext 不接受客户端传入；deleteSession 幂等；登出后原 token 因会话缺失立即 401）+ GET /api/v1/auth/me（userId 取自 SecurityContext；**数据来自数据库** UserService.getById，邮箱/昵称修改后立即生效；用户被删除 → 404 非 200+null；UserVO 复用无敏感字段）；新增集成测试 10 个（真实 MySQL+Redis），mvn test 106/106；真实 curl E2E：me 返回最新用户→logout 200→会话删除→旧 token /me 401→重复 logout 401→无 token logout 401 全部实证。**未实现**：失败限制(P2-13)/前端认证页面(P2-16+)。
>
> **进度：P2-11 + P2-12 ✅ DONE（2026-09-06，批次提交）**——UserController 5 端点（GET /users 分页 / GET /users/{id} / POST 201 / PUT /users/{id} / PATCH /users/{id}/status），`@PreAuthorize("hasRole('ADMIN')")` + `@EnableMethodSecurity` 后端强制 ADMIN；禁用即踢线（DISABLED 同事务删 Redis 会话，旧 JWT 立即 401；ACTIVE 恢复不自动建会话）；自操作守卫（operatorId==target → 400）；修复真实 bug：AuthorizationDeniedException 被全局兜底吞成 500 → 重抛交回 Security 403；补 HttpMessageNotReadable → 422。业务规则用户已确认：禁止自禁用/允许互禁/最后管理员保护暂不实现（ADR-009）。新增测试 22 个，mvn test 128/128；真实 curl E2E：admin 管理 API→201 创建→MEMBER 403→禁用踢线→旧 token 401→启用→重新登录→新 token 200→自禁用 400 全部实证。**未实现**：失败限制(P2-13)/前端页面(P2-16+)。
>
> **进度：P2-13 + P2-14 ✅ DONE（2026-09-06，批次提交）**——LoginAttemptService（auth:fail:{username}，INCR 原子计数+EXPIRE 窗口，5 次/15 分钟，第 5 次 TTL 重置锁定，成功登录 DEL；**统一计数语义**含不存在 username 防枚举，ADR-010）+ AuthService 集成（锁定检查最前：不查库/不验密码/不签发/不建会话；Redis 故障 fail-closed）；OpenAPI Bearer SecurityScheme（http/bearer/JWT + 根级 SecurityRequirement + health 空覆盖）；新增测试 13 个（锁定 7 + fail-closed 1 + OpenAPI 5），mvn test 140/140；真实 curl E2E：4×401→第5次 429→锁定期正确密码 429→Redis 计数5/TTL 899→窗口结束恢复 200；Swagger/ui 与 api-docs scheme 实证。**未实现**：P2-15 后端测试扩展/P2-16 前端页面。
>
> **进度：P2-14 修复 ✅ DONE（2026-09-06，fix(api) 提交）**——用户验收指出 login 在 OpenAPI 中错误继承根级 Bearer。修复：AuthController.login 空 @SecurityRequirements（公开标记）；logout/me 逐方法与 UserController 类级显式 @SecurityRequirement(bearerAuth)；文档声明与运行时鉴权一一对应。mvn test 140/140（OpenAPI 断言更新为逐端点），pytest/npm 回归全绿，运行时 JSON 实测（login=[]、logout/me/users=[{bearerAuth}]、health=[]、根级保留）。**未实现**：P2-15 后端测试扩展/P2-16 前端页面。
>
> **进度：P2-15 + P2-20 ✅ DONE（2026-09-06，批次提交）**——P2-15 补 4 个真实缺口测试（分页参数校验 422、路径变量类型错误 400、username 大小写唯一性语义、弱密钥 fail-fast）+ 2 个异常处理器（BindException→422、MethodArgumentTypeMismatch→400），mvn test 144/144；P2-20 建立 Python API 自动化体系（tests/api/：conftest fixtures[api_client/admin_token/member_token/用户工厂+清理] + test_auth_api 6 + test_user_api 13 + test_lockout_api 3 = 28 用例真实 HTTP 黑盒），依赖新增 pymysql+redis 仅用于清理 fixture；发现并修正测试设计问题：admin 多次登录被单会话语义顶掉（fixture 改为单次登录+工厂用户验证登录载荷）、unique_suffix 改函数级；pytest 28/28，清理零残留（users=2、api_test_/fail 键 0）。**未实现**：P2-21 前端测试/P2-22 文档化。
>
> **进度：P2-16 ~ P2-19 ✅ DONE（2026-09-06，批次提交）**——登录页（真实 API/手动校验/loading/防重复提交/统一错误展示）+ Dashboard（/me 用户资料+退出按钮）+ 路由守卫（公开白名单/无 token 重定向/有 token 强制 fetchMe 校验）+ Axios（Bearer 自动注入/401 清理回登录页防循环/403 不误登出/2xx 解包容 201）+ Pinia auth store（token 持久化 localStorage `workflowx_access_token`，currentUser 唯一来源 /me，ADR-011）；**修复真实 P0**：el-form validate() 在真实 Chrome 永久 pending 导致登录不可用 → 改同步手动校验。前端测试 9 个（auth store 4/LoginView 3/HealthView 2）；真实浏览器 E2E 14 步全部实证（空提交校验/错误密码 401/admin 登录 dashboard/刷新恢复/会话覆盖自动登出/伪造 token 自动回登录/member 闭环/403 不误登出）。**未实现**：P2-21 完整前端测试/P2-22 文档化/业务页面。
>
> **进度：P2-21 + P2-22 ✅ DONE（2026-09-06，批次提交）**——前端测试体系分层建成（Unit: token util；Component: LoginView 9/Dashboard 4；Integration: 真实 axios 实例+受控 adapter 验证拦截器链 8、真实 router 导航验证守卫 8、auth store 8），新增 36 个测试至 **45/45 全绿**（lint/build 同步全绿）；P2-22 建立 docs/testing/test-data.md（seed 账号/Python api_test_ 前缀与清理/Java p2_*_test_ 前缀与 @AfterEach 清理/前端 mock 数据/Redis 键与 TTL/锁定影响/人工清理命令/生产安全边界）。回归：mvn 144/144、pytest 28/28 全绿。
>
> **进度：P2-23 ✅ DONE（2026-09-06）**——Phase 2 全链路验证：E2E 脚本 42 项检查 **2 轮全过**（登录/统一 401 防枚举/5 次锁定 429/锁定期正确密码拒绝/锁定解除恢复/覆盖踢线/会话删除失效/用户管理 201·200·409·422·404/自禁用 400/权限矩阵/400~500 错误码+traceId/无敏感泄漏/Redis 停机 500 fail-closed 实测）+ 三线回归（mvn 144/pytest 28/Vitest 45）；环境问题修复（WSL 转发失效→Hyper-V 防火墙放行+portproxy）记录于 phase2-validation.md。**报告: docs/testing/phase2-validation.md**。
>
> **进度：P2-24 ✅ DONE（2026-09-06）**——文档收口：README（Phase 2 实际状态）/getting-started（登录 API 可用+测试账号+curl 示例）/api-conventions（新增认证 API 端点与规则节）；新增 docs/architecture/security.md（认证链路全景图+会话模型+失败限制+边界风险）。
>
> **进度：P2-25 ✅ DONE（2026-09-06）——Phase 2 Release Gate: PASS**——交付状态（clean/13 语义化提交）、产品完整性（后端 11+前端 7 关键组件+迁移配置全在位+运行时冒烟 5 链 200）、安全终检 8 项全过、文档一致性 5 项全过、三线回归（mvn 144/Vitest 45+lint+build/pytest 稳定 3 轮 28/28）；首轮 pytest 21 errors 定位为 WSL 容器冷启动竞态（环境瞬时，非产品缺陷），复验通过；**未发现产品缺陷，未修改任何生产代码**。Gate 报告: docs/testing/phase2-validation.md §Release Gate。**Phase 2 全部 25 任务完结，下一阶段 Phase 3 — RBAC 等用户指令。**

---

## 5. Phase 3 — RBAC（进行中：P3-01 + P3-02 ✅ DONE，等用户指令继续）

- **目标**: 用户-角色-权限模型落地与后端强制鉴权演进（Master Prompt §7、ADR-003/012）
- **输入**: Phase 2 认证链路（JWT roles claim → ROLE_ authorities → @PreAuthorize）；V1 五表；ADR-012
- **设计基线**: docs/architecture/rbac.md（权限编码 {resource}:{action}、系统角色/权限清单、衔接演进路径）

| # | 任务 | 状态 | 说明 |
|---|---|---|---|
| P3-01 | RBAC 数据模型与领域设计 | ✅ DONE | 五表零结构变更；rbac.md 设计文档；ADR-012；V3 系统权限种子迁移（14 项 + ADMIN 全量绑定，公共 migration） |
| P3-02 | RBAC 后端基础能力 | ✅ DONE | rbac 模块：Role/Permission 实体 + 4 Mapper（关联表注解 SQL）+ DTO/VO + Role/Permission/UserRole 三 Service（事务/唯一 409/404/系统保护 400）；新增测试 28 个 |
| P3-03 | RBAC REST API + 权限注解接线 | ✅ DONE | 13 端点（角色 CRUD/角色权限 replace/权限只读/用户角色 get·assign·revoke·replace）；Filter 权限 authorities 实时接线；UserController 迁移 hasAuthority；AuthRoleQueryMapper 统一并删除；RbacControllerIntegrationTest 16 用例；mvn 188/188 |
| P3-04 | RBAC 前端产品能力 | ✅ DONE | 系统管理三页（用户角色/角色管理/权限管理）+ ADMIN 菜单 + api/rbac.ts + RoleVO/PermissionVO 类型；Vitest 新增 11 用例（mock 仅 HTTP boundary）56/56；运行时冒烟权限矩阵+实时授予回收实证 |
| P3-05+ | 前端权限细化/测试完善/验收 | ⬜ TODO | 进入时细化 |

> **进度：P3-01 ✅ DONE（2026-09-06）**——执行前检查确认五表真实结构（permissions UK + ENUM(MENU/API/BUTTON)，关联表 FK CASCADE）与 Security 行接点；产出 docs/architecture/rbac.md（编码规范/系统角色权限/衔接演进）、ADR-012（JWT 仅带 roles、权限服务端实时查询、零结构变更、Phase 2 机制不动）、V3__rbac_permissions.sql（14 项系统权限 + ADMIN 全量绑定，公共迁移幂等种子）；migration-plan V3 归属更新（组织表顺延 V5）。
>
> **进度：P3-02 ✅ DONE（2026-09-06）**——rbac 模块落地：Role/Permission 实体 + RoleMapper/PermissionMapper（BaseMapper）+ UserRoleMapper/RolePermissionMapper（复合键关联表注解 SQL，P2-08 先例）+ CreateRole/UpdateRole/CreatePermission/AssignRolePermissions DTO（编码正则守护）+ RoleVO/PermissionVO（system 标记）+ RbacConstants（系统角色/权限代码守护）+ RoleService（CRUD/replace 语义权限绑定/系统角色 400 保护/未知权限 404）+ PermissionService（CRUD/系统权限 400/三表 JOIN 用户权限实时解析）+ UserRoleService（幂等分配/回收/查询）；新增集成与校验测试 28 个；**回归: mvn 172/172（Phase 2 144 全绿无回归）+ pytest 28/28（新 jar）+ Vitest 45/45 + lint/build + 运行时冒烟（登录→me→users→logout 200）**；V3 迁移实测 success=1（permissions=14、ADMIN 绑定=14）。
>
> **进度：P3-03 + P3-04 ✅ DONE（2026-09-06）**——P3-03：RbacController 13 端点（hasAuthority 全覆盖）+ JwtAuthenticationFilter 权限 authorities 实时接线（user→role→permission，收权即时生效）+ UserController hasRole→hasAuthority 迁移 + AuthRoleQueryMapper 统一删除（SQL 等价，AuthLogin 14 用例回归验证）+ AssignUserRolesRequest（元素级校验）+ replaceUserRoles；RbacControllerIntegrationTest 16 用例（权限矩阵/系统保护/实时授予回收），mvn 188/188。P3-04：系统管理三页（RolesView 创建/编辑/删除保护/权限分配对话框、PermissionsView 只读列表、UserRolesView 分页搜索+角色 replace 对话框）+ App 头部 ADMIN 菜单（纯 UX）+ api/rbac.ts + 类型；Vitest 新增 11 用例 56/56（mock 仅 HTTP boundary）+ lint/build 全绿；运行时冒烟：admin 200/member 403/未认证 401 + 同一 token 授予 role:list 403→200、回收 200→403 实时生效。pytest 28/28 回归。**发现修复**：测试数据小写前缀违反自身角色编码规范（P3-02 遗留，Service 层无 @Valid 故当时未拦截）→ 统一大写；WSL portproxy 指向过期 IP → 更新脚本化。

> **进度：P4-01 ✅ DONE（2026-09-15）**——V4 迁移（organizations/departments/organization_members，users 零改动）；org 模块（实体×3/Mapper×3/DTO×4/VO×3/服务×2）+ ForbiddenException；ADR-013 + organization.md；集成测试 14 个；mvn 202/202。
>
> **进度：P4-02 ✅ DONE（2026-09-15）**——V5 权限种子（org×6 + department×5，ADMIN 全量绑定，系统保护清单扩至 25）；OrgController 13 端点（hasAuthority 全覆盖 + org:delete 数据级 OWNER 校验叠加）；OrgControllerIntegrationTest 12 用例（权限矩阵/OWNER 规则/防环/409/422/404）；mvn 214/214；运行时冒烟 201/403/400/200 实证。
>
> **进度：P4-03 ✅ DONE（2026-09-15）**——组织管理前端：OrganizationsView（分页/搜索/CRUD/删除确认）+ OrganizationDetailView（部门树 el-tree/成员管理/权限码按钮）+ 菜单权限化 + api/org.ts；Vitest 新增 12 用例 70/70。
>
> **进度：Phase 3 + Phase 4 Release Gate ✅ PASS（2026-09-15，67dfe6f）**——三线 214/43/70 全绿 + 真实 HTTP 冒烟 + MySQL/Redis 残留清零 + Swagger 同步；Gate 记录 docs/testing/phase34-gate.md。
>
> **进度：P5-01 ✅ DONE（2026-09-15）**——V6 projects 迁移（key UK/org_id CASCADE/status 归档/owner_id）；ADR-014（数据级归属：写操作须为组织成员）+ project.md；project 模块（实体/枚举/Mapper/DTO×3/VO/Service）；集成测试 11 个；mvn 225/225。
>
> **进度：P5-02 ✅ DONE（2026-09-15）**——V7 权限种子（project×5，系统权限达 30）；ProjectController 5 端点（hasAuthority）；数据级归属测试（authority 有+非成员 403→加入成员 200）；ProjectControllerIntegrationTest 10 用例；mvn 235/235；运行时冒烟 201/409/归档恢复/级联全过；pytest 43 两轮 + Vitest 70 + lint/build 全绿。
>
> **进度：P5-03 ✅ DONE（2026-09-15）**——项目管理前端：ProjectsView（列表/搜索/status+组织筛选/创建/编辑/归档恢复确认/权限 UX）+ 菜单 + api/project.ts + 类型；Vitest 7 用例（含组织下拉真实交互），77/77。
>
> **进度：P5-04 ✅ DONE（2026-09-15）**——V8 project_members（复合PK/OWNER,MANAGER,MEMBER/FK CASCADE）+ project:assign_member 种子（系统权限 31）；ADR-015（组织成员前置/角色最小集）；ProjectMemberService（前置 400/重复 409/OWNER 保护 400/操作者数据级 403）+ ProjectServiceImpl.create 同事务写 OWNER 行；ProjectMemberController 3 端点；集成测试 9 个；mvn 244/244。
>
> **进度：P5-05 ✅ DONE（2026-09-15）**——tests/api/test_project_api.py 10 用例（CRUD 生命周期/keyword+status+orgId 过滤/dup 409/invalid 422/缺 org 404/不可变字段/归档恢复/成员生命周期/数据级归属 403→入组 201→移出 403）；pytest 48/48 两轮。
>
> **进度：Phase 5 Release Gate ✅ PASS（2026-09-15）**——三线 244/48/77 全绿 + 12 步真实 HTTP 冒烟 + MySQL/Redis 残留清零 + Swagger 同步；Gate 记录 docs/testing/phase5-gate.md。**Phase 5 全部完结，下一阶段 Phase 6 — Issue Management 等用户指令。**

## 5.5 Phase 6 — Issue Management（✅ 全部完结并通过 Release Gate，2026-09-15）

- **目标**：建立完整 Issue 核心领域（创建/查询/更新/编号/分派/状态基础），为 Phase 7 Workflow / Phase 8 Comment / Phase 9 Notification 留稳定接口
- **P6-01 ✅**：V9（projects.issue_seq + issues 表，data-dictionary §10 落地，索引补齐）；ADR-016（行锁序号/业务编号拼装/Phase 6 状态边界/数据级=项目成员/severity 规则/归档项目待决策）
- **P6-02 ✅**：IssueService——create（reporter=操作者/issue_no 事务内行锁分配/assignee 项目成员约束/severity 仅 BUG）、getById（防跨项目 404）、page（keyword 双字段+issueNo+四枚举+reporter+assignee+组合+分页≤100+稳定排序）、update（不可变字段/assignee 重校验）、updateStatus（仅枚举，流转属 Phase 7）
- **P6-03 ✅**：V10 权限种子（issue×5，系统权限 36）+ IssueController 6 端点（hasAuthority）+ 权限矩阵测试（401/403/200/201）
- **P6-04 ✅**：查询全维测试（keyword 双字段/issueNo 精确/枚举/人员/组合/分页/稳定序/项目隔离）
- **P6-05 ✅**：状态边界——本阶段仅枚举合法值（流转矩阵属 Phase 7，ADR-016.3）
- **P6-06 ✅**：Java 集成测试 19 个（领域 12 + Controller 7），含 reporter 不可伪造/issue_no 不可变/跨项目 404
- **P6-07 ✅**：Python test_issue_api.py 10 用例真实 HTTP（生命周期/五维过滤/issue_no 不可变/数据级归属）
- **P6-08 ✅**：前端 IssuesView（列表/筛选/创建/编辑/状态下拉/分派下拉/权限 UX）+ api/issue.ts + 路由 + 项目行入口；Vitest 84/84
- **P6-09 ✅**：E2E 12 步真实 HTTP + 三线全量回归（mvn 263 / pytest 48 / Vitest 84）
- **P6-10 ✅ Gate PASS**：docs/testing/phase6-gate.md；**修复真实 Bug 1 个**（取消分派 assigneeId=null 被 MP updateById 忽略，f48c5de）；Phase 6 正式关闭

## 5.7 Phase 7 — Workflow（✅ 全部完结并通过 Release Gate，2026-09-21）

- **目标**：建立正式 Issue 状态机（矩阵/权限/并发保护），流转能力受控可验证，为 Phase 8 Comment 留稳定接口
- **P7-01 ✅**：ADR-017 正式状态转换矩阵（6 条合法流转/CLOSED 唯一终态/REOPENED 回路/禁跳转/禁自环/非法 409），取代 ADR-016.3
- **P7-02 ✅**：WorkflowService 领域服务（VALID_TRANSITIONS 矩阵收敛 Service 层，Controller 零状态机逻辑；非法流转 message 携带允许目标）
- **P7-03 ✅**：V11 issue:transition 种子 + ADMIN 绑定（系统权限 36→37）；数据级仍要求项目成员（双层校验）
- **P7-04 ✅**：PATCH status 迁移至 issue:transition + WorkflowService；TransitionIssueStatusRequest（fromStatus/toStatus 均必填，防并发伪造）
- **P7-05 ✅**：条件 UPDATE 乐观并发（`WHERE status=fromStatus`，affected=0→409；无 version 字段、无 MAX+1 类方案）；WorkflowConcurrencyTest 8 线程 1×成功+7×409
- **P7-06 ✅**：Java 集成测试（矩阵主链/REOPENED 回路/终态/非法跳转/自环 409/数据级 403/跨项目 404/8 线程并发）；mvn 264/264
- **P7-07 ✅**：tests/api/test_workflow_api.py 12 用例真实 HTTP（主链/回路/终态/自环/非法/422/401/403/404/8 线程并发/完整回路）
- **P7-08 ✅**：前端 allowedTargets 合法目标下拉（VALID_TRANSITIONS 镜像常量）+ issue:transition 权限 gating + 防重复提交；Vitest IssuesWorkflow 5 用例
- **P7-09 ✅**：E2E 严格断言 22 项 ALL PASS（tests/e2e/phase7_workflow_e2e.sh 归档：幂等预清理/逐步硬断言/角色回收）
- **P7-10 ✅ Gate PASS**：docs/testing/phase7-gate.md；关键测试连续两轮（mvn 264×2 / pytest 60×2 / Vitest 89×2）；Phase 7 正式关闭

## 5.8 Phase 8 — Comment & Attachment（✅ 全部完结并通过 Release Gate，2026-09-22）

- **目标**：建立 Issue 协作层（评论 + MinIO 附件），权限/数据级/ownership 三层校验，文件安全闭环，为 Phase 9 Notification 留稳定接口
- **P8-01 ✅**：影响分析（MinIO 基础设施复用 compose 现有 bucket；数据字典 §11/§12 命名为准；Issue 校验链模式沿用）
- **P8-02 ✅**：V12 issue_comments + attachments（FK CASCADE/UNIQUE object_key）+ 实体/Mapper
- **P8-03 ✅**：CommentService（author 服务端绑定/content 10000 上限/无软删除/跨资源 404）
- **P8-04 ✅**：CommentController 5 端点（嵌套资源边界 Swagger 同步）
- **P8-05 ✅**：comment×5 权限种子 + ownership 第三层（仅作者本人，ADMIN 不豁免）
- **P8-06 ✅**：Attachment 元数据模型（零 BLOB 入库）
- **P8-07 ✅**：StorageService/MinioConfig（fail-fast bucket 校验；common.storage 不感知业务）
- **P8-08 ✅**：文件安全策略（白名单 12 类不信任 Content-Type/10MB 可配 413/文件名清洗/objectKey 服务端生成）——ADR-018
- **P8-09 ✅**：multipart 上传 + MinIO→DB 一致性补偿（insert 失败删对象，补偿失败记 ERROR）
- **P8-10 ✅**：后端鉴权下载（同步 byte[]，修复 StreamingResponseBody 连接污染真实缺陷；无 presigned/公开 bucket）
- **P8-11 ✅**：删除顺序 MinIO→DB（对象失败元数据保留）+ MaxUploadSizeExceededException→413
- **P8-12 ✅**：Java 集成测试 23 个（真实 MySQL+MinIO：一致性双向断言/白名单/穿越/ownership）；mvn 287/287
- **P8-13 ✅**：Python 黑盒 21 个（真实 HTTP；MinIO 终态=0 断言）；pytest 81/81
- **P8-14 ✅**：IssueCommentsPanel（列表/创建/编辑/删除/loading/empty/error/权限 UX/重拉）+ Vitest 8
- **P8-15 ✅**：IssueAttachmentsPanel（上传进度/前端预检/鉴权下载/删除）+ IssuesView 抽屉 + Vitest 8；105/105
- **P8-16 ✅**：真实文件 E2E 20 断言 ALL PASS ×2（MinIO mc/DB mysql 双侧一致性核验+权限路径+终态=0）
- **P8-17 ✅ Gate PASS**：docs/testing/phase8-gate.md；ADR-018；关键测试连续两轮（mvn 287×2/pytest 81×2/Vitest 105×2）；**修复真实缺陷 1 个**（StreamingResponseBody keep-alive 污染）；Phase 8 正式关闭

## 5.9 Phase 9 — Notification（✅ 全部完结并通过 Release Gate，2026-09-22）

- **目标**：真实业务通知能力（3 类型触发/self 资源隔离/已读管理/前端通知中心），不引入 MQ/WebSocket，为 Phase 10 Audit 留 related 关联模式
- **P9-01 ✅**：影响分析（触发点收敛 4 方法/权限决策 self 资源/§14 transitions 表遗留缺口记录）
- **P9-02 ✅**：V13 notifications（字典 §13 + read_at/related/created_at 扩展，recipient 无 FK 保留历史）
- **P9-03 ✅**：NotificationType 3 类（ISSUE_ASSIGNED/STATUS_CHANGED/COMMENTED，正文含业务编号+操作者）
- **P9-04 ✅**：NotificationService（create/listMy/unreadCount/markRead 幂等/markAllRead 条件 UPDATE）
- **P9-05 ✅**：4 端点 self 资源 API（无权限码，ADR-019；Swagger 完整）
- **P9-06 ✅**：ownership 收敛 SQL（跨用户 404 不泄露；无 ADMIN 后门）
- **P9-07 ✅**：触发接线（create 变更分派/transition/comment，共事务；assign 复用 update 天然单路径）
- **P9-08 ✅**：收件人 Set 去重排除操作者；insert 失败上抛回滚不吞
- **P9-09 ✅**：前端通知中心（Header 铃铛 badge/列表/已读/全部已读/跳转含失效兜底/分页/登出重置）+ store + Vitest 15
- **Java ✅**：12 集成测试（真实 MySQL；数据/隔离/幂等/分页/触发/事务回滚）；mvn 299/299
- **Python ✅**：8 黑盒用例（真实 HTTP；三触发/筛选/跨用户/分页/401）；pytest 89/89
- **E2E ✅**：真实浏览器两轮（badge=3→点击已读跳转→全部已读→刷新持久化→切号隔离→二轮新通知 badge=1）
- **Gate ✅ PASS**：docs/testing/phase9-gate.md；ADR-019；两轮 299/89/120 全绿；终态全零；**无产品缺陷**

## 5.10 Phase 10 — Audit & Dashboard（✅ 全部完结并通过 Release Gate，2026-09-22）

- **目标**：审计日志（16 高价值接线点/双事务语义/敏感红线）+ 真实聚合仪表盘（角色数据范围/ECharts 可视化）；收口 §14 历史遗留
- **P10-01 ✅**：影响分析（issue_history/operation_log 均不存在；audit_logs 按字典 §15；§14 决策 D 不建表）
- **P10-02 ✅**：V14 audit_logs（字典 §15 + trace_id/summary/user_agent/created_at；索引 ×3）
- **P10-03 ✅**：AuditService（record 同事务 / recordStandalone REQUIRES_NEW / query 多条件稳定排序 / getById）
- **P10-04 ✅**：16 接线点（AUTH×3/USER×2/ORG×2/PROJECT×3/ISSUE×2/COMMENT×2/ATTACHMENT×2）
- **P10-05 ✅**：事务双语义 + 业务回滚不留成功审计（事务模板实证）
- **P10-06~07 ✅**：audit:list/get 权限（仅 ADMIN）+ 全系统数据范围（权限即范围）
- **P10-08 ✅**：§14 最终决策 D（字典更新替代说明，ADR-020）
- **P10-09~11 ✅**：Dashboard 指标（项目/Issue 四维分布/14 天创建趋势——拒绝假 resolved 趋势）/overview API/角色数据范围（空成员短路）
- **P10-12~13 ✅**：Dashboard 统计卡片+ECharts 饼图/趋势图；AuditView 筛选分页+无权限提示
- **P10-14~16 ✅**：Java 12 + Python 8 + Vitest 8；三线 311/97/128 连续两轮全绿
- **P10-17 ✅**：真实浏览器 E2E 两轮（业务=Audit=Dashboard 三方一致+权限隔离）
- **P10-18 ✅**：SQL review（GROUP BY 下推/无 N+1/空 scope 短路/索引核验）
- **P10-19 ✅**：audit.md/dashboard.md/字典 §14 更新/ADR-020/phase10-gate.md
- **Gate ✅ PASS**：**修复真实缺陷 2 个**（审计 user_id UNSIGNED 越界归一化；并发登录失败 REQUIRES_NEW 连接池死锁→login 移除事务）——第一轮按规则未宣布 PASS，根因分析→修复→完整回归通过

## 5.11 Phase 11 — Frontend Completion（✅ 全部完结并通过 Release Gate，2026-09-23）

- **目标**：Phase 2~10 分散前端能力产品化收口（gap analysis→补缺→系统浏览器回归），非重写
- **P11-01 ✅**：gap analysis（G1 无 404/G2 Users 页缺失/G3 列表解析缺陷；其余逐页证据齐备）
- **P11-02~03 ✅**：顶栏 Layout 保持（菜单权限矩阵 admin 7 项/user1 0 项）+ 404 catch-all + UsersView 补齐（api/user.ts 对齐契约）
- **P11-04~19 ✅**：Axios 归一化确认/页面状态矩阵/交互防重/危险确认/异常实证（401 会话被顶自动跳转/403 提示/404 兜底）/桌面响应式/代码收口（`as PageVO` 错误模式 grep=0）
- **P11-20 ✅**：Vitest 136/136（新增 UsersView 7+catch-all 1）
- **P11-21 ✅**：真实浏览器 E2E 两轮（21 步全链+8 项复验；业务=UI=审计三方一致+权限隔离）
- **P11-22~23 ✅**：视觉一致性（既有形态统一）+性能检查（无轮询/ECharts 销毁/无重复请求）
- **P11-24~25 ✅**：Gate；后端回归 mvn 311/pytest 97；终态全零
- **Gate ✅ PASS**：**修复真实缺陷 3 个**（listOrgs/listProjects 解析错误致列表恒空；issue.ts 同款自 Phase 6 潜伏——Vitest mock 掩盖、P11 浏览器回归首次暴露）；phase11-gate.md

## 6. Phase 4–19 里程碑概览

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
