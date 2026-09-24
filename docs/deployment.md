# WorkFlowX 部署指南（Phase 16）

## 1. System Architecture

```
Browser ──HTTP──▶ Nginx (frontend:80) ──/api/──▶ Spring Boot (backend:8080)
                                                      │
                              ┌────────────┬──────────┴──────────┐
                              ▼            ▼                     ▼
                          MySQL 8       Redis 7               MinIO
                        (workflowx)   (sessions)      (workflowx bucket)
```

## 2. Requirements

Docker + Docker Compose（其余构建/运行全部容器化）。本机: Docker 29.8.0 / Compose v5.5.1。

## 3. 首次部署

```bash
cp deploy/.env.example deploy/.env
# 编辑 deploy/.env 填入真实密码/JWT_SECRET（≥32 字节）
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d
# 等待全部 healthy（约 1~2 分钟）
docker compose -f deploy/docker-compose.yml ps
```

## 4. 访问入口

| 入口 | 地址 |
|---|---|
| 前端 SPA | http://localhost:${FRONTEND_PORT:-80} |
| 后端 API | 经 Nginx /api 反代（不直接暴露） |
| Swagger | http://localhost:${FRONTEND_PORT}/swagger-ui（dev profile） |

## 5. 默认账号（dev profile 自动 seed）

admin/Admin@123456（ADMIN）、user1/Member@123456（MEMBER）。**首次登录后建议改密**。

## 6. Health Check / Logs

```
docker compose -f deploy/docker-compose.yml ps
curl http://localhost:${FRONTEND_PORT}/api/v1/health
docker compose -f deploy/docker-compose.yml logs backend
```

## 7. Backup / Restore

```
bash scripts/backup/backup.sh [目录]       # MySQL dump + MinIO mirror + meta
bash scripts/restore/restore.sh <备份目录>  # 自动先备份当前状态再恢复
```

## 8. Security Notes

- MySQL/Redis/MinIO 不向宿主机暴露端口（仅集群网络）
- backend 不直接对外（Nginx 反代 /api）
- prod profile：JWT_SECRET/MINIO 凭据缺失即启动失败（fail-fast）
- Swagger 默认仅 dev profile

## 9. Known Limitations

- 单机部署（无 K8s/编排高可用——学习项目边界）
- MinIO 备份为 mc mirror 快照（非增量）
- HTTP 明文（生产叠加 TLS 属部署环境配置）
