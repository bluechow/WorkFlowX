# WorkFlowX OWASP Top 10 对照说明（Phase 15）

> 基准: OWASP Top 10 (2021)。映射范围限本阶段实际执行的测试与验证；"Not Applicable" 表示当前架构无对应攻击面或场景，不构成"绝对安全"声明。

## A01 Broken Access Control（失效的访问控制）

**状态: 已测试覆盖，未发现缺陷**

- 验证: IDOR/BOLA 全矩阵（tests/api/test_idor_security.py）——cross-user/cross-org/cross-project 三层，覆盖 GET/PUT/PATCH/DELETE 与 comment/attachment/notification/audit 资源
- 关键实证: notification self 资源对 ADMIN 也 404（无特权后门）；org 删除仅 OWNER；issue_id 替换拒绝
- 防回归: security marker 用例 + Phase 12 既有数据级用例

## A02 Cryptographic Failures（加密失败）

**状态: 已测试覆盖，未发现缺陷**

- 密码: BCrypt 哈希存储（登录响应/me 响应无 password/passwordHash 字段——test_sensitive_data 断言）
- JWT: HS256 服务端密钥（dev-only 默认密钥仅本地环境；生产强制 JWT_SECRET 环境变量，application-prod.yml fail-fast）
- 传输: 当前仅本地 HTTP（学习项目边界）；生产部署应叠加 TLS（属 Phase 16 部署范畴，记录为已知限制）

## A03 Injection（注入）

**状态: 已测试覆盖，未发现缺陷**

- SQL 注入: 探测 payload 全部作为字面文本处理（MyBatis-Plus 参数化），test_input_security 断言原样存储+返回、无 SQL/异常泄露
- 状态: 无字符串拼接 SQL；全参数绑定

## A04 Insecure Design（不安全设计）

**状态: 设计层有意识防护，未发现缺陷**

- 状态机: 服务端矩阵强制，直接 PATCH CLOSED→OPEN 等全部 409（ADR-017）
- 单会话: auth:session 覆盖式设计（ADR-008）
- ownership 三层: authority + 数据级 + 作者本人（ADR-018），ADMIN 不豁免

## A05 Security Misconfiguration（安全配置错误）

**状态: 已审查并加固**

- actuator 仅暴露 health（show-details: never）；env/beans/mappings 401/404
- Swagger/dev 仅 dev profile；prod 默认关闭
- **Phase 15 加固**: SecurityConfig 增加安全响应头（X-Content-Type-Options: nosniff / X-Frame-Options: DENY / Cache-Control）；MissingServletRequestPartException 500→422（ADR-021）
- CORS: 未配置=默认同源（浏览器层面无跨域放行）

## A06 Vulnerable and Outdated Components（易受攻击组件）

**状态: Not Run（工具未安装，未阻塞 Gate）**

- `npm audit` / `mvn dependency-check` / `trivy` 未安装或未运行
- 依赖均为主流 maintained 版本（Spring Boot 3.5.16 / Vue 3.5 / EP 2.x / JMeter 5.6.3）
- 后续可在 CI（Phase 17）引入依赖扫描

## A07 Identification and Authentication Failures（身份认证失败）

**状态: 已测试覆盖，未发现缺陷**

- 失败锁定: 5 次/15 分钟（ADR-010），锁定后正确密码仍拒绝，成功登录清零——阶梯测试验证
- 防枚举: 存在用户/不存在用户统一 401 契约（message 一致断言）
- 会话: 单会话覆盖、登出/禁用即时失效、旧 token 不复活
- JWT: roles/userId/exp 篡改 + alg=none + 伪造签名全部 401

## A08 Software and Data Integrity Failures（软件与数据完整性失败）

**状态: 已测试覆盖（JWT 签名完整性部分）**

- JWT 签名篡改拒绝；payload 篡改（roles/sub/exp）拒绝
- 附件 objectKey 服务端生成（UUID 前缀），客户端不可控——完整性由服务端保证

## A09 Security Logging and Monitoring Failures（日志与监控不足）

**状态: 已测试覆盖**

- 关键安全事件审计: LOGIN/LOGIN_FAIL/LOGOUT/USER CREATE+STATUS/ROLE 变更/ORG DELETE/WORKFLOW TRANSITION/COMMENT/ATTACHMENT（16 接线点，Phase 10）
- 敏感信息: 审计与响应无密码/secret/JWT（负向断言）；traceId 贯穿可追踪
- 局限: 无实时告警/监控仪表盘（明确不在学习项目范围）

## A10 SSRF（服务端请求伪造）

**状态: Not Applicable / Not Present**

- 当前无任何服务端根据用户输入发起出站请求的功能（无 URL 预览/回调/webhook）
- 附件为客户端直传 MinIO（预签名/凭证不暴露），无服务端拉取远端 URL 的代码路径
