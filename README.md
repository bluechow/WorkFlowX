# WorkFlowX

企业级项目协作与工单管理平台 —— 面向中小型团队的项目管理、Issue 管理、团队协作、权限控制、通知、审计和数据统计。

> 本项目同时是一个测试学习平台：每一个业务功能都考虑如何测试、如何自动化、如何回归（详见 [AI_MASTER_PROMPT.md](AI_MASTER_PROMPT.md) §40）。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 21 · Spring Boot 3.5.x · Spring Security（Phase 2）· MyBatis-Plus · JWT（Phase 2） |
| 前端 | Vue 3 · TypeScript · Vite · Pinia · Vue Router · Element Plus |
| 数据层 | MySQL 8.x · Redis · MinIO |
| 测试 | JUnit 5 + MockMvc · Vitest · Python + Pytest + httpx ·（Playwright / Allure / JMeter 随 Phase 12–14 引入） |
| 基础设施 | Docker Compose（MySQL / Redis / MinIO） |
| 架构 | Modular Monolith（[ADR-001](AI_DECISIONS.md)） |

## 架构

```text
Frontend (Vue 3 + Vite)
    ↓ /api 代理
Spring Boot (:8080)
    ↓
┌──────────────┬──────────────┬──────────────┐
MySQL (:3307)  Redis (:6379)  MinIO (:9000/9001)
     └──────────── Docker Compose ────────────┘
```

后端业务模块：auth / user / organization / project / issue / notification / audit / dashboard / system / common。

## 快速启动

```bash
# 0. 环境要求: JDK 21 / Node 18+ / Python 3.11+ / WSL2 + Docker
#    详细步骤见 docs/development/getting-started.md

# 1. 配置本地环境变量（首次）
cp .env.example .env

# 2. 启动基础设施（MySQL / Redis / MinIO）
docker compose up -d

# 3. 启动后端（默认 dev profile，端口 8080）
cd backend && mvn spring-boot:run

# 4. 启动前端（端口 5173，/api 代理到 8080）
cd frontend && npm install && npm run dev
```

验证：

- 打开 http://localhost:5173 → 首页展示"后端服务正常"
- `curl http://localhost:8080/api/v1/health` → 统一响应结构
- API 文档（dev）：http://localhost:8080/swagger-ui

## 测试

三层测试运行方式见 [docs/testing/how-to-run-tests.md](docs/testing/how-to-run-tests.md)：

```bash
cd backend  && mvn test          # 后端单元/Web 切片
cd frontend && npm run test      # 前端组件冒烟
pytest                             # API 自动化（需后端运行中）
```

## 文档

| 文档 | 内容 |
|---|---|
| [docs/development/getting-started.md](docs/development/getting-started.md) | 开发环境搭建（Windows / WSL 细节） |
| [docs/api/api-conventions.md](docs/api/api-conventions.md) | API 设计约定 |
| [docs/database/er-model.md](docs/database/er-model.md) | ER 模型 |
| [docs/database/data-dictionary.md](docs/database/data-dictionary.md) | 数据字典 |
| [docs/database/migration-plan.md](docs/database/migration-plan.md) | 数据库迁移计划 |
| [docs/testing/how-to-run-tests.md](docs/testing/how-to-run-tests.md) | 测试运行说明 |
| [AI_MASTER_PROMPT.md](AI_MASTER_PROMPT.md) | 项目永久规则（AI 协作约束） |
| [AI_DECISIONS.md](AI_DECISIONS.md) | 技术决策记录（ADR） |

## 项目阶段

项目按 Phase 0–19 推进（见 [AI_TASKS.md](AI_TASKS.md)）：当前 **Phase 1 — Project Foundation 已完成**，下一阶段 Phase 2 — Authentication & User。
