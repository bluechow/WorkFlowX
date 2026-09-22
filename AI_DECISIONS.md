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

### ADR-009: 用户管理 API 权限模型与禁用即踢线

- 日期: 2026-09-06
- 状态: Accepted
- 背景: P2-11/P2-12 将用户管理暴露为 REST API，需要确定权限执行位置、状态变更联动与会话行为（业务规则 D1 自禁用禁止/允许互禁/最后管理员保护暂不实现，均经用户确认）。
- 决策:
  1. 权限模型: 用户管理 5 端点（GET /users、GET /users/{id}、POST /users、PUT /users/{id}、PATCH /users/{id}/status）统一 `@PreAuthorize("hasRole('ADMIN')")`（SecurityConfig `@EnableMethodSecurity`），角色来自 JWT roles claim → Filter 映射的 `ROLE_*` authorities，后端强制执行；未认证 401、非 ADMIN 403。
  2. 参数校验与授权的顺序: Spring 标准行为为请求体校验（@Valid，422）先于方法级授权（403）——非法请求体无论角色返回 422，属可接受语义（不泄漏数据存在性）。
  3. 禁用即踢线: PATCH status 将目标置为 DISABLED 的同一事务内删除 `auth:session:{targetUserId}`，目标现有 JWT 因会话校验失败立即 401；恢复 ACTIVE 不自动创建会话，须重新登录。
  4. 自操作守卫: operatorId（取自 SecurityContext）等于目标 userId 时拒绝（400"不能修改自己的状态"）。
  5. 已确认不实现: "最后一个可用 ADMIN 保护"（当前单 ADMIN 且禁止自禁用，规则暂无触发场景，记录为未决规则）；SUPER_ADMIN 层级（系统无此角色）。
- 理由: 后端强制授权满足 Master Prompt §7（禁止仅前端隐藏）；踢线保证禁用立即生效，不留"库中禁用、会话存活"的窗口。
- 备选方案: 最后管理员保护（用户决策暂缓）；URL/请求参数传递角色（放弃：不可信信息源）。
- 影响: 新增 Controller 层角色守卫依赖 ADR-008 的 roles claim；Phase 3 RBAC 将以同机制扩展细粒度权限；AuthorizationDeniedException 必须重抛给 Security（不可被全局异常兜底拦截，否则 403 变 500——已修真实 bug）。

### ADR-010: 登录失败限制与统一计数语义

- 日期: 2026-09-06
- 状态: Accepted
- 背景: P2-13 需防暴力破解（15 分钟窗口 5 次失败 → 锁定 15 分钟），且不得破坏账号枚举防护（任务硬性要求）。
- 决策:
  1. 计数与锁定: Redis `auth:fail:{username}`，INCR 原子计数；首次失败设 EXPIRE 900，第 5 次失败将 TTL 重置 900（锁定 15 分钟）；GET ≥5 即锁定；成功登录 DEL 清零。
  2. **统一计数语义**: 对不存在的 username 同样计数与锁定——否则 429 仅出现在真实用户名上，会成为账号枚举通道。代价: 攻击者可故意锁住任意 username 15 分钟（限流方案标准取舍），已接受。
  3. 锁定期间: 不查库、不验密码、不签发 JWT、不建会话；正确密码同样 429；`users.status` 保持不变（Redis 临时锁定 ≠ 数据库 LOCKED）。
  4. fail-closed: Redis 异常不吞——计数/会话不可用则登录按统一服务错误失败（500），不放行。
  5. 错误信息: 前 4 次失败 401"用户名或密码错误"；第 5 次起 429"登录尝试次数过多，请稍后再试"——消息与用户存在性完全解耦。
  6. OpenAPI: 全局 bearerAuth SecurityScheme（http/bearer/JWT）+ 根级 SecurityRequirement（所有未覆盖操作继承）；health 端点以空 @SecurityRequirements 标记公开；Swagger 资源公开不改变业务 API 鉴权。
- 理由: INCR 原子性消除并发竞态；统一计数是唯一同时满足"5 次锁定"与"防枚举"两要求的方案。
- 备选方案: 仅对存在用户计数（放弃：429 泄漏用户存在性）；单独 lock key（放弃：count≥5 判断已等价，少一个键）。
- 影响: 暴力破解被限制为每 username 15 分钟 5 次；测试/清理需覆盖 auth:fail:* 键（TTL 900s 跨运行残留）；P2-15 起的自动化测试沿用同一限制。

### ADR-019: Notification 通知模块设计基线

- 日期: 2026-09-22
- 状态: Accepted
- 背景: Phase 9（Master Prompt §5；数据字典 §13）实现站内通知。要求真实业务触发、最小实现、不引入 MQ/WebSocket。
- 决策:
  1. **类型最小集 3 类**: ISSUE_ASSIGNED / ISSUE_STATUS_CHANGED / ISSUE_COMMENTED，仅映射既有业务触发点，不预设类型。
  2. **self 资源，无 notification:* 权限码**: 对齐 /auth/me 先例；Controller 仅要求认证；数据隔离由 Service/SQL 层 recipient ownership 强制（跨用户 404 不泄露存在性）；ADMIN 无全局查看后门。
  3. **数据模型最小扩展**: 字典 §13 基础上增加 read_at（与 is_read 同步写）、related_type+related_id（跳转必需）、created_at；通知不建 FK（Issue 删除后通知保留历史，跳转失效由前端 projectId=null 兜底）。
  4. **共事务**: 通知在主业务 @Transactional 内（REQUIRED）——主业务回滚通知同回滚；insert 失败上抛回滚主业务（不吞异常）。无 MQ、无异步投递。
  5. **已读语义**: 单条幂等（二次已读成功）；全部已读为数据库条件 UPDATE（只影响本人未读），非内存遍历。
  6. **触发收敛**: 4 个业务方法各一处（create 变更分派/update/transition/comment），assign 端点复用 update 故天然单路径；收件人 Set 去重并排除操作者。
  7. **列表无 N+1**: 通知正文自包含业务上下文；related Issue 的 projectId 单次 IN 批量解析。
- 理由: 最小实现满足"真实业务通知"要求；共事务保证"业务成功才产生通知"的强一致语义，失败概率极低（单表 insert）不值得引入异步补偿。
- 备选方案: MQ 异步投递（放弃：明确禁止且无规模需求）；WebSocket 实时推送（放弃：需求排除）；notification:* 权限码（放弃：self 资源加权限码会造成 ADMIN 权限语义混乱）；通知表建 FK 级联删除（放弃：通知是历史记录，Issue 删除不应抹掉收件历史）。
- 影响: 系统权限保持 46 项不变；notifications 表由测试 fixture 显式清理（无级联）；后续 Audit（Phase 10）可复用 related_type/related_id 关联模式；实时性留待后续阶段按需求演进。

### ADR-018: Comment 与 Attachment（MinIO）设计基线

- 日期: 2026-09-22
- 状态: Accepted
- 背景: Phase 8（Master Prompt §5/§13；数据字典 §11/§12）实现 Issue 评论与附件。库内已有表结构定义，文件二进制必须存 MinIO（compose.yaml 既有的 workflowx bucket，不建第二套存储）。
- 决策:
  1. **命名以数据字典为准**: 表 `issue_comments`/`attachments`；字段 `file_name`/`file_size`（非 original_filename/size_bytes）；comment 增补 `updated_at`（可编辑语义）。
  2. **权限 9 项（V12 种子，37→46）**: comment:list/get/create/update/delete + attachment:list/get/upload/delete。数据级=项目成员；**ownership 第三层**: 编辑/删除仅作者/上传者本人，**ADMIN 非 owner 同样 403**（对齐 Phase 4 org 删除仅 OWNER 的先例，Service 数据级不因角色豁免）。
  3. **文件安全**: 扩展名白名单 12 类（jpg/jpeg/png/gif/webp/pdf/txt/doc/docx/xls/xlsx/zip），不信任 Content-Type（仅记录）；上限 `attachment.max-size-bytes`（默认 10MB，可配）+ Spring multipart 11MB 兜底；超限 413、白名单外/空文件/非法文件名 422；文件名去路径分量/控制字符/限长 120；**objectKey 服务端生成** `issues/{issueId}/{uuid}-{safeName}`（防碰撞+防 path traversal，库内 UNIQUE 兜底）。
  4. **下载走后端鉴权**: 同步 byte[] 响应（10MB 上限内存可控）+ RFC 5987 文件名编码；**不用 presigned URL、bucket 不公开**。放弃 StreamingResponseBody——实测其异步写出污染 keep-alive 连接（后续请求 Server disconnected）。
  5. **一致性**: 上传 = MinIO 成功 → insert 元数据，insert 失败补偿删对象（补偿失败记 ERROR 不吞）；删除 = **先删对象后删元数据**（对象删除失败则元数据保留，避免悬空记录）。
- 理由: ownership 不豁免与既有先例一致且规则最小；白名单 + 服务端 objectKey 是文件上传的最小安全闭环；同步下载在该大小上限下简单可靠。
- 备选方案: presigned URL（放弃：多一跳鉴权复杂度，暂无性能需求）；独立 comment/attachment 顶层模块包（放弃：作为 Issue 子资源放 issue 包，与 project-member 先例一致）；宽松 Content-Type 检测（放弃：magic number 库引入成本高于白名单收益）。
- 影响: 系统权限 46 项（RbacConstants 守护同步）；Comment/Attachment 随 Issue FK 级联删除，但 **org 级联删除不回收 MinIO 对象**——测试/清理必须显式走 DELETE API 或按 object_key 清理（E2E 已覆盖终态=0 断言）；下载为同步阻塞读，Phase 14 性能测试若成瓶颈再评估 presigned/流式。

### ADR-017: Issue Workflow 状态转换矩阵

- 日期: 2026-09-15
- 状态: Accepted
- 背景: Phase 6（ADR-016.3）明确状态流转矩阵属 Phase 7。Master Prompt §15 已给出示例主链（OPEN→IN_PROGRESS→RESOLVED→TESTING→CLOSED；测试失败 TESTING→REOPENED→IN_PROGRESS），Phase 7 基于该既有定义形成正式矩阵。
- 决策:
  1. **正式矩阵（唯一合法集合，共 6 条）**: OPEN→IN_PROGRESS；IN_PROGRESS→RESOLVED；RESOLVED→TESTING；TESTING→CLOSED；TESTING→REOPENED；REOPENED→IN_PROGRESS。
  2. **CLOSED 为唯一终态**（无出边）；REOPENED 只能进 IN_PROGRESS 重新进入工作流。
  3. **禁止跳过中间状态**（如 OPEN→CLOSED、OPEN→RESOLVED），保守矩阵防误操作与统计失真。
  4. **禁止相同状态重复 transition** → 409。
  5. **非法 transition → 409 Conflict**，message 携带当前状态与允许目标列表。
  6. **并发保护**: 条件 UPDATE（`WHERE status = fromStatus`）乐观并发；条件不满足（状态已被并发变更）→ 409。不新增 version 字段。
  7. **权限**: 新增 issue:transition authority（V11 种子，ADMIN 绑定）；PATCH status 端点从 issue:update 迁移为 issue:transition；数据级仍要求操作者为项目成员（沿 ADR-016.4）。不新增 reason/comment 等字段。
- 理由: 严格主链+REOPENED 回路完全对齐 Master Prompt §15 既有示例；条件 UPDATE 在不增加 schema 的前提下根治"最后写入覆盖"。
- 备选方案: 宽松矩阵（允许 OPEN→CLOSED 等跳转，放弃：误操作风险）；version 乐观锁字段（放弃：状态场景条件 UPDATE 足够，少一列）；issue:transition 沿用 issue:update（放弃：Phase 7 起流转为独立受控能力）。
- 影响: ADR-016.3 由本 ADR 取代（Phase 6"仅枚举校验"状态随即终止）；IssueController PATCH status 请求体新增 fromStatus 必填字段（对旧客户端为破坏性变更，当前无外部消费者）；系统权限 36→37。

### ADR-016: Issue 编号并发分配与状态边界

- 日期: 2026-09-15
- 状态: Accepted
- 背景: P6-01/02 要求 issue_no 为项目内递增序号、并发下不可重复、禁止 `SELECT MAX+1` 无保护写法；同时须划定 Phase 6 状态能力边界。
- 决策:
  1. **序号分配**: V9 为 projects 增加 `issue_seq BIGINT NOT NULL DEFAULT 0` 计数器列；创建 Issue 时在同一事务内执行 `UPDATE projects SET issue_seq = issue_seq + 1 WHERE id = ?`（InnoDB 行锁串行化并发），随后 `SELECT issue_seq` 取得本次序号并写入 issue_no。`UNIQUE(project_id, issue_no)` 作为最终防线。计数器不回收：项目删除级联清 issues 后 issue_seq 随项目行消失，不产生正确性问题。
  2. **业务编号**: 对外展示 `project.key-issue_no`（如 WFX-12），由 VO/前端拼装，库内不冗余存储。
  3. **Phase 6 状态边界**: status 仅校验枚举合法值（PATCH，非法 422），**不实现流转矩阵**（Phase 7 Workflow 负责）；issue:transition authority 本阶段不引入，状态端点使用 issue:update。
  4. **数据级权限**: Issue 写操作（create/update/status/assignee）要求操作者为**项目成员**（project_members，强于 ADR-014 的组织成员规则）；读操作仅 authority。
  5. **severity**: 仅 type=BUG 可设置；type≠BUG 且携带 severity → 400；severity 可空，无默认值。
  6. **归档项目建 Issue**: 既有任务/ADR 未定义禁止规则 → 本阶段不限制，记录为待决策项。
- 理由: 行锁递增是单库场景下最简洁可靠的并发序号方案；不引入独立 sequence 表（少一张表与一次 JOIN）；状态边界严格遵守 Phase 划分。
- 备选方案: 独立 issue_sequence 表（放弃：多一表多一次 JOIN，收益相同）；SELECT MAX+1 后 INSERT（禁止：并发重复）；UUID 业务编号（放弃：违背 data-dictionary 既有 issue_no 设计）。
- 影响: ProjectMapper 新增 incrementIssueSeq/selectIssueSeq；issues 表 UNIQUE 兜底并发；Phase 7 引入流转矩阵时需新增 issue:transition authority 并修订本 ADR。

### ADR-015: 项目成员模型与前置规则

- 日期: 2026-09-15
- 状态: Accepted
- 背景: P5-04 项目成员是 Issue/Workflow 权限的基础。任务定义未明确成员角色枚举，数据字典 project_members.role 标注"随 Phase 5 细化"。
- 决策:
  1. 角色最小集 OWNER/MANAGER/MEMBER（V8 ENUM）：创建者即 OWNER（同事务写入，唯一，不可移除/降级）；MANAGER 后续可承接项目管理能力；MEMBER 为普通成员。未采用 DEVELOPER/TESTER 五级——当前无对应业务能力，避免空转角色。
  2. **组织成员前置**: 添加项目成员要求目标用户已是项目所属组织成员（organization_members 存在）→ 否则 400"用户须先加入组织"。移出组织（级联删组织成员行）不影响其历史项目成员行（不级联清理，保留追溯；用户删除才级联删项目成员）。
  3. 权限: project:assign_member（V8 种子）管理成员；project:get 查看列表；操作者数据级校验=须为项目所属组织成员（同 ADR-014）。
  4. 不提供"修改角色"端点（任务未要求）；移除后重新添加允许（角色重置为指定值）。
- 理由: 最小可行且为 Issue 阶段留空间；组织前置保证项目成员 ⊆ 组织成员，权限边界清晰。
- 备选方案: 五级角色（放弃：无业务能力对应）；自动加入（放弃：成员应受控）；删除项目成员随组织移除级联（放弃：丢失追溯）。
- 影响: Issue 分配人候选集=project_members；P5-05 Python 用例覆盖前置规则。

### ADR-014: 项目模型与组织归属数据级权限

- 日期: 2026-09-15
- 状态: Accepted
- 背景: Phase 5 项目管理启动。projects 在数据字典已有设计（key UK/org_id/status），需确定负责人字段、归档策略与组织级数据权限。
- 决策:
  1. V6 projects：数据字典设计基础上新增 owner_id（创建者/负责人合一，逻辑引用）；`key` 全局唯一且不可修改（正则 `^[A-Z][A-Z0-9]{1,19}$`，Issue 编号前缀）；FK(org_id) CASCADE。
  2. 归档策略：status ENUM(ACTIVE/ARCHIVED)，无物理删除端点；project:delete authority 预留不用。
  3. 数据级权限：写操作（create/update/status）要求操作者是目标组织成员（organization_members 存在，含 OWNER）→ 403；与 project:* authority 叠加（authority 是能力，归属是范围）。
- 理由: 满足 Master Prompt §7 后端强制与真实企业场景（跨组织操作者即使有全局权限也不应越组织写数据）；为 project_members/Issue 留出细粒度模型空间。
- 备选方案: 仅 authority 无归属校验（放弃：任意组织互写）；按项目成员校验（放弃：Project Member 属后续任务，组织成员是最小可行边界）。
- 影响: P5-02 五端点 + V7 权限种子；project_members 引入后可在组织成员之上细化到项目成员（届时修订本 ADR）。

### ADR-013: 组织架构模型与归属设计

- 日期: 2026-09-06
- 状态: Accepted
- 背景: Phase 4 组织架构需支持后续 Project 归属与用户部门归属；数据字典已预设计 organizations/organization_members，未含部门。
- 决策: V4 迁移落地 organizations（code UK/owner_id 逻辑引用）、departments（org 内树：parent_id 自引用 FK，UK(org_id, code)，删父提升子级）、organization_members（复合 PK，role ENUM(OWNER/ADMIN/MEMBER)，department_id SET NULL）；users 表零改动（归属关系全部走成员表）；组织删除仅 OWNER（数据级规则，与 org:delete authority 叠加）；部门树防环（同组织/非自身/非自身后代）；org 权限 11 项入 V5 种子并扩充系统权限保护清单。
- 理由: 成员表承载归属使 users 保持单一职责；SET NULL 策略保证部门调整不丢失成员关系；owner 校验是业务规则而非权限（授权给非 owner 的 ADMIN 仍不可删他人组织）。
- 备选方案: users 加 org_id/department_id 列（放弃：破坏 users 单一职责与"零改动"原则）；部门层级物化路径（放弃：当前深度需求简单，邻接表足够）。
- 影响: P4-02 的 OrgController 以 hasAuthority + owner 规则双重控制；Project（V6）引用 org_id。

### ADR-012: RBAC 数据模型与权限体系基线

- 日期: 2026-09-06
- 状态: Accepted
- 背景: Phase 3 启动。V1 五表（users/roles/permissions/user_roles/role_permissions）已定稿且 permissions/role_permissions 为空表，需确定权限编码规范、系统角色/权限清单、初始化策略及与 Phase 2 Spring Security 机制的衔接。
- 决策:
  1. 零表结构变更：沿用 V1 五表，RBAC 能力以领域服务实现（com.workflowx.rbac 模块）。
  2. 权限编码 `{resource}:{action}`（小写 snake_case，两段式，正则守护）；系统权限 14 项（user 6 + role 6 + permission 2）由 V3 公共迁移种子（产品数据，NOT EXISTS 幂等，dev/prod 均执行）；ADMIN 绑定全部，MEMBER 暂无管理权限。
  3. 系统角色（ADMIN/MEMBER）与系统权限由代码常量守护（禁止删除/系统角色 code 不可改），不加数据库标记列。
  4. JWT 继续只携带 roles claim（ADR-008 延续）；权限以服务端实时查询为准（PermissionService.findPermissionCodesByUserId），后续任务将 permission authorities 接入 Filter/注解（hasAuthority）——权限变更实时生效且 token 体积不受权限数影响。
  5. Phase 2 机制完全保持：hasRole('ADMIN') 注解、AuthRoleQueryMapper、Filter 的 ROLE_ 映射均不修改。
- 理由: 满足 Master Prompt §7 后端强制授权；渐进式演进避免一次性重写 Phase 2 已过 Gate 的认证链路。
- 备选方案: permissions 写入 JWT（放弃：token 膨胀 + 收权不实时）；增加 is_system 列（放弃：代码常量足够，少一次 DDL）。
- 影响: P3-02 提供领域服务但不暴露 REST API（属后续任务）；权限注解细化（hasAuthority）在后续任务接线；业务模块（Phase 4+）新增权限时须先在 rbac.md 登记。

### ADR-011: 前端认证闭环与 Token 存储方案

- 日期: 2026-09-06
- 状态: Accepted
- 背景: P2-16~P2-19 建立前端认证闭环，需确定 Token 持久化、认证状态来源与 401/403 行为。
- 决策:
  1. Token 存储: localStorage `workflowx_access_token`（无 Refresh Token 架构下维持刷新登录态）。localStorage 非高安全等级方案，当前阶段（学习项目、无富文本 XSS 入口）接受；不存储密码/密码哈希/任何 secret。
  2. 认证状态来源: Pinia auth store 持有 accessToken/roles/currentUser；**currentUser 唯一来源为 GET /auth/me**（数据库实时数据），login 响应字段仅作过渡；"token 存在 ≠ 认证有效"，路由守卫对无 currentUser 的已存 token 场景强制 fetchMe 校验。
  3. Axios 行为: 请求拦截自动注入 Bearer；401（非 login 请求、非登录页）→ 清 token + 回 /login（防循环）；403 不自动登出（交页面展示）；5xx 归一化为安全消息。
  4. 登出: 先尽力调用后端 logout（401 视为已失效），本地无条件清理后回 /login，用户绝不卡在登录态。
  5. 前端角色仅用于 UI 展示/控制，权限强制始终在后端 @PreAuthorize（Master Prompt §7）。
  6. 登录页校验: 同步手动校验 + 错误区展示——不依赖 el-form validate() 异步 Promise（真实 Chrome 环境观测到其永久 pending 导致登录不可用，已修复；测试佐证 P2-16 修复记录）。
- 理由: 与 Master Prompt §6"前端权限仅 UI 展示"及无 Refresh Token 基线（D2）一致；/me 实时化保证禁用/资料变更立即反映。
- 备选方案: 内存 token + 刷新即登出（放弃：体验差且无安全增益）；Cookie+CSRF（放弃：与无状态 JWT 架构不符）；el-form validate 异步校验（放弃：观测到 pending 缺陷）。
- 影响: XSS 可读取 token 为已知接受风险（无富文本入口缓解）；P2-21 前端测试与 P2-23 全链路验证以本闭环为基线。
