# 测试数据与测试环境说明

> 任务 P2-22 ｜ 面向: 所有为 WorkFlowX 编写/运行测试的人（含未来的你）
> 原则: 测试数据"创建 → 使用 → 验证 → 清理"生命周期可追溯；生产凭据永远不出现在本文档。

---

## 1. 环境依赖（所有集成/自动化测试的前提）

| 依赖 | 地址（默认） | 启动方式 |
|---|---|---|
| 后端 API | http://localhost:8080 | `mvn -f backend/pom.xml spring-boot:run` |
| MySQL 8 | localhost:3307（容器内 3306） | 仓库根 `docker compose up -d` |
| Redis 7 | localhost:6379 | 同上 |
| MinIO | localhost:9000/9001 | 同上（用户 API 测试暂不依赖） |
| 前端 dev server | http://localhost:5173 | `cd frontend && npm run dev`（前端测试不需要） |

数据库连接参数通过环境变量 `WORKFLOWX_DB_*` 覆盖（见 `tests/api/conftest.py`），默认与 `.env.example` 一致。

---

## 2. Seed 数据（dev 环境，长期存在，禁止删除）

| 账号 | 角色 | 用途 |
|---|---|---|
| admin | ADMIN | 管理端测试主账号（用户 CRUD/禁用操作的执行者） |
| user1 | MEMBER | 普通用户，用于权限反例（403）与被禁用目标 |

- 创建者: dev 种子迁移 `db/seed/dev/V2__seed_dev.sql`（**仅 dev Flyway location，生产不执行**）
- 密码: BCrypt 哈希存库；明文凭据**只**记录在 `docs/development/getting-started.md`（仅 DEV/测试用途）
- 禁止: 删除/重命名这两个账号、修改其角色、将其密码写入其他任何文件

### Seed 账号的伴生 Redis 键（会自动过期，可安全清理）

| 键 | TTL | 说明 |
|---|---|---|
| `auth:session:{admin的id=1}` | 2h | 每次 admin 登录覆盖写入 |
| `auth:fail:admin` | 15min | admin 登录失败计数（5 次触发锁定） |
| `auth:session:2` / `auth:fail:user1` | 同上 | user1 的对应键 |

---

## 3. Python API 自动化测试（tests/api/，P2-20）

**运行**: `pytest`（需后端 + 基础设施运行中）；清理 fixture 会话结束自动执行。

### 数据策略

- 前缀: 所有测试用户 `api_test_` + 随机 uuid 后缀（`unique_suffix` 函数级 fixture，避免同批冲突与跨运行冲突）
- 创建方式: **真实 ADMIN API**（`POST /api/v1/users`，经 admin_token fixture），不直连数据库造数
- 清理（`cleanup_at_session_end`，autouse，会话结束自动执行）:
  1. DB: `DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'api_test_%')` → `DELETE FROM users WHERE username LIKE 'api_test_%'`（外键级联兜底）
  2. Redis: 删除已跟踪用户的 `auth:session:{id}`
  3. Redis: SCAN 删除 `auth:fail:api_test_*`
  4. 兜底: 删除 `auth:fail:admin` / `auth:fail:user1`（清除测试过程中可能对 seed 账号累积的失败计数，防止跨运行锁定）

### 已知残留与处理

- Redis 键有 TTL（会话 2h / 失败计数 15min），即使清理 fixture 未运行也会自动过期
- **测试失败中断时**: 残留用户仅存在于 dev 库，带 `api_test_` 前缀；可手工清理:

```sql
USE workflowx;
DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'api_test_%');
DELETE FROM users WHERE username LIKE 'api_test_%';
```

```bash
# Redis 侧（WSL 内 docker）
docker exec workflowx-redis redis-cli --scan --pattern "auth:session:*"   # 核对
docker exec workflowx-redis redis-cli --scan --pattern "auth:fail:api_test_*"
```

- **不可删除**: `admin`、`user1`、`flyway_schema_history`、V1/V2 迁移产物

---

## 4. Java 集成测试数据（backend/src/test/，P2-15 及之前）

| 前缀 | 所在测试 | 说明 |
|---|---|---|
| `p2_mapper_test_` | UserMapperTest | 分页/查询测试用户 |
| `p2_service_test_` | UserServiceIntegrationTest | Service 层测试用户 |
| `p2_auth_test_` | AuthLoginIntegrationTest / AuthLogoutMeIntegrationTest | 登录/登出/me 测试用户 |
| `p2_ctrl_test_` | UserControllerIntegrationTest | Controller 权限矩阵测试用户 |
| `p2_lock_test_` | AuthLockoutIntegrationTest | 锁定测试用户 |
| （无前缀） | SeedDataIntegrationTest / SeedPasswordHashTest | 只读校验 dev seed（admin/user1） |

- 清理机制: 各测试类 `@AfterEach` 按 `likeRight(username, 前缀)` 删除（user_roles 外键级联）+ 删除对应 `auth:fail:*` / `auth:session:*` 键
- 事务说明: **未使用 @Transactional 回滚**——测试使用真实 MySQL 且部分断言依赖 Redis 状态，显式清理更直观且与生产语义一致
- 依赖: 测试上下文为完整 Spring Boot（连接 dev 的 MySQL/Redis）；**运行 Java 测试必须先启动基础设施**
- Redis 键 TTL 提示: `auth:fail:*` 15 分钟内跨运行残留会影响计数类断言——测试类均已显式清理

---

## 5. 前端测试数据（frontend/src/**/__tests__/，P2-21）

- **纯 mock，不依赖真实后端**：`@/api/auth` 整体 vi.mock（login/logout/fetchMe）
- mock 用户: `alice`（UserVO/LoninResponse 结构样例，非真实账号）
- mock 错误: `{ code: 401/403/429/500, message: '...' }`（对齐后端安全消息）
- 隔离: 每用例 `localStorage.clear()` + `vi.clearAllMocks()` + 全新 Pinia；LoginView/Dashboard 用 vue-router mock（push spy）
- 明确: 前端 mock 数据**不是**生产/开发 seed，与后端数据零关联

---

## 6. 锁定机制对测试的影响（重要）

登录失败限制（ADR-010）: 同一 username 15 分钟窗口内 5 次失败 → 锁定 15 分钟。

- 编写测试时: 对同一账号的**错误密码**尝试不得超过 4 次，或使用独立用户名
- 清理时: **必须**删除 `auth:fail:{username}` 键，否则跨运行累积导致后续测试被 429
- 锁定中的账号: 正确密码也会 429，与用户是否存在无关（统一计数语义）
- 人工解锁: `docker exec workflowx-redis redis-cli DEL "auth:fail:{username}"`

---

## 7. 生产安全边界

- 本文档所有凭据仅适用于 **dev/测试环境**
- 生产环境: 无默认账号（dev 种子迁移不部署）；所有密钥经环境变量注入（JWT_SECRET/数据库密码等）
- 测试数据前缀（api_test_/p2_*_test_）在任何环境都不应存在生产数据中
