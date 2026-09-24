# Final QA 报告（Phase 18）

> 时间: 2026-09-25 ｜ 基线: ad4d05e（Phase 17 Gate）｜ 审查视角: 交付前 QA/Reviewer（非开发者）

## 1. QA Scope

对 Phase 0~17 全部交付物做系统级最终审查：功能完整性、认证/授权/数据级安全、API 契约、前端 UX、四线自动化、安全、性能回归、Docker 部署、持久化、备份恢复、CI/CD、文档一致性、终态数据清理。**不新增任何功能**（只修复缺陷）。

## 2. Feature Matrix（基于真实实现+测试证据）

| 模块 | Backend API | Service | Migration | Frontend | 测试 | 结论 |
|---|---|---|---|---|---|---|
| Authentication | /auth/login·logout·me·me/permissions | Auth/Session/LoginAttempt | V1,V2 | LoginView+auth store | auth 7+security 10 | COMPLETE |
| User | /users CRUD+status | UserService | V1 | UsersView（P11 补齐） | 17+security | COMPLETE |
| Role/Permission | /roles /permissions /users/{id}/roles | RbacService | V3~V14 | Roles/Permissions/UserRoles View | rbac 9 | COMPLETE |
| Organization/Department | /orgs /departments | OrganizationService | V4,V5 | OrganizationsView+Detail | org 5 | COMPLETE |
| Project/Member | /projects /members | Project/MemberService | V6,V7,V8 | ProjectsView | project 5 | COMPLETE |
| Issue | /projects/{id}/issues CRUD+status+assignee | IssueService | V9,V10 | IssuesView | issue+workflow 12+boundary 7 | COMPLETE |
| Workflow | PATCH status | WorkflowService（矩阵+条件 UPDATE） | V11 | allowedTargets 下拉 | workflow 12 | COMPLETE |
| Comment | /comments CRUD | CommentService | V12 | 抽屉评论面板 | comment 10 | COMPLETE |
| Attachment | /attachments upload/download/delete | Attachment+StorageService（MinIO） | V12 | 附件面板 | attachment 11 | COMPLETE |
| Notification | /notifications×4 | NotificationService（3 类型触发） | V13 | 铃铛+通知中心 | notification 8 | COMPLETE |
| Audit | /audit-logs×2 | AuditService（16 接线点） | V14 | AuditView | audit 5 | COMPLETE |
| Dashboard | /dashboard/overview | DashboardService（真实聚合） | —（动态查询） | StatsSection+ECharts | dashboard 3 | COMPLETE |
| Deployment | — | — | — | — | docker-deploy 3+smoke 15 | COMPLETE |
| CI/CD | — | — | — | — | 本地等价验证 | COMPLETE |

## 3-6. 功能/安全/API/UI QA（复验结论）

全部复用既有自动化作为证据（本 QA 阶段全量重跑通过，见 §14）：防枚举统一 401、锁定阶梯（5 次/15min）、单会话覆盖、JWT 篡改拒绝（roles/sub/exp/alg=none）、RBAC 403 矩阵、IDOR 三层隔离（ADMIN 不豁免 ownership、通知 self 隔离 404）、SQLi 字面存储、XSS 纯文本渲染、路径穿越剥离、Result/PageVO 契约（安全层 401/403 无 data 字段）、traceId 贯穿、安全响应头（nosniff/DENY）。

## 7. Performance regression（轻量 smoke，非 Phase 14 重跑）

| 端点 | avg | max | 状态 |
|---|---|---|---|
| health | 4ms | 5ms | 200 ✅ |
| project list | 8ms | 9ms | 200 ✅ |
| dashboard | 14ms | 15ms | 200 ✅ |

与 Phase 14 基线（L0 avg 12ms）同量级，**无回归**。

## 8-9. Docker / Persistence QA（fresh deployment 复验）

`down → up`（named volumes 保留；删除 network 后重建，Flyway V1~V14 重新执行成功）→ 5 容器全部 healthy → smoke 15/15 ×2 ✅。

## 10. Backup/Restore Review

backup.sh / restore.sh 可执行性复核 PASS；Phase 16 的破坏→恢复→byte-equal 证据仍然有效；本阶段未重复破坏性验证（按 Final QA 边界）。

## 11. CI/CD QA

ci.yml YAML valid（5 jobs）｜无 secret 泄漏｜无 skip test｜secrets-scan PASS。**GitHub remote not configured**——本地等价验证已执行（phase17-gate.md §3），GitHub-hosted runner 结果待仓库推送后自动产生。

## 12-13. Defects（Round 1 发现与分类）

| 类别 | 数量 | 明细 |
|---|---|---|
| **Test Defect** | 3 | ① docker-deploy.spec 用 seed admin UI 登录→顶掉 setup storageState→后续 factory 401（违反 P13-06 铁律）→改动态 ADMIN 用户 ② secrets-scan.sh Git Bash 双引号循环在中文路径下性能退化超时→Python 重写 ③ .env.example 占位值误报→白名单补齐 |
| **Documentation Defect** | 2 | ① README 停留在 Phase 2 状态（技术栈/功能/测试矩阵/阶段声明全部过时）→重写至 Phase 0~17 实际 ② how-to-run-tests.md 覆盖说明过时（仅列 2 用例）→重写四线运行方式 |
| **Product Defect** | 0 | — |
| **Security Defect** | 0 | — |
| **Deployment Defect** | 0 | — |
| **CI Defect** | 0 | — |
| **Environment Issue** | 1 | 后端进程存活窗口与 pytest 竞争（重启/等待处理，非产品问题） |

## 14-15. Round 2 + Final Cleanup

Round 2 全部 PASS（§13 修复后回归）：**mvn 311 / pytest 115 / Vitest 136 / Playwright 25（serial ×3 稳定）/ lint / build / smoke 15/15 ×2 / secrets scan PASS**。

终态（实测）：users=2（admin/user1 seed，api_test_ 临时用户已清）｜user_roles=0｜organizations=0｜departments=0｜projects=0｜project_members=0｜issues=0｜comments=0｜attachments=0｜notifications=0｜audit_logs=0｜Redis auth:*=0｜MinIO objects=0。migration history/schema/seed 完整保留；系统表 roles=6、permissions=49 完整。

## 16. Known Limitations（延续各 Phase 记录，无新增）

单机 Compose 无高可用｜HTTP 明文（生产 TLS 属环境配置）｜MinIO 备份全量快照｜依赖扫描 NOT RUN（工具限制）｜通知拉取式（无 WebSocket）｜issue_status_transitions 表决策 D 不建（ADR-020）。

## 17. 毕业设计/简历交付事实核查

- 演示链路完整：登录→组织→项目→Issue→工作流→评论→附件→通知→审计→仪表盘全部可真实演示（Playwright chain spec 为证）
- 四线自动化+性能+安全+CI+Docker 全部有真实可执行命令与文档
- 无无法启动的模块；README/getting-started 与实际一致（本阶段已修复过时文档）
