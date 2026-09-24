# WorkFlowX 系统架构

## 总体架构

```
Browser
  ↓ HTTP
Nginx (frontend 容器:80，SPA 静态资源 + /api 反向代理)
  ↓ /api/**
Spring Boot (backend 容器:8080，模块化单体)
  ↓
┌──────────────┬──────────────┬──────────────┐
MySQL 8       Redis 7.2       MinIO
(业务数据)     (会话/计数)      (附件对象)
```

**前后端分离 + 模块化单体后端 + Docker Compose 部署。不是微服务架构**（ADR-001）。

## 后端模块（com.workflowx.*）

| 模块 | 职责 |
|---|---|
| auth | 登录/JWT/单会话/失败锁定 |
| user | 用户 CRUD/状态/踢线 |
| rbac | 角色/权限/用户绑定（49 项系统权限） |
| org | 组织/部门/组织成员 |
| project | 项目/归档/项目成员 |
| issue | Issue CRUD/编号/分派 |
| issue（Workflow） | 状态机矩阵（ADR-017） |
| issue（Comment/Attachment） | 评论/附件（MinIO） |
| notification | 站内通知（3 类型触发） |
| audit | 审计日志（16 接线点） |
| dashboard | 数据聚合 |
| common | 安全/JWT/异常/traceId/存储抽象 |

## 前端结构（src/）

views（13 页面）· components（dashboard/issue/notification）· stores（auth/notification/health）· api（14 模块）· router（守卫+404）· utils（token）。

## 部署形态

- 开发：compose.yaml（仅 MySQL/Redis/MinIO，端口 3307/6379/9000）+ 本机运行 backend/frontend
- 部署：deploy/docker-compose.yml（全栈 5 容器，仅 Nginx 对外暴露）

详细见 [docker-architecture.md](docker-architecture.md) 与 [deployment.md](deployment.md)。
