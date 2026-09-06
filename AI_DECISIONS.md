# WorkFlowX 技术决策记录（AI_DECISIONS）

| 项 | 值 |
|---|---|
| 定位 | 重要技术决策的**唯一记录处**（ADR, Architecture Decision Record） |

**使用规则：**

1. 今后所有重要技术决策（架构、技术选型、数据模型原则、安全策略、关键流程）必须在本文件追加 ADR
2. 禁止静默推翻已接受的决策；推翻时新增 ADR，并将旧决策状态改为 `Superseded`
3. ADR 一旦 `Accepted`，不可原地修改决策内容，只能变更状态

---

## ADR 模板

```markdown
### ADR-XXX: <标题>
- 日期: YYYY-MM-DD
- 状态: Proposed / Accepted / Deprecated / Superseded (by ADR-YYY)
- 背景: 为什么需要做这个决策
- 决策: 决定了什么
- 理由: 为什么这样决定
- 备选方案: 考虑过哪些、为何放弃
- 影响: 对架构 / 开发 / 测试 / 部署的影响
```

---

## 已接受决策

### ADR-001: 采用 Modular Monolith（模块化单体）架构

- 日期: 2026-09-06
- 状态: Accepted
- 背景: WorkFlowX 面向中小团队，当前阶段微服务拆分没有真实业务驱动。
- 决策: 整体采用模块化单体。后端按 auth / user / organization / project / issue / notification / audit / dashboard / system / common 模块划分，模块间保持清晰边界。
- 理由: 降低部署、调试与运维复杂度；模块边界清晰，未来确有需要时仍可拆分。
- 备选方案: 微服务架构（放弃：运维成本高，当前规模收益为负）。
- 影响: 部署形态为单一 Spring Boot 应用 + 基础设施容器（MySQL / Redis / MinIO）。

### ADR-002: 技术栈选定

- 日期: 2026-09-06
- 状态: Accepted
- 背景: 项目启动前需固定技术栈，避免后续随意引入栈外技术。
- 决策: 后端 Java 21 + Spring Boot 3.x（Spring MVC / Spring Security / MyBatis-Plus / Bean Validation / JWT）；前端 Vue 3 + TypeScript + Vite + Vue Router + Pinia + Axios + Element Plus + ECharts；数据层 MySQL 8.x + Redis + MinIO；测试 Python + Pytest + Requests/HTTPX + Playwright + Allure + JMeter；DevOps Git + Docker Compose + GitHub Actions。
- 理由: 由用户在 Master Prompt §3 中指定，与本项目的目标场景匹配。
- 备选方案: 不适用（用户指定）。
- 影响: 全部代码以此为准；引入栈外技术必须新增 ADR 说明理由。

### ADR-003: 认证采用 JWT，授权采用 RBAC 且由后端强制执行

- 日期: 2026-09-06
- 状态: Accepted
- 背景: 平台需要可靠的认证与授权，且必须防御绕过前端的直接 API 调用。
- 决策: 认证使用 JWT；授权使用 RBAC（User → UserRole → Role → RolePermission → Permission），权限校验强制在后端执行，前端权限仅用于 UI 展示。
- 理由: 满足 Master Prompt §6/§7 的要求；后端强制鉴权是安全的底线，无法依赖前端。
- 备选方案: 会话（Session）机制（放弃：与前后端分离 + 水平扩展目标不匹配）。
- 影响: Security 过滤器链、权限注解机制、越权测试用例均以此为前提。

### ADR-004: 测试体系独立于业务代码

- 日期: 2026-09-06
- 状态: Accepted
- 背景: WorkFlowX 同时是测试学习平台，测试本身是一等公民。
- 决策: 测试代码放在独立的 `tests/` 目录（api / ui / performance / fixtures / data / utils / config），采用 Python + Pytest（API）、Playwright（UI）、Allure（报告）、JMeter（性能），不与 Java 业务代码混合。
- 理由: 满足 Master Prompt §19；独立目录保证测试可用性不受业务代码重构影响，且便于独立演进。
- 备选方案: Spring Boot 内置单元测试（保留：单元测试仍放在 backend 模块内，本 ADR 约束的是自动化测试体系）。
- 影响: Phase 12–15 的自动化建设均在此目录展开。

### ADR-005: API 统一规范

- 日期: 2026-09-06
- 状态: Accepted
- 背景: API 是前后端与自动化测试的契约，必须从一开始就规范。
- 决策: 所有 API 统一前缀 `/api/v1/`；正确使用真实 HTTP 状态码（200/201/400/401/403/404/409/422/429/500），禁止一律返回 200；统一响应结构至少包含 code / message / data / timestamp / traceId。
- 理由: 满足 Master Prompt §9；真实状态码是自动化测试断言与监控告警的基础。
- 备选方案: 全部 200 + 业务码（放弃：掩盖故障，不利于监控与测试）。
- 影响: 后端统一响应体、全局异常处理、API 自动化断言均以此为前提。

### ADR-006: 建立 AI 总控文件体系

- 日期: 2026-09-06
- 状态: Accepted
- 背景: 高质量 AI 项目不能只靠一份 Master Prompt，需要项目控制层约束全生命周期。
- 决策: 项目根目录维护五份总控文件（AI_MASTER_PROMPT / AI_WORKFLOW / AI_CONTEXT / AI_TASKS / AI_DECISIONS）+ `AGENTS.md` 入口（规定加载顺序），并以此作为 Phase 0 的交付物。
- 理由: 将"永久规则 / 工作方式 / 当前状态 / 任务 / 决策"分离，各司其职，AI 每次会话可快速恢复上下文并受规则约束。
- 备选方案: 单一巨型规则文件（放弃：职责混杂，状态类信息频繁变更会污染永久规则）。
- 影响: AI 的所有工作受此体系约束；任务完成后必须同步更新 AI_CONTEXT.md 与 AI_TASKS.md。

### ADR-007: 本地开发基础设施统一通过 Docker Compose 管理

- 日期: 2026-09-06
- 状态: Accepted
- 背景: Phase 1 环境检查发现本机 Docker/WSL 未安装，同时存在原生 `MySQL80` 服务（3306）。需明确本地基础设施的统一管理方式，避免 Docker 与原生服务混用造成环境漂移（决策项 P1-ENV-1 / P1-ENV-2，用户确认均为方案 A）。
- 决策: MySQL、Redis、MinIO 统一优先通过 Docker Compose（WSL2 + Docker Desktop）提供与管理；不采用 Windows 原生 Redis/MinIO 作为正式开发基础设施；本机原生 MySQL80 服务保留但不用于 WorkFlowX。环境决策 P1-ENV-1（安装 JDK 21 LTS 并设为 JAVA_HOME）为履行既有 ADR-002，不修改、不新增 ADR。
- 理由: 与 Master Prompt §24 及 Phase 16 部署形态一致；环境一致性与可重建性优先；规避 Windows 原生 Redis 无官方支持的质量风险。
- 备选方案: Phase 1 暂用本机原生服务（用户否决）；直连原生 MySQL80（放弃：与"统一 compose 管理"冲突）。
- 影响: Phase 1 任务 1-2 按 compose 路径验收；compose MySQL 主机端口默认 3307（本机 3306 被原生服务占用）；后续所有 Phase 的数据层均以 compose 实例为基准。

### ADR-008: JWT 签发与验证技术基线

- 日期: 2026-09-06
- 状态: Accepted
- 背景: Phase 2 认证体系需要确定 Token 技术细节（决策项 D2/D3 用户已确认：无 Refresh Token、2 小时 + 单会话）。
- 决策: JWT 实现统一采用 jjwt 0.12.x（全项目唯一 JWT 实现）；算法服务端固定 HS256（signWith/verifyWith 同一密钥，客户端算法声明不参与验证，alg=none/算法混淆天然拒绝）；Claims = sub(userId)/username/roles/jti/iat/exp，禁止写入密码、email 等敏感字段；TTL 2 小时；secret 通过环境变量 JWT_SECRET 注入，dev 默认值仅为本地开发（.env.example 标注），prod 缺失即启动失败；角色写入 roles claim，Filter 中映射为 `ROLE_{code}` authorities 与后续 hasRole 对齐；P2-06 阶段 Token 验证仅为密码学验证，会话级校验（Redis 白名单）由 P2-07 增加。
- 理由: 满足 Master Prompt §6 与安全基线；jjwt 0.12 是 Spring 生态主流轻量选择；服务端固定算法消除算法混淆攻击面。
- 备选方案: nimbus-jose-jwt / spring-security-oauth2-jose（放弃：重且与既有选型重复）；引入 Refresh Token（放弃：见 D2 决策）。
- 影响: JwtService 为全项目唯一签发/解析入口；P2-07 的 Redis 会话将以 payload 的 userId/jti 为键；后续认证测试使用独立测试密钥。
