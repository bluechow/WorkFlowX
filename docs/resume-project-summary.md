# 简历项目素材（WorkFlowX）

> 版本注记：v2.0.0 Final Edition 已交付（工作台/项目空间/标签关联/活动流/里程碑/全局搜索/@提及/数据分析），数据可按最新版微调；本文要点仍然成立。

> 事实型素材，供简历/面试整理使用。数据均来自项目实际 Gate 记录，可直接核验。

## 一句话

**前后端分离的企业项目协作与工单管理平台（模块化单体 + Docker Compose 全栈部署），配套四线自动化测试体系与 CI/CD。**

## 技术栈

- 后端：Java 21 · Spring Boot 3.5 · Spring Security · MyBatis-Plus · Flyway · JWT
- 前端：Vue 3 · TypeScript · Vite · Pinia · Element Plus · ECharts
- 数据：MySQL 8 · Redis 7 · MinIO
- 工程：Docker Compose · GitHub Actions · JMeter · Pytest · Playwright · Vitest

## 核心工作（可量化）

- 独立设计并实现 10 个业务模块（认证/用户/RBAC/组织/项目/Issue/工作流/评论/附件/通知/审计/仪表盘），Flyway 版本化迁移 V1~V14，49 项权限的三层访问控制（功能权限 + 数据级 + 资源 ownership）
- 搭建四线自动化测试体系：**Maven 311 + Pytest 115 + Vitest 136 + Playwright 25**，含契约测试、并发测试（状态机 8 线程仅 1 成功、编号唯一递增）、安全专项（认证防枚举/JWT 篡改/IDOR/SQLi/XSS/文件穿越 54 用例）
- 建立 Docker 全栈部署（5 容器 healthcheck 依赖链、非 root、内部服务不暴露端口）与 MySQL+MinIO 备份/恢复脚本
- 建立 GitHub Actions CI（5 jobs：前端/后端/全栈 E2E/镜像构建/secrets scan），测试失败自动归档报告
- JMeter 四级性能负载：定位吞吐平台 ~354 req/s 与拐点（40 线程），全程错误率 0%

## 交付质量

- Phase 0~18 共 19 个阶段全部通过 Release Gate（每阶段 Gate 记录含实测证据）
- Final QA 后终态：测试数据全零、seed 保留、仓库 clean、secrets scan PASS
- 21 个 ADR（架构决策记录）覆盖技术选型与安全/一致性策略

## 工程亮点（面试可展开）

1. **状态机并发正确性**：条件 UPDATE（`WHERE status=fromStatus`）实现乐观并发，8 线程竞争仅 1 成功；放弃 version 字段与 MQ
2. **双层会话模型**：JWT 无状态 + Redis 单会话覆盖（防 token 重放），禁用即踢线
3. **测试数据生命周期工程**：跨框架（pytest/Playwright/JMeter）命名空间隔离 + 会话终态自动归零，消除跨轮污染
4. **契约测试防回归**：API envelope/PageVO 结构锁定——曾据此发现前端解析缺陷（列表恒空）被单测 mock 掩盖的问题
5. **附件安全闭环**：扩展名白名单 + 服务端生成 objectKey + 上传补偿删除 + 删除先对象后元数据

## 已知限制（如实）

单机部署（无高可用）；HTTP 明文（生产需 TLS）；通知为拉取式；性能基线来自笔记本环境。
