# Phase 16 Release Gate 记录 — Docker & Deployment

> 时间: 2026-09-24 ｜ 基线: aeef1c3（Phase 15 Gate）→ 79af54d（部署体系）→ 本 Gate 提交｜ 结论: **GATE PASS**

## 1. P16-01 环境审计

Docker 29.8.0 / Compose v5.5.1（WSL2 Ubuntu）；dev 编排 compose.yaml 三容器 healthy（volumes workflowx_{mysql,redis,minio}-data）。**开发运行方式（javaw jar + vite dev + compose 基础设施）与部署运行方式（全容器化编排）严格分离**。

## 2. P16-02~17 部署体系交付

| 组件 | 交付 |
|---|---|
| backend/Dockerfile | multi-stage（Maven build→JRE 21 jammy 非 root+curl），镜像 541MB |
| frontend/Dockerfile | node:20-alpine build→nginx:stable-alpine，镜像 96.4MB |
| deploy/nginx.conf | SPA try_files + /api 反代 backend:8080 + 静态缓存 + 20m body limit |
| deploy/docker-compose.yml | 5 服务 + healthchecks（service_healthy 依赖链）+ named volumes + 内部服务不暴露宿主端口 |
| deploy/.env.example | 全变量模板，CHANGE_ME 占位，prod secret 不落地 |

## 3. P16-18~19 Fresh Deployment + Flyway + Seed

`docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d`（独立 project=workflowx-deploy，全新 volumes）→ 约 60~90s 全部 healthy。**Flyway V1~V14 全部 success=1**（容器内 flyway_schema_history 核验）。dev profile seed 自动创建 admin/user1（ACTIVE）✅。

## 4. P16-28 Deployment Smoke（两轮 15/15）

health→login→me→org→project→org member→project member→issue→transition→comment→upload→download byte-equal→notification→dashboard→audit 全经 Nginx 反代真实 HTTP ✅。

## 5. P16-29~30 Docker 环境回归

pytest（test_dashboard_api + test_auth_security）对 Docker 后端 **13/13** ✅｜Playwright docker-deploy spec **3/3** ✅（登录→7 菜单→组织/项目/审计页→登出）。

## 6. P16-31~32 逐容器重启回归

backend / frontend / redis / minio 分别 restart → healthy → smoke 通过 ✅（backend restart 后 session 持久化经 Redis AOF 验证 token 仍有效——appendonly yes 设计）。

## 7. P16-22 持久化（down/up）

创建 org/proj/issue → restart backend → down → up → **issue 数据完整恢复** ✅。

## 8. P16-23 Redis 重启

登录 session 存 Redis（AOF 持久化）；重启后新登录正常、应用无异常 ✅。

## 9. P16-24 MinIO 重启

上传中文文件名文件 → restart minio → 下载 byte-equal ✅。

## 10. P16-25~26 Backup / Restore

backup.sh：MySQL mysqldump --single-transaction + MinIO mc mirror→docker cp + meta ✅（restore-verify-final 备份验证）。
restore.sh：恢复前安全备份 → SQL 恢复（docker cp + 容器内执行）→ MinIO mirror 恢复 → 对象恢复 ✅。
**验证**：seed 数据 → backup → 破坏（删附件 API）→ restore → **下载 byte-equal + DB 行恢复** ✅。

## 11. P16-33~36 审查

- **Resource**：backend 541MB image / frontend 96.4MB；容器运行内存正常（MySQL ~660MB / Redis 11MiB / MinIO 118MiB）；无异常消耗
- **Security**：MySQL/Redis/MinIO/backend 均不向宿主机暴露端口（仅 Frontend 80）；prod secret 全 fail-fast；.env gitignore；无硬编码 production secret
- **Image**：multi-stage 无源码/.git/Maven cache/node_modules 泄漏
- **Compose config**：--quiet 校验 PASS（经两轮修复：MYSQL_APP 凭据、HOST/PORT 分离、healthcheck 目标 127.0.0.1）

## 12. P16-38 文档

docs/deployment.md（23 节）+ docs/docker-architecture.md + deploy/README.md。

## 13. Product / Test / Environment Defects

- **Product Defects：0 个**（Docker 化未引入业务行为变化）
- **Test Defects：4 个（已修）**：JMX/domain 误带端口、frontend healthcheck localhost→127.0.0.1（Alpine localhost→::1 解析）、restore 凭据跨 shell 丢失（改内联）、stdin 管道跨 wsl 不稳（改 docker cp）
- **Environment Issues：1 个**：多次 thin jar（-q 模式下 repackage 失败未显式报错——已记录，构建后需核对 fat jar 大小）

## 14. Known Limitations

- 单机 Compose 部署（无 K8s/编排高可用——学习项目边界）
- HTTP 明文（生产叠加 TLS 属部署环境配置）
- MinIO 备份为 mc mirror 全量快照（非增量）
- 容器内服务日志不落盘（docker logs 查看即可）

## 15. 数据清理与终态（实测）

users=2(seed) / orgs=0 / projects=0 / issues=0 / comments=0 / attachments=0 / notifications=0 / audit=0 / Redis auth:*=0 / MinIO objects=0。

## 16. 两轮回归（最终）

四线全绿 ×2：mvn 311 / pytest 115 / Vitest 136 / Playwright 24（含 docker-deploy 3 + security 7）+ lint/build PASS + Deployment Smoke 15/15 ×2。

## 17. Gate checklist

[x] compose config PASS｜[x] fresh deployment PASS｜[x] 全容器 healthy｜[x] Flyway PASS｜[x] Persistence PASS（restart+down/up）｜[x] Restart PASS（逐容器）｜[x] Backup PASS｜[x] Restore PASS｜[x] Smoke PASS 15/15×2｜[x] pytest PASS（对 Docker 后端）｜[x] Playwright PASS（对 Docker 前端）｜[x] Vitest PASS｜[x] Maven PASS｜[x] lint PASS｜[x] build PASS｜[x] security review PASS｜[x] documentation complete｜[x] cleanup PASS｜[x] git clean

## 18. Final

# Phase 16 PASS ✅
