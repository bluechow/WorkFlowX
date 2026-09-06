# WorkFlowX 任务清单（AI_TASKS）

| 项 | 值 |
|---|---|
| Version | 1.0 |
| 定位 | 当前任务清单的**唯一事实来源** |
| 关联规则 | 任务完成标准见 `AI_MASTER_PROMPT.md` §28，状态流转约束见 `AI_WORKFLOW.md` |

---

## 1. 任务维护规则

1. 任务状态：`TODO` / `DOING` / `BLOCKED` / `DONE`
2. 每个时刻**至多一个** `DOING` 任务
3. `BLOCKED` 必须注明原因（通常对应 `AI_WORKFLOW.md` 的 STOP 条件 S1–S7）
4. 任务标记 `DONE` 必须满足 Master Prompt §28 Definition of Done；未满足的只能保持 `DOING`
5. 任务完成时同步更新 `AI_CONTEXT.md`
6. 禁止删除历史任务记录：完成的任务保留在对应 Phase 清单中，作为项目演进痕迹

---

## 2. Phase 0 — Project Governance（已完成 ✅）

- **目标**：建立可约束整个项目生命周期的 AI 总控体系
- **输入**：用户提供的 Master Prompt 与治理层设计
- **验收标准**：总控文件齐备、AGENTS.md 入口生效、Git 仓库初始化完成

| # | 任务 | 状态 | 备注 |
|---|---|---|---|
| 0-1 | 编写 `AI_MASTER_PROMPT.md`（永久规则） | ✅ DONE | 2026-09-06 |
| 0-2 | 编写 `AI_WORKFLOW.md`（工作流 + STOP 条件 + 自主边界） | ✅ DONE | 2026-09-06 |
| 0-3 | 编写 `AI_CONTEXT.md`（项目状态快照） | ✅ DONE | 2026-09-06 |
| 0-4 | 编写 `AI_TASKS.md`（本文件） | ✅ DONE | 2026-09-06 |
| 0-5 | 编写 `AI_DECISIONS.md`（初始 ADR） | ✅ DONE | 2026-09-06 |
| 0-6 | 编写 `AGENTS.md` 入口 + `.gitignore` | ✅ DONE | 2026-09-06 |
| 0-7 | 初始化 Git 仓库并完成首次提交 | ✅ DONE | 2026-09-06 |

---

## 3. Phase 1 — Project Foundation（下一阶段）

- **目标**：搭建可运行的项目骨架与本地开发环境
- **输入**：Phase 0 的总控体系、Master Prompt §3/§4 技术栈与架构约定
- **验收标准**：前后端骨架可启动、Docker 基础设施可用、数据库设计初稿评审通过、README 完整

> 以下为初步任务，进入 Phase 1 时需细化并逐项补充验收标准。

| # | 任务 | 状态 |
|---|---|---|
| 1-1 | 建立仓库目录结构（backend/ frontend/ tests/ docs/ deploy/） | ⬜ TODO |
| 1-2 | 建立 docs/ 文档树（requirements/ architecture/ database/ api/ development/ testing/ deployment/） | ⬜ TODO |
| 1-3 | 后端 Spring Boot 骨架（统一响应 / 全局异常 / 模块分包骨架） | ⬜ TODO |
| 1-4 | 前端 Vue 3 + TypeScript 骨架 | ⬜ TODO |
| 1-5 | Docker Compose 本地环境（MySQL / Redis / MinIO） | ⬜ TODO |
| 1-6 | 数据库设计初稿（ER 模型 + 迁移脚本机制） | ⬜ TODO |
| 1-7 | README.md | ⬜ TODO |

---

## 4. Phase 2–19 里程碑概览

| Phase | 名称 | 核心产出 |
|---|---|---|
| 2 | Authentication & User | 登录 / 登出 / JWT / 用户管理 API |
| 3 | RBAC | 用户-角色-权限模型与后端强制鉴权 |
| 4 | Organization | 组织管理 |
| 5 | Project Management | 项目管理 |
| 6 | Issue Management | Issue 全生命周期 |
| 7 | Workflow | Issue 状态机与状态转换约束 |
| 8 | Comment & Attachment | 评论与附件（MinIO） |
| 9 | Notification | 通知 |
| 10 | Audit & Dashboard | 审计日志与数据统计 |
| 11 | Frontend Completion | 前端完整可用 |
| 12 | API Automation | Pytest API 自动化测试体系 |
| 13 | UI Automation | Playwright UI 自动化 |
| 14 | Performance Testing | JMeter 性能测试与数据报告 |
| 15 | Security Testing | 安全测试 |
| 16 | Docker & Deployment | 完整 Docker 部署方案 |
| 17 | CI/CD | GitHub Actions 流水线 |
| 18 | Final QA | 全面回归验证 |
| 19 | Final Delivery | 最终交付 |

> 每个 Phase 开始前，必须在第 2/3 节同位置建立该阶段的详细任务清单（目标 / 输入 / 任务 / 输出 / 验收标准）。
