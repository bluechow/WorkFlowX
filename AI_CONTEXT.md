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

**Phase 19 — Final Delivery：✅ 全部完结并通过 Release Gate（2026-09-25）**。**WorkFlowX V1.0.0 正式封版**（v1.0.0 本地 tag）。Phase 0~19 全部完结。

- **P3-01 ✅ DONE**：RBAC 设计基线（docs/architecture/rbac.md + ADR-012）+ V3 系统权限种子迁移（14 项 + ADMIN 全量绑定）；五表零结构变更，Phase 2 机制不动
- **P3-02 ✅ DONE**：rbac 模块领域能力（Entity/4 Mapper/DTO/VO/RbacConstants/三 Service：角色 CRUD+权限绑定、权限 CRUD+用户权限实时解析、用户角色幂等绑定）；新增测试 28 个，mvn 172/172，Phase 2 回归全绿（pytest 28/npm 45/运行时冒烟）
- **P3-03 ✅ DONE**：RbacController 13 端点（hasAuthority 全覆盖）+ Filter 权限实时接线（收权即时生效，运行时实证）+ UserController hasRole→hasAuthority + AuthRoleQueryMapper 统一删除；mvn 188/188
- **P3-04 ✅ DONE**：系统管理三页（角色/权限/用户角色）+ ADMIN 菜单 + api/rbac.ts；运行时冒烟权限矩阵 + 实时授予回收全过
- **P3-05 ✅ DONE**：/auth/me/permissions + 按钮级权限 UX + Python RBAC 10 用例；Vitest 58/58；Phase 3 收口
- **P4-01 ✅ DONE**：组织架构 V4 迁移 + org 模块领域能力 + 14 测试；ADR-013
- **P4-02 ✅ DONE**：V5 权限种子（25 系统权限）+ OrgController 13 端点 + OWNER 数据级规则 + 12 用例；mvn 214/214
- **P4-03 ✅ DONE**：组织管理前端两页 + 菜单权限化 + api/org.ts；Vitest 70/70
- **Phase 3 + Phase 4 ✅ Release Gate PASS**（docs/testing/phase34-gate.md）
- **P5-01 ✅ DONE**：V6 projects 迁移 + project 模块 + ADR-014（数据级归属）+ 11 测试；mvn 225/225
- **P5-02 ✅ DONE**：V7 权限种子（30 系统权限）+ ProjectController 5 端点 + 归属/归档/矩阵测试；mvn 235/235
- **P5-03 ✅ DONE**：项目管理前端 ProjectsView + 菜单 + api/project.ts；Vitest 77/77
- **P5-04 ✅ DONE**：V8 project_members + ADR-015（角色 OWNER/MANAGER/MEMBER + 组织成员前置）+ ProjectMemberService/Controller 3 端点；mvn 244/244
- **P5-05 ✅ DONE**：Python project+member 10 用例；pytest 48/48 两轮
- **Phase 5 ✅ Release Gate PASS**（docs/testing/phase5-gate.md）；本机动态端口范围已纠正（1024-15000 → 49152-65535，根治 8080 被 winnat 排除的间歇故障）
- **Phase 6 ✅ 全部完结并通过 Release Gate（2026-09-15）**：Issue 模块（V9/V10 迁移+6 端点+领域服务+Python 10 用例+前端 IssuesView+权限双层+数据级项目成员校验）；ADR-016；修复真实 Bug（取消分派 MP null 忽略）；mvn 263/pytest 48/Vitest 84 全绿；Gate 记录 docs/testing/phase6-gate.md
- **Phase 7 ✅ 全部完结并通过 Release Gate（2026-09-21）**：Workflow 状态机（ADR-017 + V11 issue:transition 权限 37 + WorkflowService 收敛 + 条件 UPDATE 并发 + Python 12 用例 + 前端 allowedTargets + E2E 22 断言归档）；mvn 264/pytest 60/Vitest 89 各连续两轮全绿；docs/testing/phase7-gate.md
- **Phase 8 ✅ 全部完结并通过 Release Gate（2026-09-22）**：Comment & Attachment（V12+ADR-018+MinIO 真实存储+E2E 双侧核验）；mvn 287/pytest 81/Vitest 105 各连续两轮全绿；docs/testing/phase8-gate.md
- **Phase 9 ✅ 全部完结并通过 Release Gate（2026-09-22）**：Notification（V13+ADR-019+前端通知中心+浏览器 E2E 两轮）；mvn 299/pytest 89/Vitest 120 各连续两轮全绿；docs/testing/phase9-gate.md
- **Phase 10 ✅ 全部完结并通过 Release Gate（2026-09-22）**：Audit & Dashboard（V14+ADR-020+16 接线点+角色数据范围+§14 决策 D；修复 user_id 越界+连接池死锁 2 缺陷）；mvn 311/pytest 97/Vitest 128 各连续两轮全绿；docs/testing/phase10-gate.md
- **Phase 11 ✅ 全部完结并通过 Release Gate（2026-09-23）**：前端收口（gap analysis+Users 页+404 兜底；修复列表解析 3 处真实缺陷）；Vitest 136+E2E 两轮；docs/testing/phase11-gate.md
- **Phase 12 ✅ 全部完结并通过 Release Gate（2026-09-23）**：API 自动化体系（架构+contract suite+业务链+清理自然归零）；pytest 115/mvn 311/Vitest 136 各两轮全绿；docs/testing/api-automation.md + phase12-gate.md
- **Phase 13 ✅ 全部完结并通过 Release Gate（2026-09-23）**：Playwright UI 自动化（架构+20 用例分层+全链；修复 ADMIN 刷新菜单消失真实缺陷）；四线各两轮全绿；docs/testing/ui-automation.md + phase13-gate.md
- **Phase 14 ✅ 全部完结并通过 Release Gate（2026-09-23）**：性能测试体系（JMeter JSR223+四级负载+附件独立+资源监控；基线 85/s→平台 354/s→拐点 40 线程；0% 错误率）；docs/testing/performance-testing.md + phase14-gate.md
- **Phase 15 ✅ 全部完结并通过 Release Gate（2026-09-24）**：安全测试体系（security suite 47+Playwright 7；修复空 filename multipart 500 缺陷+安全响应头加固）；docs/testing/security-testing.md + phase15-gate.md
- **Phase 16 ✅ 全部完结并通过 Release Gate（2026-09-24）**：Docker & Deployment（deploy compose+Dockerfile×2+Nginx SPA+备份恢复）；docs/deployment.md + docker-architecture.md
- **Phase 17 ✅ 全部完结并通过 Release Gate（2026-09-24）**：CI/CD（ci.yml 5 jobs+secrets scan；收敛 9 项数据生命周期缺陷）；docs/ci-cd.md + phase17-gate.md
- **Phase 18 ✅ 全部完结并通过 Release Gate（2026-09-25）**：Final QA（Feature Matrix 全 COMPLETE+文档一致性修复）；docs/final-qa.md + phase18-gate.md
- **Phase 19 ✅ 全部完结并通过 Release Gate（2026-09-25）**：Final Delivery（V1.0.0 封版；architecture/test-matrix/graduation-notes/resume-summary/demo-scenario/future-roadmap 交付文档；workflowx-release 全新部署验证；seed user_roles 恢复+非 seed 角色清理兜底）；docs/testing/phase19-gate.md
- **V1.0.0 封版完成。后续 V1.1/V1.2/V2.0 见 docs/future-roadmap.md，需按完整 Phase 流程重新立项**
- Phase 2 — Authentication & User 已于 2026-09-06 通过 Release Gate（25 任务全部完结）
- **P2-01 ✅ DONE**：User 实体（映射 V1 真实结构）+ UserStatus 枚举 + UserVO（无密码字段）+ dev 种子迁移 V2（db/seed/dev location 隔离，prod 不执行）+ 测试 16 个（mvn test 20/20 全绿含 P1 回归）
- **P2-02 ✅ DONE**：UserMapper（BaseMapper 极简）+ MybatisPlusConfig 分页插件（MySQL；MP 3.5.9+ 已补 mybatis-plus-jsqlparser 依赖）+ UserMapperTest 11 用例（真实 MySQL，SQL 实证 ORDER BY/LIMIT/count 正确）；mvn test 31/31 全绿
- **P2-03 + P2-04 ✅ DONE**：UserService（查询/分页/创建/更新/状态，唯一性 409、404/409 异常体系复用）+ PasswordService（BCrypt 10 统一入口，PasswordEncoderConfig 全局唯一 Bean）+ DTO Bean Validation + PageVO 统一分页结构；新增测试 30 个，mvn test 61/61
- **P2-05 + P2-06 ✅ DONE**：Spring Security 6 基础配置（STATELESS/CSRF off/公开端点白名单/统一 401·403 JSON，无 formLogin·httpBasic）+ JWT 基础设施（jjwt 0.12.6，HS256 服务端固定，JwtService 签发/解析/验证，JwtAuthenticationFilter 建立 Authentication；ADR-008 技术基线）；mvn test 82/82
- **P2-07 + P2-08 ✅ DONE**：Redis 登录会话（auth:session:{userId}→jti，TTL 2h 同源，单会话后登录覆盖，Filter 会话校验 fail-closed）+ 登录接口 POST /api/v1/auth/login（统一错误防枚举，状态检查在密码验证后，last_login_at 更新，LoginResponse 含 token/expiresIn/userId/username/roles）；**登录链路已真实打通**（真实 curl + 集成测试 14 个）；mvn test 96/96
- **P2-09 + P2-10 ✅ DONE**：POST /api/v1/auth/logout（认证必需，仅删当前用户会话，幂等，登出后原 token 立即 401）+ GET /api/v1/auth/me（SecurityContext 取 userId → UserService 读库返回最新 UserVO，无敏感字段，用户删除后 404）；新增集成测试 10 个，mvn test 106/106
- **P2-11 + P2-12 ✅ DONE**：用户管理 API 5 端点（ADMIN 后端强制 @PreAuthorize）+ 禁用即踢线（DISABLED 删会话旧 JWT 立即失效，ACTIVE 恢复需重登）+ 自操作守卫 400 + AuthorizationDeniedException 重抛修复（403 不再变 500）；ADR-009 权限模型；新增测试 22 个，mvn test 128/128
- **P2-13 + P2-14 ✅ DONE**：登录失败限制（auth:fail:{username}，5 次/15 分钟，统一计数防枚举，成功清除，fail-closed，ADR-010）+ OpenAPI Bearer SecurityScheme（根级 requirement + health 公开覆盖）；新增测试 13 个，mvn test 140/140
- **P2-14 修复 ✅ DONE**：login 空 @SecurityRequirements（公开）+ logout/me/users 显式 bearerAuth，文档声明与运行时鉴权一一对应；mvn test 140/140
- **P2-15 + P2-20 ✅ DONE**：Java 测试补 4 缺口（分页校验/路径类型/大小写语义/弱密钥 fail-fast）+ 2 异常处理器（BindException 422/TypeMismatch 400），mvn test 144/144；Python API 自动化体系建立（28 用例真实 HTTP 黑盒：auth/user/lockout + fixtures[单会话感知的 token/用户工厂/清理]），pytest 28/28，DB/Redis 清理零残留
- **P2-16 ~ P2-19 ✅ DONE**：前端认证闭环——登录页（真实 API/手动校验/loading/防重复）、Dashboard（/me 资料+登出）、路由守卫（fetchMe 确认认证有效性）、Axios（Bearer 注入/401 回登录防循环/403 不误登出）、Pinia auth store（localStorage token 持久化，ADR-011）；修复真实 P0（el-form validate 永久 pending）；真实浏览器 E2E 14 步全过
- **P2-21 + P2-22 ✅ DONE**：前端测试体系分层（Unit/Component/Integration，新增 36 测试至 45/45：token util/axios 拦截器真实链/路由守卫 8 场景/auth store 全场景/LoginView 交互/Dashboard）；docs/testing/test-data.md 建立测试数据全生命周期文档
- **P2-23 ✅ DONE**：Phase 2 全链路验证——E2E 脚本 42 项检查 2 轮全过（认证/锁定/覆盖/登出/踢线/权限矩阵/错误码 400~500/traceId/无泄漏）+ 三线回归（mvn 144、pytest 28、Vitest 45 全绿）+ 500 fail-closed 实测；报告 docs/testing/phase2-validation.md
- **P2-24 ✅ DONE**：文档收口——README/getting-started/api-conventions 更新至 Phase 2 实际状态；新增 docs/architecture/security.md（认证架构全景）
- **P2-25 ✅ DONE（Release Gate: PASS）**：交付/完整性/安全 8 项/文档 5 项核验全过；三线回归 mvn 144 + Vitest 45 + pytest 稳定 28；无产品缺陷未改生产代码；Gate 报告见 docs/testing/phase2-validation.md
- 下一步：**Phase 3 — RBAC**（用户-角色-权限模型与后端强制鉴权，V3 迁移），**等用户指令后启动**
- Phase 1 — Project Foundation 已于 2026-09-06 完成并通过验收（DoD 8/8）

---

## 3. 业务模块状态

| 模块 | 说明 | 状态 |
|---|---|---|
| auth | 认证（登录 / 登出 / JWT / Redis 会话） | ✅ Phase 2 完成 |
| user | 用户管理（CRUD / 状态 / 禁用踢线） | ✅ Phase 2 完成 |
| rbac | 角色-权限-用户绑定领域 + REST API + 权限接线 | ✅ P3-01~04 完成（13 端点 + 实时权限 authorities + 前端管理三页） |
| organization | 组织管理 | ⬜ 未开始（Phase 4，包占位已建） |
| rbac | 角色权限（User-Role-Permission） | 🔵 身份域 5 表已建（V1），功能属 Phase 3 |
| project | 项目管理 | ⬜ 未开始（Phase 5，包占位已建） |
| issue | Issue 管理（编号/类型/分派/状态基础） | ✅ Phase 6 完成 |
| workflow | Issue 状态机（ADR-017 矩阵/issue:transition/条件 UPDATE 并发） | ✅ Phase 7 完成 |
| comment | Issue 评论（author 绑定/ownership 三层/无软删除） | ✅ Phase 8 完成 |
| attachment | 附件元数据 + MinIO 存储（白名单/objectKey 服务端生成/一致性补偿） | ✅ Phase 8 完成 |
| notification | 站内通知（3 类型触发/self 隔离/共事务/前端通知中心） | ✅ Phase 9 完成 |
| audit | 审计日志（16 接线点/双事务语义/敏感红线/仅 ADMIN） | ✅ Phase 10 完成 |
| dashboard | 数据统计（真实聚合/角色数据范围/ECharts 可视化） | ✅ Phase 10 完成 |
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
| WSL 网络修复 | ✅ P2-23 期间修复 docker stop/start 后 localhost 转发失效问题：Hyper-V 防火墙放行 WSL VM 入站 + `netsh portproxy` 127.0.0.1:3307/6379 → WSL IP（**WSL 重启后 IP 变化需更新 portproxy connectaddress**，见 docs/testing/phase2-validation.md） |
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
| 2026-09-25 | Phase 19 完成：Final Delivery（V1.0.0 封版，v1.0.0 tag；交付文档七份：architecture/test-matrix/graduation-notes/resume-summary/demo-scenario/future-roadmap/deployment 更新；workflowx-release 全新部署验证+smoke 15/15）；phase19-gate.md | Phase 19 收口/V1.0.0 |
| 2026-09-25 | Phase 18 完成：Final QA（Feature Matrix+四线两轮 311/115/136/25+Docker fresh deployment 复验+文档一致性修复 README/how-to-run-tests）；docs/final-qa.md + phase18-gate.md | Phase 18 收口 |
| 2026-09-24 | Phase 17 完成：CI/CD（GitHub Actions ci.yml 5 jobs+secrets scan+docs/ci-cd.md）；收敛测试数据生命周期 9 项缺陷；四线各两轮全绿；Gate PASS（phase17-gate.md） | Phase 17 收口 |
| 2026-09-24 | Phase 16 完成：Docker & Deployment（backend/frontend Dockerfile+deploy compose 5 服务+Nginx SPA+反代+fresh deployment+Flyway+持久化+重启+备份恢复+安全端口评审）；docs/deployment.md + docker-architecture.md | Phase 16 收口 |
| 2026-09-24 | Phase 15 完成：安全测试体系（认证/会话/JWT 篡改/RBAC/IDOR 三层隔离/SQLi/XSS/文件穿越/敏感信息/审计/配置/Secrets 扫描/OWASP mapping）；**修复空 filename multipart 500 真实缺陷**+安全响应头加固；47+7 安全用例两轮全绿+四线回归；Gate PASS（phase15-gate.md + security-testing.md） | Phase 15 收口 |
| 2026-09-23 | Phase 14 完成：性能测试体系（JMeter JSR223 架构+四级负载+附件独立+资源监控+瓶颈分析）；基线 85/s→平台 354/s→拐点 40 线程；全程 0% 错误率；回归 mvn 311/pytest 115/Vitest 136 全绿；Gate PASS（performance-testing.md + phase14-gate.md） | Phase 14 收口 |
| 2026-09-23 | Phase 13 完成：Playwright UI 自动化体系（架构+Page Objects+storageState+20 用例分层+全链 spec）；修复 ADMIN 刷新菜单消失真实缺陷；四线各两轮全绿；Gate PASS（phase13-gate.md + ui-automation.md） | Phase 13 收口 |
| 2026-09-23 | Phase 12 完成：API 自动化体系（clients/factories/assertions 架构+contract suite+分层 markers+业务链 15 环节；单会话铁律+清理体系修复终态自然归零）；pytest 115/mvn 311/Vitest 136 各两轮全绿；Gate PASS（phase12-gate.md） | Phase 12 收口 |
| 2026-09-23 | Phase 11 完成：前端收口（gap analysis+Users 管理页+404 兜底+浏览器 E2E 两轮）；修复列表解析 3 处真实缺陷（orgs/projects/issues 浏览器端曾恒空）；Vitest 136/后端 311/97 回归；Gate PASS（phase11-gate.md） | Phase 11 收口 |
| 2026-09-22 | Phase 10 完成：Audit & Dashboard 全量（V14+ADR-020+16 接线点+双事务语义+角色数据范围聚合+ECharts 前端+§14 决策 D）；修复审计 user_id 越界与并发登录连接池死锁 2 个真实缺陷；三线 311/97/128 各两轮全绿；Gate PASS（phase10-gate.md） | Phase 10 收口 |
| 2026-09-22 | Phase 9 完成：Notification 全量（V13+ADR-019+3 类型共事务触发+self 资源隔离+前端通知中心+真实浏览器 E2E 两轮）；三线 299/89/120 各两轮全绿；Gate PASS（phase9-gate.md） | Phase 9 收口 |
| 2026-09-22 | Phase 8 完成：Comment & Attachment 全量（V12+9 权限 46 项+StorageService+文件安全白名单+前端抽屉+E2E 20 断言）；修复 StreamingResponseBody 连接污染真实缺陷；ADR-018；Gate PASS（phase8-gate.md） | Phase 8 收口 |
| 2026-09-21 | Phase 7 完成：Workflow 状态机（ADR-017 矩阵+V11 issue:transition+WorkflowService+条件 UPDATE 并发+Python 12+前端 allowedTargets+E2E 22 断言归档）；三线 264/60/89 连续两轮全绿；Gate PASS（phase7-gate.md） | Phase 7 收口 |
| 2026-09-15 | Phase 6 完成：Issue 模块全量（V9/V10+领域+6 端点+Python 10+前端 IssuesView）；修复取消分派 MP null Bug；Gate PASS（phase6-gate.md） | Phase 6 收口 |
| 2026-09-15 | P5-03~05+Gate 完成：项目前端+V8 成员迁移+31 权限+Python 10 用例；三线 244/48/77 全绿；Gate PASS（phase5-gate.md）；修复 winnat 排除段占用 8080 | Phase 5 收口 |
| 2026-09-15 | P4-03+Gate+P5-01+P5-02 完成：组织前端+Phase3/4 Gate PASS+V6/V7 迁移+Project 5 端点+数据级归属；mvn 235/Vitest 70/pytest 43 全绿 | Phase 4 收口+Phase 5 |
| 2026-09-15 | P3-05+P4-01+P4-02 完成：Phase 3 收口（/me/permissions+按钮 UX+10 Python 用例）；组织架构 V4/V5 迁移+13 REST 端点+OWNER 规则；mvn 214/Vitest 58/pytest 38 全绿 | Phase 3 收口+Phase 4 |
| 2026-09-06 | P3-03+P3-04 完成：RBAC 13 REST 端点 + Filter 权限实时接线（收权即时生效实证）+ 前端系统管理三页；mvn 188/188 + Vitest 56/56 + pytest 28/28 | Phase 3 |
| 2026-09-06 | P3-01+P3-02 完成：RBAC 设计基线（rbac.md/ADR-012）+ V3 权限种子 + rbac 模块领域服务 + 28 测试（mvn 172/172）；Phase 2 回归全绿 | Phase 3 |
| 2026-09-06 | P2-25 完成：Phase 2 Release Gate **PASS**（交付/完整性/安全/文档核验 + 三线回归；pytest 首轮环境瞬时竞态已定位复验）；报告见 phase2-validation.md §Release Gate | Phase 2 收口 |
| 2026-09-06 | P2-23 完成：全链路 E2E 42 项 2 轮全过 + 三线回归（mvn 144/pytest 28/Vitest 45）；修复 WSL 转发失效（Hyper-V 防火墙 + portproxy）；报告 docs/testing/phase2-validation.md | Phase 2 |
| 2026-09-06 | P2-24 完成：README/getting-started/api-conventions 收口 + docs/architecture/security.md | Phase 2 |
| 2026-09-06 | P2-21 + P2-22 完成：前端测试体系分层（45/45）+ test-data.md | Phase 2 |
| 2026-09-06 | P2-03+P2-04 完成：UserService/PasswordService/DTO 校验/PageVO，新增 30 测试（mvn 61/61），修复 @Email local≤64 边界认知 | Phase 2 |
| 2026-09-06 | P2-02 完成：UserMapper + 分页插件（MP 3.5.9+ jsqlparser 拆分修正），mvn 31/31 | Phase 2 |
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
