# 开发环境搭建指南

> 任务 1-8 ｜ 适用环境: Windows（本指南基于实际安装过程验证，2026-09-06）

## 1. 环境要求

| 工具 | 版本要求 | 说明 |
|---|---|---|
| JDK | 21 LTS（Temurin 等） | `JAVA_HOME` 指向 JDK 21（ADR-002） |
| Maven | 3.9+ | 建议配置国内镜像（Aliyun）加速 |
| Node.js | 18+（含 npm） | 建议配置 npmmirror 源 |
| Python | 3.11+ | 用于 API 自动化测试 |
| Docker | Docker CE / Docker Desktop | 本项目统一经 Docker Compose 管理基础设施（ADR-007） |

检查：

```bash
java -version     # 应显示 21.x
mvn -v            # Java version 应为 21
node -v
python --version
docker version    # WSL 内或 Windows 侧均可
```

## 2. 获取代码

```bash
git clone <仓库地址> WorkFlowX
cd WorkFlowX
```

## 3. 启动基础设施

```bash
# 首次: 创建本地环境变量（含数据库密码等，勿提交 .env）
cp .env.example .env

# 启动 MySQL / Redis / MinIO
docker compose up -d

# 确认三容器 healthy
docker compose ps
```

说明：

- MySQL 主机端口默认 **3307**（避开本机 3306 遗留服务），容器内仍为 3306；后端 dev 配置已对齐
- MinIO Console: http://localhost:9001 ；bucket `workflowx` 由 minio-init 自动创建
- 若使用 WSL 内 docker-ce（本机方案）：命令前加 `wsl -d Ubuntu --`，例如
  `wsl -d Ubuntu -- bash -c "cd /mnt/d/codex协同项目/WorkFlowX && docker compose up -d"`

### WSL 注意事项（本机实测）

- WSL 虚拟机默认空闲 60 秒被回收，会导致下次调用冷启动、MySQL 重新初始化（约 30 秒）。
  已在 `~/.wslconfig` 配置 `vmIdleTimeout=86400000` 延长；如仍频繁冷启动可按需调整。
- Docker 镜像加速已配置在 WSL 内 `/etc/docker/daemon.json`。

## 4. 启动后端

```bash
cd backend
mvn spring-boot:run          # 默认 dev profile，端口 8080
```

验证：`curl http://localhost:8080/api/v1/health` 返回统一响应结构（code/message/data/timestamp/traceId）。

可用环境变量覆盖默认连接（见 `application-dev.yml`）：`MYSQL_HOST` `MYSQL_PORT` `MYSQL_DATABASE` `MYSQL_USERNAME` `MYSQL_PASSWORD` `REDIS_HOST` `REDIS_PORT`。

## 5. 启动前端

```bash
cd frontend
npm install
npm run dev                  # 端口 5173，/api 代理到 8080
```

验证：打开 http://localhost:5173 ，首页显示"后端服务正常"（Loading → Success 三态链路即通）。

## 6. 测试账号（仅 DEV 环境）

> ⚠️ 以下凭据由 dev 种子迁移（`db/seed/dev/V2__seed_dev.sql`）创建，**仅用于本地开发与测试**；
> 生产环境不配置该 Flyway location，不存在默认凭据，正式环境必须使用独立强密码。

| 账号 | 密码 | 角色 | 说明 |
|---|---|---|---|
| admin | Admin@123456 | ADMIN | 系统管理员（Phase 3 起用于权限验证） |
| user1 | Member@123456 | MEMBER | 普通用户（用于越权/权限反例测试） |

| 账号 | 密码 | 角色 | 说明 |
|---|---|---|---|
| admin | Admin@123456 | ADMIN | 系统管理员（用户管理 API 的操作者） |
| user1 | Member@123456 | MEMBER | 普通用户（用于越权/权限反例测试） |

登录接口 `POST /api/v1/auth/login` 已可用（curl 示例）：

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123456"}'
```

相关端点：`POST /api/v1/auth/logout`、`GET /api/v1/auth/me`、用户管理 `GET/POST/PUT/PATCH /api/v1/users*`（ADMIN）。安全架构详见 [docs/architecture/security.md](../architecture/security.md)。

## 7. 运行测试

见 [docs/testing/how-to-run-tests.md](../testing/how-to-run-tests.md)。

## 8. 常见问题

| 现象 | 处理 |
|---|---|
| 后端启动报 MySQL 连接失败 | 确认 `docker compose ps` 中 mysql 为 healthy；确认端口 3307 未被占用 |
| 前端首页一直 Loading / 显示网络错误 | 确认后端 8080 已启动；Vite 代理仅对 `/api` 前缀生效 |
| `docker compose up` 报缺少环境变量 | 未创建 `.env`（步骤 3），或 `cp` 后未保存 |
| MySQL 初始化很慢 / 每次都很慢 | 见步骤 3 的 WSL 注意事项（vmIdleTimeout） |
| 拉取镜像超时 | 检查 WSL 内 `/etc/docker/daemon.json` 镜像加速配置 |
