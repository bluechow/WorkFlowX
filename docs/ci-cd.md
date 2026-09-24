# CI/CD 体系（Phase 17）

## 1. 架构

单主 workflow：`.github/workflows/ci.yml`（5 jobs，push/PR → master/main 触发，同 ref 并发去重）。
不拆 security.yml/docker.yml——项目规模下单文件更可维护；安全扫描与镜像构建均为独立 job。

```
push / PR → master·main
  ├─ frontend        Node 22 → npm ci → lint → Vitest → build
  ├─ backend         Java 21 + MySQL service(:3307) → mvn -B test（Flyway+seed 自动）
  ├─ api-ui-e2e      MySQL+Redis+MinIO services → backend jar(8080) → vite(5173)
  │                  → pytest 115 → Playwright 24
  ├─ docker-build    deploy compose config → backend/frontend 镜像构建 + size 记录
  └─ security-scan   scripts/ci/secrets-scan.sh（Git 跟踪文件秘密扫描）
```

## 2. 环境一致性

| 层 | 本机验证 | CI |
|---|---|---|
| Node | 22.22.3 | actions/setup-node 22 + npm cache |
| Java | 21.0.12 Temurin | temurin 21 + maven cache |
| Python | 3.11.4 | setup-python 3.11 + tests/requirements.txt |
| MySQL | dev compose :3307 | CI service :3307（Flyway+seed 自动迁移） |
| Playwright | chromium（已装） | npx playwright install --with-deps chromium |

## 3. 关键设计

- **后端 job**: MySQL service 映射 :3307（对齐 application-dev.yml 默认），Flyway+dev seed 自动执行——与本地行为一致；失败上传 surefire-reports
- **api-ui-e2e job**: mvn package(-DskipTests) → 后端 jar 后台运行（/actuator/health 轮询就绪，失败输出日志）→ vite dev → MinIO bucket 初始化（mc mb --network host）→ pytest → Playwright；失败上传 backend 日志与 Playwright report
- **就绪等待**: 全部为带退出条件的轮询（明确失败），禁止裸 sleep
- **Secrets**: 仓库无任何真实 secret（Phase 15 secrets scan PASS）；CI 无需额外 secrets
- **安全**: security-scan job 复用 scripts/ci/secrets-scan.sh（本地与 CI 同一脚本）

## 4. 分层

PR/push 均运行全部 jobs（当前全量 ~15 分钟，无昂贵长压测——性能测试按 Phase 14 手动触发，不入 CI）。

## 5. 失败处理

- frontend/backend 失败 → 上传 surefire-reports / Playwright report / backend.log
- api-ui-e2e 失败 → 保留 backend.log（定位启动/迁移问题）
- 分类流程: Product Defect → 修复+回归；Test/CI Defect → 修测试/配置

## 6. 本地复现

```bash
# frontend
cd frontend && npm ci && npm run lint && npm run test -- --run && npm run build
# backend
cd backend && mvn -B test
# api+ui（需 dev compose + backend jar + vite dev）
cd tests/api && python -m pytest
cd frontend && npx playwright test
# docker
docker compose -f deploy/docker-compose.yml --env-file deploy/.env config
docker build -f backend/Dockerfile -t workflowx/backend:ci .
# security
bash scripts/ci/secrets-scan.sh
```

## 7. Branch Protection 建议（需仓库管理员，Phase 17 未自动修改）

main：Require PR + Require CI checks（frontend/backend/api-ui-e2e/docker-build/security-scan）+ Require branch up to date。

## 8. Known Limitations

- 仓库暂无 remote：workflow 推送至 GitHub 后首次运行需在 Actions 页启用
- Playwright CI 使用 chromium（与本地一致）；其他浏览器引擎未覆盖
- 依赖扫描（npm audit/dependency-check）未纳入（镜像源/工具限制，见 Phase 15 §17）
