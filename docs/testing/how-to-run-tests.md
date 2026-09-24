# 测试运行说明

> Phase 12/13/15/17 持续完善；当前四线：Maven / Vitest / Pytest / Playwright

## 0. 前置条件

- 基础设施已启动：`docker compose up -d`（仓库根目录；MySQL :3307 / Redis :6379 / MinIO :9000）
- 后端已启动：`mvn -f backend/pom.xml spring-boot:run`（或运行 jar）；dev profile 自动 Flyway V1~V14 + seed（admin/Admin@123456）

## 1. 后端测试（单元 / 集成，真实 MySQL）

```bash
cd backend
mvn test          # 311 用例
```

覆盖：统一响应/traceId、认证与会话、RBAC、组织、项目、Issue、Workflow 状态机、评论、附件（真实 MinIO）、通知、审计、仪表盘聚合。

## 2. 前端测试

```bash
cd frontend
npm run test -- --run   # Vitest 136 用例（组件/store/api/router）
npm run lint            # ESLint
npm run build           # vue-tsc 类型检查 + 产物构建

# UI 自动化（Playwright，需 backend :8080 + dev server 或 Docker 部署）
npx playwright install chromium    # 首次
npx playwright test                # 25 用例（auth/security/core/notification/chain/docker）
```

## 3. API 自动化（Python + Pytest，真实 HTTP，需后端运行中）

```bash
pip install -r tests/requirements.txt
pytest                     # 115 用例（默认 http://localhost:8080）
WORKFLOWX_BASE_URL=http://localhost:8081 pytest   # 指定后端地址
pytest -m security         # 安全专项（47 用例）
pytest -m smoke            # 快速冒烟（contract suite）
pytest -m regression       # 核心回归
```

数据隔离：`api_test_` 用户前缀、`SECURITY_`/`AA`/`PERF` 命名空间；会话结束自动清理（终态归零，见 conftest.py）。

## 4. 性能测试（JMeter，手动）

见 [performance-testing.md](performance-testing.md)。

## 5. 安全测试

见 [security-testing.md](security-testing.md) 与 `bash scripts/ci/secrets-scan.sh`。

## 6. Docker 部署环境下的测试

部署栈（deploy/docker-compose.yml）启动后，可用 `WORKFLOWX_BASE_URL=http://localhost:${FRONTEND_PORT}` 指向 Docker 后端执行 pytest；Playwright 可用 `PLAYWRIGHT_BASEURL` 指向 Docker 前端。参考 frontend/e2e/specs/docker-deploy.spec.ts。
