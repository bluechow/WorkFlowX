# 毕业设计素材整理（WorkFlowX）

> 客观素材整理，供毕业设计写作参考。不预设学校格式要求，不保证答辩结果。

## 1. 项目背景

中小团队需要覆盖「组织—项目—任务—协作—通知—审计」的轻量协作平台；市面 SaaS 难以私有化部署与二次开发。WorkFlowX 以"可私有化部署的模块化单体"定位解决该问题，同时作为软件测试教学/实习的真实被测系统。

## 2. 项目目标

功能完整（认证到仪表盘 15 模块）、质量可证（四线自动化+安全+性能）、可一键部署（Docker Compose）、工程过程可审计（19 个 Phase 的 Gate 记录与 ADR）。

## 3. 系统架构

前后端分离；后端为模块化单体（Spring Boot 3.5，10 个业务模块）；前端 Vue 3 SPA；数据层 MySQL 8 + Redis 7.2（会话/计数）+ MinIO（附件对象存储）；部署 Docker Compose 全栈 5 容器。详见 docs/architecture.md。

## 4. 功能模块

认证（JWT+单会话+失败锁定）、用户管理、RBAC（6 角色 49 权限）、组织与部门树、项目与成员、Issue（业务编号/分派/状态机）、评论、附件（MinIO）、站内通知（3 类型自动触发）、审计日志（16 接线点）、数据仪表盘（ECharts）。

## 5. 数据库

MySQL 8，Flyway 版本化迁移 V1~V14（13 张业务表+审计+通知），utf8mb4，外键级联策略按表设计（issue 级联评论/附件元数据；org 级联项目；通知/审计独立保留）。数据字典见 docs/database/data-dictionary.md。

## 6. 权限模型

三层：① 功能权限（Spring Security hasAuthority，49 项 `{resource}:{action}`）② 数据级（项目成员/组织成员/OWNER 规则，Service 层强制）③ 资源 ownership（评论作者/附件上传者，ADMIN 不豁免）。详见 docs/architecture/rbac.md 与 ADR-012/014/016/018/019。

## 7. 测试方案

四线测试（Maven 311 / Pytest 115 / Vitest 136 / Playwright 25）+ 安全专项（54）+ 性能四级负载。测试独立于业务代码（tests/ 目录），真实 HTTP/真实 MySQL/Redis/MinIO，禁止 mock 被测链路。分层运行：smoke/regression/full/security。

## 8. API 自动化

Pytest + httpx；统一 client（独立 token 会话）/数据工厂/断言库/契约套件（Result/PageVO 结构锁定）；命名空间隔离与会话结束自动清理（终态归零）。115 用例覆盖 14 模块正反边界。详见 docs/api-automation.md。

## 9. UI 自动化

Playwright + Page Object 模型；storageState 认证（每轮刷新）；数据经 API 工厂预置；25 用例含登录/权限渲染/各模块/通知/404/全链。详见 docs/ui-automation.md。

## 10. 性能测试

JMeter 5.6.3 四级负载（L0 基线 1 线程 → L3 受控压力 60 线程）。基线：85 req/s（1 线程）；平台 ~354 req/s（15 线程，P95 129ms）；拐点 40 线程（吞吐回落、P95 403ms）；全程错误率 0%。瓶颈第一候选为数据库连接池。**数据来自开发者笔记本，为相对基线非容量结论**。

## 11. 安全测试

认证防枚举/锁定、会话覆盖与失效、JWT 篡改（roles/sub/exp/alg=none）、RBAC 直访、IDOR 三层矩阵、SQL 注入探测（参数化验证）、XSS 渲染、文件穿越/扩展名伪装、敏感信息泄露扫描、actuator/Swagger 暴露检查、secrets scan。OWASP Top 10 映射见 docs/security/owasp-top10-mapping.md。

## 12. Docker 部署

deploy/docker-compose.yml 全栈 5 容器（Nginx/Backend/MySQL/Redis/MinIO）；healthcheck 依赖链；multi-stage 镜像构建；内部服务不暴露宿主端口；.env 注入凭据；fresh deployment 约 90s 全 healthy。详见 docs/deployment.md。

## 13. CI/CD

GitHub Actions 单主 workflow：push/PR 触发 5 jobs（前端 lint/test/build、后端 Maven+MySQL service、API/UI E2E 全栈、Docker 构建、secrets scan），失败上传报告。详见 docs/ci-cd.md。

## 14. 已知限制

单机部署无高可用；HTTP 明文（生产需 TLS）；MinIO 备份为全量快照；通知拉取式（无 WebSocket）；依赖扫描未纳入 CI；性能基线来自笔记本环境。

## 15. 后续扩展

见 docs/future-roadmap.md（V1.1 测试管理 / V1.2 敏捷看板 / V1.3 WebSocket 通知 / V2.0 AI 辅助测试）。
