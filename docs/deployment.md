# WorkFlowX 部署指南

## 1. 系统架构

```
Browser ──▶ Nginx(frontend) ──/api/──▶ Spring Boot(backend) ──▶ MySQL / Redis / MinIO
```

模块化单体；5 容器 Docker Compose 编排；仅 frontend 对外暴露。详见 [docker-architecture.md](docker-architecture.md)。

## 2. Requirements

Docker + Docker Compose（推荐 WSL2 或 Linux）；2GB+ 可用内存。

## 3. Docker Installation

安装 Docker Engine 与 Compose 插件（Windows 推荐 WSL2 内 docker-ce）。本机版本：Docker 29.8.0 / Compose v5.5.1。

## 4. Environment Variables / 5. .env Configuration

```bash
cp deploy/.env.example deploy/.env
# 编辑：MYSQL_ROOT_PASSWORD / MYSQL_APP_PASSWORD / MINIO 凭据 / JWT_SECRET(≥32字节) / FRONTEND_PORT
```

必须提供全部 `:?required` 变量，否则容器拒绝启动（fail-fast）。

## 6. Start

```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d
```

## 7. Stop / 8. Restart

```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env down    # 数据保留在 volume
docker compose -f deploy/docker-compose.yml restart backend               # 单服务重启
```

## 9. Health Check

```bash
docker compose -f deploy/docker-compose.yml ps          # 全部 healthy 即就绪（约 60~90s）
curl http://localhost:${FRONTEND_PORT}/api/v1/health
```

## 10. Logs

```bash
docker compose -f deploy/docker-compose.yml logs -f backend
```

## 11. Database / 12. Redis / 13. MinIO

- MySQL：容器内 `mysql -uworkflowx -p...`；Flyway 启动自动执行 V1~V14；数据在 `workflowx-deploy-mysql-data` volume
- Redis：AOF 持久化（session 数据重启保留）；`workflowx-deploy-redis-data`
- MinIO：bucket `workflowx` 由 minio-init 自动创建；Console 9001 端口默认未对外暴露，如需查看可在 compose 中临时映射

## 14. Frontend / 15. Backend

- frontend：Nginx 服务 Vue SPA，`/api/` 反代 backend；刷新不 404（try_files）
- backend：非 root 用户运行；SIGTERM 优雅关闭；healthcheck `/actuator/health`

## 16. Default Accounts（dev profile）

admin / Admin@123456（ADMIN）；user1 / Member@123456（MEMBER）。**仅用于开发/测试，首次登录后请修改密码**。

## 17. Backup / 18. Restore

```bash
bash scripts/backup/backup.sh [目录]     # MySQL dump + MinIO mirror + meta
bash scripts/restore/restore.sh <目录>   # 恢复前自动备份当前状态
```

## 19. Troubleshooting

| 现象 | 排查 |
|---|---|
| backend 反复重启 | `docker compose logs backend`（常见：DB 未就绪/凭据错误/MinIO 不可达） |
| frontend unhealthy | healthcheck 目标为 127.0.0.1:80（容器内 localhost 解析 ::1） |
| 端口占用 | 修改 .env 的 FRONTEND_PORT |
| 登录 429 | 失败锁定（5 次/15 分钟），等 TTL 过期或清 Redis auth:fail:* |

## 20. API Smoke Test / 21. UI Smoke Test

```bash
bash scripts/deploy/smoke.sh http://localhost:${FRONTEND_PORT}   # 15 断言
# UI: 浏览器访问前端首页执行演示路径（docs/demo-scenario.md），或 Playwright docker-deploy spec
```

## 22. Production Notes

生产部署需额外：TLS 终结（Nginx 前置 LB 或证书）、强随机 JWT_SECRET/MINIO/DB 凭据（外部注入）、prod profile（Swagger 关闭、无 dev seed）、日志采集与监控、定期执行 backup.sh。本仓库交付的是开发/测试环境级部署。

## 23. Known Limitations

单机部署无高可用；MinIO 备份为全量快照；通知拉取式；容器日志不落盘（docker logs）。
