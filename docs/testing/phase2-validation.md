# Phase 2 全链路验证报告（P2-23）

> 验证时间: 2026-09-06 ｜ 验证人: AI Engineering Agent ｜ 结论: **PASS**
> 上游: P2-01～P2-22 已完成并经外部（ChatGPT）源码审查确认（审查包 WorkFlowX-source.zip，256 文件）

## 1. 验证环境

| 项 | 值 |
|---|---|
| 后端 | Spring Boot 3.5.16 jar（含 P2-15 异常处理器），localhost:8080 |
| 基础设施 | Docker Compose: MySQL 8.0.46 (:3307) / Redis 7 (:6379) / MinIO (:9000) |
| 前端 | Vite dev server :5173（P2-16~19 认证闭环代码，HMR 加载） |
| 宿主 | Windows + WSL2 Ubuntu（NAT 模式 + netsh portproxy 转发，见第 6 节环境问题） |

## 2. 验证命令

- **自动化三线回归**: `mvn test`（backend）｜ `pytest`（tests/api，真实 HTTP）｜ `npm run test / lint / build`（frontend）
- **E2E 全链脚本**: `p2_23_e2e.sh`（42 项检查，curl + Redis 实查 + SQL 清理），连续执行 **2 轮**（幂等性验证）
- **浏览器 E2E**: P2-16~19 批次已完成 14 步真实浏览器验证（本批复核其结论仍有效）

## 3. 自动化测试结果（三线）

| 线 | 结果 |
|---|---|
| Maven | **144/144 passed** |
| Pytest | **28/28 passed**（连接真实后端 localhost:8080，非 mock） |
| Frontend Vitest | **45/45 passed** |
| Frontend lint / build | PASS / PASS |

## 4. E2E 全链验证结果（42 项检查，2 轮全部通过）

### 认证
| 验证点 | 结果 |
|---|---|
| 登录成功（结构/roles/traceId/无密码字段） | ✅ 200 |
| 错误密码 / 不存在用户 → 401 统一消息（防枚举） | ✅ |
| 登录失败 5 次 → 第 5 次 **429** | ✅ |
| 锁定期间正确密码仍 **429**、不创建会话 | ✅ |
| 锁定解除（DEL 计数键）→ 恢复登录 | ✅ 200 |
| JWT 非法 Token → 401 | ✅ |
| 第二次登录覆盖 → 旧 Token 401 / 新 Token 200 | ✅ |
| Redis Session 删除 → Token 401 | ✅ |
| Logout（P2-09 用例 + Java 集成测试）→ Token 立即失效 | ✅ |

### 用户（ADMIN）
| 验证点 | 结果 |
|---|---|
| 分页查询 / keyword / 按 ID 查询 | ✅ 200 |
| 创建（201、无密码字段、库中真实存在） | ✅ |
| 更新（email/nickname） | ✅ 200 |
| 重复 username / email → **409** | ✅ |
| 非法参数 → **422**；不存在 → **404** | ✅ |
| 禁用 → 200 + Redis 会话删除 + 旧 Token 401（踢线） | ✅ |
| 重新启用 → 200、不自动恢复会话、重新登录获新会话 | ✅ |
| ADMIN 自禁用 → **400** 保护 | ✅ |

### 权限
| 验证点 | 结果 |
|---|---|
| 未认证 → 401 | ✅ |
| MEMBER → 管理 API 403（浏览器 E2E 亦实证 403 后不误登出） | ✅ |
| ADMIN → 正常访问 | ✅ |

### 错误码与响应质量
| 码 | 实测场景 | traceId | 消息安全 |
|---|---|---|---|
| 400 | 路径参数类型错误 | ✅ | ✅ |
| 401 | 未认证/凭证错误 | ✅ | ✅ 防枚举 |
| 403 | 非_ADMIN/禁用 | ✅ | ✅ |
| 404 | 资源不存在 | ✅ | ✅ |
| 409 | username/email 冲突 | ✅ | ✅ |
| 422 | 参数校验失败 | ✅ | ✅ 无堆栈泄漏 |
| 429 | 登录锁定 | ✅ | ✅ |
| 500 | Redis 停机（fail-closed 实测） | ✅ | ✅ 仅 internal server error |

## 5. 发现的问题与处理

| # | 类型 | 描述 | 处理 |
|---|---|---|---|
| 1 | 环境问题 | WSL2 `docker compose stop/start` 后 localhost 端口转发（wslrelay）不稳定，后端启动连不上 MySQL；尝试 mirrored 网络模式不可行（不转发 Docker iptables 端口） | **修复**: Hyper-V 防火墙放行 WSL VM 入站（`Set-NetFirewallHyperVVMSetting ... -DefaultInboundAction Allow`）+ `netsh portproxy` 建立稳定的 127.0.0.1:3307/6379 → WSL IP 转发；已验证并记录 |
| 2 | 环境问题 | WSL 虚拟机在工具调用间隙被空闲回收（MySQL 收到 SHUTDOWN），导致 relay 中断 | `.wslconfig` vmIdleTimeout=86400000 已配置 + keep-alive 进程（历史方案） |
| 3 | 测试脚本问题 | 首版 E2E 脚本非幂等（固定用户名撞上上次残留）+ bash 引号嵌套笔误 | 修正为时间戳后缀 + 前置清理步骤，重跑 2 轮全绿 |
| 4 | 产品 Bug | **未发现**——E2E 42 项 + 自动化 217 项无一行需要修改生产代码 | — |

## 6. 最终回归

- Maven: **144/144** ｜ Pytest: **28/28** ｜ Vitest: **45/45** ｜ lint/build: PASS
- 测试数据清理: p2_23_ 前缀用户与 Redis 键清零（DB users 回到 seed 的 2 行）
- 结论: **P2-01～P2-22 组合后的完整认证/用户链路验证通过，Phase 2 达到 Definition of Done**
