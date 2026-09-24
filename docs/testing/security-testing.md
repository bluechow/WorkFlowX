# WorkFlowX 安全测试体系（Phase 15）

## 1. Scope（测试范围）

本体系覆盖 WorkFlowX 全栈（Vue 前端 / Spring Boot 后端 / MySQL·Redis·MinIO）在**本地开发环境**下的安全验证：
认证安全、会话安全、JWT 篡改、RBAC 授权、IDOR/BOLA、跨组织/跨项目隔离、输入校验（SQL 注入探测/XSS/边界 fuzz）、文件上传安全、敏感信息泄露、审计完整性、HTTP 配置、Secrets 扫描、OWASP Top 10 对照。

**不包含**：生产环境渗透测试、大规模爆破/DoS、真实恶意软件、外部目标扫描（Phase 15 安全边界，见 phase15-gate.md）。

## 2. Environment

开发笔记本（8C16T/16GB，Windows + WSL2 Docker）。全部测试走真实 HTTP/API/UI 链路；数据使用 `SECURITY_`/`AA`/`api_test_` 命名空间隔离并在 teardown 清理。

## 3. Threat Model（按资产）

| 资产 | 主要威胁 | 防护层 | 验证 |
|---|---|---|---|
| 用户凭证 | 枚举/爆破/泄露 | 统一 401、失败锁定（ADR-010）、BCrypt | test_auth_security |
| 会话 | 会话固定/未失效 | 单会话覆盖、登出/禁用即失效 | test_session_jwt_security |
| JWT | 篡改提权/alg=none | 服务端 HS256 签名 + 会话比对 | test_session_jwt_security |
| 业务数据 | IDOR/BOLA | 三层：authority + 数据级 + ownership | test_idor_security |
| 输入 | SQLi/XSS/超长/enum 篡改 | 参数化查询 + 纯文本渲染 + Bean Validation | test_input_security |
| 文件 | 路径穿越/恶意扩展/MIME 伪装 | 扩展白名单 + objectKey 服务端生成 + 清洗 | test_sensitive_file_config_security |
| 敏感信息 | 响应/日志泄露 | 契约脱敏（NON_NULL）+ 审计脱敏 | test_sensitive_file_config_security |
| 配置 | actuator/Swagger 暴露 | 仅 health 暴露；Swagger 不绕过认证 | test_sensitive_file_config_security |

## 4. 组件与验证矩阵

### pytest security suite（tests/api/，marker `security`，47 用例）

| 文件 | 用例 | 覆盖 |
|---|---|---|
| test_auth_security.py | 7 | 防枚举 401 契约、malformed/缺字段/null/超长、失败计数 1→5 阶梯、锁定后正确密码拒绝、成功清零、mass assignment |
| test_session_jwt_security.py | 12 | 双登录覆盖、登出幂等、禁用踢线/恢复不复活、7 种 malformed bearer、JWT roles/sub/exp 篡改、alg=none、伪造签名 |
| test_idor_security.py | 8 | 跨组织读写 403、跨项目 403/404、issueId 替换、评论 ownership（ADMIN 不豁免）、通知 self 隔离 |
| test_input_security.py | 5 | SQLi 字面存储、fuzz 矩阵（null/enum/负数/数组/字符串 body）、分页钳制、workflow 篡改 409、mass assignment 忽略 |
| test_sensitive_file_config_security.py | 9 | 响应敏感词、路径穿越变体、双扩展/MIME 伪装、objectKey 服务端生成、actuator、Swagger 不绕过、审计无密钥、安全头、安全事件审计 |

### Playwright security specs（frontend/e2e/specs/security.spec.ts，7 用例）

XSS 纯文本渲染（script/img 不注入）、ADMIN 刷新菜单保持（P13 缺陷防回归）、user1 直访审计/用户管理权限提示、单会话覆盖后旧 context 401 自动跳转、localStorage 无密码。

### 跨框架兜底清理

pytest conftest 回收 AA% 组织与 api_test_ 用户（含 Playwright 残留）；org 删除失败保留 owner（可重试）。

## 5. 已知限制

- 本地学习项目：HTTP 明文（生产需 TLS，属 Phase 16 部署范畴）
- JWT dev-only 默认密钥（生产强制 JWT_SECRET 环境变量，prod fail-fast）
- 无 APM/实时告警；依赖扫描工具未安装（见 phase15-gate.md §Dependency Scan: NOT RUN）
- 本文档不构成"系统绝对安全"声明——仅陈述本阶段测试范围内未发现缺陷

## 6. 运行方式

```
cd tests/api
pytest -m security              # 安全专项（47 用例）
pytest                          # 全量（security 含在内）
cd frontend
npx playwright test e2e/specs/security.spec.ts   # 前端安全 7 用例
```
