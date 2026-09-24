# WorkFlowX

企业级项目协作与工单管理平台 —— 面向中小型团队的项目管理、Issue 管理、团队协作、权限控制、通知、审计和数据统计。

> 本项目同时是一个测试学习平台：每一个业务功能都考虑如何测试、如何自动化、如何回归（详见 [AI_MASTER_PROMPT.md](AI_MASTER_PROMPT.md) §40）。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 21 · Spring Boot 3.5.x · Spring Security · MyBatis-Plus · Flyway · JWT |
| 前端 | Vue 3 · TypeScript · Vite · Pinia · Vue Router · Element Plus · ECharts |
| 数据层 | MySQL 8.x · Redis · MinIO |
| 测试 | JUnit 5 · Pytest+httpx · Vitest · Playwright · JMeter |
| 部署 | Docker Compose（backend / frontend / MySQL / Redis / MinIO 全栈） |
| CI | GitHub Actions（lint / test / e2e / docker build / secrets scan） |
| 架构 | Modular Monolith（[ADR-001](AI_DECISIONS.md)） |

## 功能

认证（JWT + 单会话 + 登录失败锁定）· 用户管理 · RBAC（角色/权限）· 组织与部门 · 项目与成员 · Issue（编号/分派/状态机）· 评论 · 附件（MinIO）· 站内通知 · 审计日志 · 数据仪表盘。

## 架构

```text
Browser
    ↓
Nginx (frontend:80, SPA + /api 反代)
    ↓
Spring Boot (backend:8080)
    ↓
┌──────────────┬──────────────┬──────────────┐
MySQL (:3307*)  Redis (:6379)  MinIO (:9000)
     └──── Docker Compose（named volumes 持久化）────┘
* 开发编排映射 3307；部署编排内网 3306 不对外暴露
```

## 快速启动（开发）

```bash
# 0. 环境要求: JDK 21 / Node 20+ / Python 3.11+ / WSL2 + Docker
#    详细步骤见 docs/development/getting-started.md

# 1. 配置本地环境变量（首次）
cp .env.example .env

# 2. 启动基础设施（MySQL / Redis / MinIO）
docker compose up -d

# 3. 启动后端（默认 dev profile，端口 8080；Flyway 自动建表+seed）
cd backend && mvn spring-boot:run

# 4. 启动前端（端口 5173，/api 代理到 8080）
cd frontend && npm ci && npm run dev
```

验证：

- 打开 http://localhost:5173 → 登录页；默认账号 admin / Admin@123456（dev seed）
- `curl http://localhost:8080/api/v1/health` → 统一响应结构
- API 文档（dev）：http://localhost:8080/swagger-ui

## Docker 部署

```bash
cp deploy/.env.example deploy/.env   # 填入真实密码/JWT_SECRET
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d
# 访问 http://localhost:${FRONTEND_PORT:-80}；详细见 docs/deployment.md
```

## 测试

| 层 | 命令 | 数量 |
|---|---|---|
| 后端集成 | `cd backend && mvn test` | 311 |
| 前端单元 | `cd frontend && npm run test -- --run` | 136 |
| API 自动化 | `cd tests/api && pytest`（需后端运行中） | 115 |
| UI 自动化 | `cd frontend && npx playwright test` | 25 |
| 性能 | `tests/performance`（JMeter，手动） | 4 级负载 |
| 安全 | `pytest -m security` + secrets scan | 见 docs/testing/security-testing.md |

详细分层与数据清理策略见 [docs/testing/how-to-run-tests.md](docs/testing/how-to-run-tests.md)。

## CI/CD

GitHub Actions（`.github/workflows/ci.yml`）：push/PR → frontend（lint/Vitest/build）→ backend（Maven+MySQL）→ API/UI E2E（全栈）→ Docker build → secrets scan。详见 [docs/ci-cd.md](docs/ci-cd.md)。

## 文档

| 文档 | 内容 |
|---|---|
| [docs/development/getting-started.md](docs/development/getting-started.md) | 开发环境搭建（Windows / WSL 细节） |
| [docs/api/api-conventions.md](docs/api/api-conventions.md) | API 设计约定 |
| [docs/database/data-dictionary.md](docs/database/data-dictionary.md) | 数据字典（含 §14 决策标注） |
| [docs/architecture/security.md](docs/architecture/security.md) · [rbac.md](docs/architecture/rbac.md) · [workflow.md](docs/architecture/workflow.md) · [notification.md](docs/architecture/notification.md) · [audit.md](docs/architecture/audit.md) · [dashboard.md](docs/architecture/dashboard.md) | 模块架构 |
| [docs/deployment.md](docs/deployment.md) · [docs/docker-architecture.md](docs/docker-architecture.md) | 部署与 Docker 架构 |
| [docs/testing/security-testing.md](docs/testing/security-testing.md) · [performance-testing.md](docs/testing/performance-testing.md) · [api-automation.md](docs/testing/api-automation.md) · [ui-automation.md](docs/testing/ui-automation.md) | 测试体系 |
| [docs/ci-cd.md](docs/ci-cd.md) | CI/CD 体系 |
| [AI_MASTER_PROMPT.md](AI_MASTER_PROMPT.md) | 项目永久规则（AI 协作约束） |
| [AI_DECISIONS.md](AI_DECISIONS.md) | 技术决策记录（ADR-001~021） |

## 项目阶段

按 Phase 0–19 推进（见 [AI_TASKS.md](AI_TASKS.md)）：**Phase 0~17 已全部完成并通过 Release Gate**（每个 Phase 的 Gate 记录见 [docs/testing/](docs/testing/)）。下一阶段 Phase 18 — Final QA（最终交付审查）。
