# Phase 17 Release Gate 记录 — CI/CD

> 时间: 2026-09-24 ｜ 基线: fedde00（Phase 16 Gate）→ 09b3561（CI workflow）→ 69dcf4b（清理收敛）→ 本 Gate 提交｜ 结论: **GATE PASS**

## 1. P17-01 审计（现状）

- git：本地仓库 master 分支，**无 remote**（workflow 推送 GitHub 后需页面启用 Actions）
- 无 .github/workflows；无 Maven wrapper（系统 Maven 3.9.x + setup-java 内置 Maven）
- frontend：package-lock ✓（npm ci）、Node 22.22.3、Playwright chromium 已装
- tests/requirements.txt ✓（pytest/httpx/pymysql/redis），Python 3.11.4
- 测试依赖矩阵：mvn 集成→MySQL(3307)+Flyway+seed；pytest/Playwright→MySQL+Redis+MinIO+Backend+Frontend 全栈；Vitest 纯单元
- 无 CI 专用文件（从零建）

## 2. CI 架构（.github/workflows/ci.yml）

5 jobs：frontend（npm ci/lint/Vitest/build）｜backend（MySQL service :3307 + mvn -B test + surefire 上传）｜api-ui-e2e（infra services + MinIO bucket 初始化 + backend jar 就绪轮询 + vite + pytest 115 + Playwright 24 + 报告上传）｜docker-build（compose config + backend/frontend 镜像构建 + size 记录）｜security-scan（scripts/ci/secrets-scan.sh）。YAML 解析校验 PASS。

## 3. 本地等价验证（Round 1 —— 发现问题并修复）

| 步骤 | 结果 |
|---|---|
| secrets-scan | 初跑 FAIL（.env.example 占位值误报）→ 白名单修正 → **PASS** |
| frontend npm ci | EPERM（node 残留进程锁定 rolldown .node 文件）→ 终止进程后成功；lint/Vitest 136/build PASS |
| backend mvn test | 311/311 PASS |
| api-ui-e2e pytest | 暴露**测试数据生命周期缺陷**（见 §13）→ 修复后 115/115 |
| api-ui-e2e Playwright | 24/24 |
| docker-build | compose config VALID + backend/frontend 镜像构建 PASS（size 已记录） |

**Round 1 暴露的真实问题（按分类）**：
- **Test Defect（数据生命周期）**：test_idor sec_env 清理未带认证头（org 删除 401 静默吞→跨轮 90 组织化石）+ test_input _seed_project 未登记 cleanup_orgs + conftest 全局组织兜底清理游标作用域错误（Cursor closed）→ 三处修复（cleanup_orgs 登记、管理员头删除、光标作用域内执行）
- **Test Defect（断言对齐）**：跨项目 issueId 替换契约实为 403（数据级先于资源校验，ADR-016.4/记忆㉔）→ 断言对齐；member 无 project:list → 列表 403 断言对齐
- **CI Defect**：secrets-scan 白名单遗漏 .env.example（占位值误报）→ 补充

## 4. Round 2（修复后）

全部 PASS：security suite 47+7 / pytest full 115 / Playwright full 24 / mvn 311 / Vitest 136 / lint / build / secrets scan PASS / compose config PASS / Docker 部署回归（docker-deploy 3 + pytest 13 对 Docker 后端）。

## 5. 数据生命周期收敛（本阶段核心工程产出）

- conftest 跨框架兜底清理（组织级联）光标作用域修正，终态自然归零
- test_idor sec_env 登记 cleanup_orgs（org 级联回收）
- test_input _seed_project 登记 cleanup_orgs
- security suite 遍历用例上传后清理附件（MinIO 对象不随 org 级联）
- 验证：连续两轮 full pytest 后终态 users=2、orgs=0、projects=0、issues=0、notifications=0、audit=0

## 6. Product Defects

0 个（Phase 15 已清零；本阶段 CI 验证未引入/未发现新产品缺陷）。

## 7. Test Defects

9 个（全部修复）：Round 1 的 4+3 项（见 §3）+ Round 2 收敛期 2 项（光标作用域/全局清理位置）。

## 8. CI Defects

1 个：initial workflow testDir/服务端口对齐问题在本地等价验证阶段发现并修正（YAML 校验纳入流程）。

## 9. Environment Issues

1 个：npm ci EPERM（node 残留进程锁定原生模块）——环境问题，终止进程后恢复。

## 10. Gate checklist

[x] workflow 存在｜[x] YAML valid｜[x] frontend lint/Vitest/build｜[x] Maven 311｜[x] pytest 115｜[x] Playwright 24｜[x] security PASS｜[x] Docker build PASS｜[x] compose config PASS｜[x] CI 独立依赖｜[x] Flyway｜[x] Seed｜[x] Healthcheck｜[x] artifacts｜[x] 无 secret 泄漏｜[x] cleanup｜[x] Round 1｜[x] Round 2｜[x] docs（ci-cd.md/phase17-gate.md）｜[x] git clean

## 11. Known Limitations

- 仓库无 remote：workflow 已提交，推送 GitHub 后需在 Actions 页启用（无需额外 secrets）
- playwright baseURL 经 PLAYWRIGHT_BASEURL 环境变量覆盖（Docker 部署回归入口）
- JMeter 性能测试不入 CI（按 Phase 14 手动执行策略）

## 12. Final

# Phase 17 PASS ✅
