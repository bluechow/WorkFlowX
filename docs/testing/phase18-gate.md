# Phase 18 Release Gate 记录 — Final QA

> 时间: 2026-09-25 ｜ 基线: ad4d05e（Phase 17 Gate）→ 402efeb/ac47b0a（缺陷与文档修复）→ 本 Gate 提交｜ 结论: **GATE PASS**

## 1. Gate checklist（28 项）

[x] 全局代码审计（git clean @ad4d05e、无敏感/临时/debug 文件、.gitignore 6 项关键覆盖、503 文件）｜[x] Feature Matrix（14 模块全 COMPLETE）｜[x] Authentication（锁定 5 次/15min、JWT 2h、单会话——代码+测试一致）｜[x] RBAC（49 权限、6 角色、前后端一致）｜[x] Data-level security（IDOR 三层）｜[x] Project｜[x] Issue｜[x] Workflow｜[x] Comment｜[x] Attachment｜[x] Notification｜[x] Audit｜[x] Dashboard｜[x] Frontend（P13 缺陷不回归）｜[x] API Contract｜[x] Maven 311×2｜[x] Vitest 136×2｜[x] pytest 115×2｜[x] Playwright 25×3（serial 稳定）｜[x] Security（suite 47+7+secrets scan）｜[x] Performance regression（health 4ms/list 8ms/dashboard 14ms 无回归）｜[x] Docker（config VALID+fresh deployment 5 healthy）｜[x] Persistence（down/up 数据恢复）｜[x] Backup/Restore scripts review｜[x] CI/CD review（YAML valid、无泄漏、GitHub remote not configured 记录）｜[x] Documentation consistency（README+how-to-run-tests 重写）｜[x] Final cleanup（全零+seed 保留）｜[x] Round 1｜[x] Round 2｜[x] Git clean｜[x] Gate evidence complete

## 2. 实际命令与结果（四线两轮）

| 套件 | Round 1 | Round 2 |
|---|---|---|
| mvn -B test | 311/311 | 311/311 |
| pytest -q | 115/115 | 115/115 |
| Vitest --run | 136/136 | 136/136 |
| Playwright --workers=1 | 25/25（×3 稳定） | 25/25 |
| lint / build | PASS | PASS |
| Deployment smoke | 15/15 | 15/15 |
| secrets scan | PASS | PASS |
| Docker fresh deployment | 5 healthy | 5 healthy |

## 3. Round 1：发现与分类

**Test Defects（3，已修复+回归）**：
1. docker-deploy.spec 用 seed admin UI 登录 → 顶掉 setup storageState → 后续 factory createOrg 401 连锁（traceId 30192096 实证）→ 改动态 ADMIN 用户；
2. secrets-scan.sh Git Bash 双引号 for 循环在中文路径下性能退化（timeout 124 实证）→ Python 重写（单遍扫描）；
3. secrets-scan 白名单遗漏 .env.example 占位值 → 误报 → 补齐 allowlist。

**Documentation Defects（2，已修复）**：
1. README.md 停留在 Phase 2 状态（技术栈缺 Flyway/ECharts/Playwright/JMeter/CI、功能清单过时、阶段声明"下一阶段 Phase 3"）→ 重写至 Phase 0~17 实际（含 Docker 部署/CI badge 章节/测试矩阵）；
2. how-to-run-tests.md 覆盖说明过时（"当前覆盖：健康检查…2 用例"）→ 重写四线运行方式+数据隔离说明。

**Environment Issues（1）**：后端进程存活窗口与 vitest/pytest 竞争——非产品问题。

## 4. Round 2

全部 PASS（§2 表格）。Playwright serial 模式（--workers=1）连续 3 次 25/25，确认 notification/security 用例稳定性（Round 1 曾出现 Playwright 并发 worker 竞态假失败，serial 模式为项目既定策略 fullyParallel: false）。

## 5. Final Cleanup（实测终态）

users=2（admin/user1 seed，ACTIVE）｜user_roles=0｜organizations=0｜departments=0｜projects=0｜project_members=0｜issues=0｜comments=0｜attachments=0｜notifications=0｜audit_logs=0｜Redis auth:*=0｜MinIO objects=0。系统表 roles=6、permissions=49 完整；flyway_schema_history V1~V14 完整。

## 6. Defect classification 汇总

- Product Defects: **0**
- Test Defects: 3（已修）
- Deployment Defects: 0
- Documentation Defects: 2（已修）
- Security Defects: 0
- CI Defects: 0
- Environment Issues: 1（已处理）

## 7. Known limitations（无新增，延续各 Phase 记录）

单机 Compose｜HTTP 明文（生产 TLS 属环境配置）｜MinIO 全量快照备份｜依赖扫描 NOT RUN｜通知拉取式｜§14 transitions 表决策 D（ADR-020）。

## 8. Final commit

本 Gate 提交（final-qa.md + phase18-gate.md + 总控同步）。

## 9. Gate Decision

# Phase 18 PASS ✅
