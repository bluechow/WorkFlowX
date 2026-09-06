# WorkFlowX 安全架构（Phase 2 认证与用户）

> 任务 P2-24 ｜ 上位规则: Master Prompt §6/§7、ADR-003/008/009/010、ADR-011（前端）

## 1. 认证链路总览

```text
Login 请求 (POST /api/v1/auth/login, permitAll)
    ↓
Bean Validation（username/password 非空）
    ↓
LoginAttemptService.isLocked —— Redis auth:fail:{username} ≥5 → 429（锁定检查最前）
    ↓
UserMapper 查询用户 —— 不存在/密码错误 → 401「用户名或密码错误」（统一防枚举）
    ↓
PasswordService.matches —— BCrypt(10) 校验
    ↓
状态检查 —— DISABLED/LOCKED → 403（密码验证通过后才检查，防止状态差异探测账号存在性）
    ↓
JwtService.issueToken —— HS256，claims = sub(userId)/username/roles/jti/iat/exp，TTL 2h
    ↓
AuthSessionService.createSession —— Redis auth:session:{userId} = jti，TTL 2h（单会话，原子 SET+TTL）
    ↓
更新 users.last_login_at
    ↓
LoginResponse（accessToken/tokenType/expiresIn/userId/username/roles，无任何密码字段）
```

## 2. 请求认证链路

```text
任意受保护请求 + Authorization: Bearer <token>
    ↓
JwtAuthenticationFilter（SecurityFilterChain 内，非独立 Bean）
    ├─ JwtService.parseToken —— 签名/过期/issuer/必要 claims 校验（HS256 服务端固定，alg=none 天然拒绝）
    ├─ AuthSessionService.isCurrentSession —— Redis auth:session:{userId} == jti
    │   ├─ 不一致/不存在 → 不建立认证（后端登出、二次登录覆盖、禁用踢线均由此生效）
    │   └─ Redis 异常 → fail-closed（不建立认证 + 服务端 WARN 日志，不放行）
    └─ SecurityContext: UsernamePasswordAuthenticationToken(principal=JwtPayload, authorities=ROLE_{code})
    ↓
@PreAuthorize("hasRole('ADMIN')") 等方法级授权（@EnableMethodSecurity）
    ↓
AuthorizationDeniedException → 重抛 → RestAccessDeniedHandler → 403 统一 JSON
未认证 → RestAuthenticationEntryPoint → 401 统一 JSON
```

## 3. 会话模型

| 项 | 规则 |
|---|---|
| Key | `auth:session:{userId}` |
| Value | 当前有效 JWT 的 jti |
| TTL | 2 小时（与 JWT exp 同源，JwtProperties.expireHours） |
| 会话数 | 每用户 **1 个**（单会话） |
| 二次登录 | 覆盖写入，旧 jti 失效 → 旧 Token 立即 401 |
| 登出 | DEL 会话键 → 原 Token 立即 401（幂等，键不存在静默成功） |
| 禁用踢线 | 状态置 DISABLED 的同事务内 DEL 会话键 |
| 无 Refresh Token | 单会话白名单已覆盖登出/过期/覆盖/踢线（ADR-010 决策） |

## 4. 登录失败限制（ADR-010）

| 项 | 规则 |
|---|---|
| Key | `auth:fail:{username}`（INCR 原子计数，首失败设 TTL 900s） |
| 锁定条件 | 15 分钟窗口内累计 **5 次**失败 |
| 锁定时长 | TTL 重置为 900s（15 分钟） |
| 锁定期间 | 正确密码同样 429，不查库、不验密码、不建会话 |
| 解除 | TTL 自然过期，或成功登录 DEL 清零后重新计数 |
| 语义 | 计数对不存在的 username 同样生效（统一响应防枚举） |

## 5. 边界与风险

| 项 | 决策 |
|---|---|
| Token 存储（前端） | localStorage `workflowx_access_token`——非高安全等级，当前阶段接受（无富文本 XSS 入口）；刷新保持登录态必需（ADR-011） |
| users.status=LOCKED | 仅由 ADMIN 手动设置；登录失败锁定是 Redis 临时态，**不写数据库** |
| 禁用后 /me | 会话有效期内 currentUser 来自数据库——禁用用户的会话已被删除，正常路径无法再访问；未删除的旧 JWT 因会话校验失败 401 |
| 密码规则 | ≥8 位且含字母和数字（Bean Validation）；BCrypt strength 10 存储；VO/日志/前端全程无密码形态字段 |
| Secret 管理 | `JWT_SECRET` 环境变量；dev 默认值仅本地（.env.example 标注 dev-only）；prod 缺失即启动失败 |
| 已知接受风险 | 登录接口无验证码（Phase 3+ 视需要加）；锁定可被第三方故意触发（限流类方案标准取舍） |

## 6. 生产/开发边界

- `application-prod.yml`: JWT_SECRET / MYSQL_PASSWORD 等缺失即启动失败（`:?` 语法），无任何默认凭据
- dev 种子迁移（db/seed/dev）不配置在 prod Flyway location——生产不存在 admin/user1 默认账号
- dev 默认 JWT secret 仅用于本地开发，已标注 dev-only
