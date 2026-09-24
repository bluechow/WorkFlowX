# Phase 15 Release Gate 记录 — Security Testing

> 时间: 2026-09-24 ｜ 基线: 294eea0（Phase 14 Gate）→ 本 Gate 提交｜ 结论: **GATE PASS**

## 1. Scope

P15-01 安全审计（Security/Swagger/Actuator/JWT/前端存储/上传/清理全栈）→ 安全头加固（产品）→ pytest security suite 47 用例 → Playwright security specs 7 用例 → secrets scan → 依赖扫描（NOT RUN）→ OWASP Top 10 mapping → 两轮回归 → 全量回归 → 终态清理 → 本 Gate。

## 2. Environment

开发笔记本（8C16T/16GB）；全部测试走真实 HTTP/UI 链路；SECURITY_/AA/api_test_ 命名空间隔离。

## 3. Authentication Security（P15-01）

| 验证 | 结果 |
|---|---|
| 防用户枚举（存在 vs 不存在用户） | 统一 401 + message 一致 ✅ |
| malformed JSON / 缺字段 / null / 空串 / 超长密码 | 400/401/422 ✅ |
| mass assignment（roles/userId/admin 注入登录） | 忽略，不提权 ✅ |
| 失败计数阶梯（1→4 次 401，第 5 次 429） | ✅ |
| 锁定后正确密码仍拒绝 | ✅ |
| 成功登录清除计数 | ✅ |

## 4. Session Security（P15-02）

二次登录覆盖旧 token（旧 401/新 200）✅｜logout 幂等+token 立即失效 ✅｜禁用踢线、恢复不复活 ✅｜7 种 malformed bearer 全 401 ✅

## 5. JWT Security（P15-03）

roles→ADMIN 篡改（保原签名）401 ✅｜sub 冒充 admin 401 ✅｜exp 续期 401 ✅｜alg=none/伪造签名/两段结构 401 ✅——**JWT 签名校验 + 服务端会话比对双层防护有效**。

## 6. RBAC Security（P15-04）

member 直访管理 API（users list/audit/org 写）→ 403 ✅；前端菜单隐藏与 API 403 双层一致 ✅（Playwright）。

## 7. IDOR / BOLA（P15-05）

评论 ownership：ADMIN 非 owner 编辑/删除 → 403（ADR-018 不豁免）✅｜通知 self 隔离：ADMIN 操作 MEMBER 通知 → 404 ✅｜跨项目 issueId 替换 → 403/404 ✅。

## 8-9. Cross-Org / Cross-Project Isolation（P15-06/07）

ORG_A 成员：ORG_B 项目读/写 403、加 ORG_B 项目成员 403、ORG_B Issue 读/流转 403、项目列表不含非成员项目 ✅。

## 10. Input Security（P15-08/10/11/19）

SQLi 探测（7 payload × keyword/title）字面存储+精确过滤 ✅｜fuzz 矩阵（null/enum/负数/数组/字符串 body）✅｜分页钳制 size≤100 ✅｜workflow 篡改（OPEN→CLOSED/OPEN→TESTING/CLOSED→OPEN）全 409 ✅｜mass assignment（PUT users 注入 roles/status/ownerId/createdAt）忽略 ✅。

## 11. File Security（P15-12/13/14）

路径穿越 6 变体（../ ..\\ %2e%2e%2f ..%252f test.txt/../x）剥离或拒绝 ✅｜双扩展 test.exe.txt 白名单放行（元数据无路径逃逸）、test.jpg.exe 422 ✅｜MIME 伪装不绕过 ✅｜空 filename part 422（**产品修复**，见 §13）｜objectKey 服务端生成不受用户控制 ✅｜下载授权（认证+成员+scope）✅。

## 12. Sensitive Data（P15-15）

login/me/错误响应/Swagger/actuator 响应无 password/passwordHash/secret/JWT/堆栈/SQL/内部路径 ✅（负向断言自动扫描）。

## 13. Audit Security（P15-16）

登录失败审计**不含密码** ✅｜USER CREATE 审计 target 与真实用户一致 ✅｜16 接线点覆盖关键安全事件 ✅。

## 14. HTTP Configuration（P15-17）

actuator 仅 health（show-details never）、env/beans/mappings/configprops 401/404 ✅｜Swagger 公开（dev 设计）但不绕过认证 ✅｜CORS 未配置=同源 ✅｜OPTIONS 不绕过认证 ✅。

## 15. **Phase 15 产品加固 + 真实产品缺陷修复**

1. **[加固] API 安全响应头**：SecurityConfig 增加 X-Content-Type-Options: nosniff / X-Frame-Options: DENY / Cache-Control——断言于 test_security_headers_present
2. **[产品缺陷→修复] 空 filename multipart → 500**：`MissingServletRequestPartException` 未被处理（Spring 将空 filename part 视为缺失）。修复：GlobalExceptionHandler 增加映射 → 422。安全意义：500 反映内部异常路径暴露面，422 为正确客户端错误语义
3. **[加固记录]** Referrer-Policy/CSP 未加：API JSON 响应无浏览上下文，收益为零——记录原因不加

## 16. Secrets Scan（P15-22）

`grep` 全仓扫描（yml/properties/env/docs/tests）：**PASS**——
- prod profile 全部 `:?required` fail-fast（JWT_SECRET/MINIO/DB 凭据不落地）
- dev-only 明文（admin 密码/HikariCP 密码）限定于 getting-started.md 与本地 perf.properties（与 ADR-007 一致，属测试环境凭据）
- `.env` 已 gitignore（实测 check-ignore 通过）

## 17. Dependency Scan（P15-23）

**NOT RUN / Not Available**：npm audit 镜像端点 NOT_IMPLEMENTED；mvn dependency-check 插件与 trivy 未安装。按边界要求记录不阻塞 Gate；建议 Phase 17 CI 引入。

## 18. 两轮结果（一致）

| 轮 | pytest security（5 文件） | Playwright security（7） | 全量回归 |
|---|---|---|---|
| Round 1 | 46 passed, 1 skip | 7 passed | mvn 311 / pytest 115 / Vitest 136 / lint+build PASS |
| Round 2 | 46 passed, 1 skip | 24 passed（full 20+security 7 子集重跑） | mvn 311 / pytest 115 / Vitest 136 / lint+build PASS |

## 19. Full Regression（P15-28，两轮）

Playwright **24/24** ×2（含 security 7）｜pytest **115/115** ×2｜Vitest **136/136** ×2｜mvn **311/311** ×2｜lint/build PASS ×2。

## 20. OWASP Top 10 Mapping（P15-24）

详见 docs/security/owasp-top10-mapping.md：A01/A02/A03/A04/A05/A07/A08/A09 已测试覆盖且未发现缺陷；A06 Not Run（工具缺）；A10 Not Applicable（无服务端出站请求面）。

## 21. Product Defects

1 个（已修复）：**空 filename multipart → 500**（MissingServletRequestPartException 未映射）。修复：GlobalExceptionHandler → 422。

## 22. Test Defects（已修，未改产品）

security suite 初版：未认证 api_client 造数 401、project key 超长 422、att_env 缺 api_client、_headers 未定义、comment_a 类型断言、URL 编码变体断言过严（字面 `..%252f` 仅为元数据不构成遍历面）。

## 23. Environment Issues

无（Playwright chromium 经镜像安装一次成功；后端重启窗口期一轮连接失败已重启重跑）。

## 24. Known Limitations

- HTTP 明文（学习项目边界；生产叠加 TLS 属 Phase 16）
- JWT dev-only 默认密钥（prod fail-fast 已就位）
- 无实时告警/APM；依赖扫描工具未装（Not Run 记录）
- Playwright XSS 用例未含下载事件断言（由 API 层逐字节断言兜底）

## 25. Commit / HEAD / working tree

本 Gate 提交为最终 commit；HEAD 见 git log；**working tree clean**。

## 26. Gate Decision

# Phase 15 PASS ✅
