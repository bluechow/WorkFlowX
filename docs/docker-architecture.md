# Docker 架构（Phase 16）

```
Browser ──:80──▶ Nginx(frontend) ──/api/──▶ Spring Boot(backend:8080)
                                                │
                         ┌──────────┬───────────┴──────────┐
                         ▼          ▼                      ▼
                     MySQL 8     Redis 7               MinIO
                  (mysql-data) (redis-data)        (minio-data)
```

## 编排分离

| 文件 | 用途 | 服务 |
|---|---|---|
| compose.yaml（仓库根） | 开发基础设施（仅 MySQL/Redis/MinIO；宿主端口 3307/6379/9000） | mysql/redis/minio |
| deploy/docker-compose.yml | 部署全栈（含 backend/frontend 构建与编排） | 5 服务 |

## 网络与端口

部署栈所有服务在 `workflowx-deploy_default` 网络内经 service name 通信。对外仅暴露 Frontend Nginx（FRONTEND_PORT，默认 80）。backend/mysql/redis/minio 均不向宿主机暴露（安全评审 P16-34）。

## 数据持久化

named volumes：workflowx-deploy-{mysql,redis,minio}-data。`docker compose down` 不删 volume；`down -v` 才删除。**附件对象在 MinIO 中不随 org/issue 级联删除**——备份脚本需显式 mc mirror。

## 镜像

- backend：multi-stage（Maven build → JRE 21 jammy + 非 root + curl healthcheck）541MB
- frontend：node:20-alpine build → nginx:stable-alpine（SPA+反代）96.4MB
