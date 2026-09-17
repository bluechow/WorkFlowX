# WorkFlowX Issue Workflow 设计（Phase 7）

> 任务 P7-01 ｜ ADR-017 ｜ 上位依据: Master Prompt §14/§15（示例主链与 REOPENED 回路）

## 1. 正式状态转换矩阵（ADR-017）

```text
OPEN ──→ IN_PROGRESS ──→ RESOLVED ──→ TESTING ──→ CLOSED
                ↑                        │
                └────── REOPENED ←───────┘
```

| from | 允许的 to |
|---|---|
| OPEN | IN_PROGRESS |
| IN_PROGRESS | RESOLVED |
| RESOLVED | TESTING |
| TESTING | CLOSED, REOPENED |
| REOPENED | IN_PROGRESS |
| CLOSED | （无出边） |

## 2. 决策点落定（ADR-017）

| 决策点 | 结论 |
|---|---|
| 终态 | **CLOSED** 为唯一终态（无出边）；其余均为非终态 |
| REOPENED 回路 | TESTING 测试失败 → REOPENED；REOPENED 只能进 IN_PROGRESS 重新进入工作流 |
| 跳过中间状态 | **禁止**——严格主链逐步流转（保守矩阵，防误操作与统计失真） |
| 相同状态重复 transition | **禁止** → 409 Conflict |
| 非法 transition HTTP 状态 | **409 Conflict**（业务状态冲突），message 携带当前状态与允许目标 |
| 附加信息 | **不新增** reason/comment 等字段（Phase 8 Comment 承载）；仅 targetStatus |
| 并发 | 条件 UPDATE（WHERE status = fromStatus）乐观并发；失败 → 409，不做最后写入覆盖 |
| 数据级 | 操作者须为**项目成员**（沿 ADR-016.4）+ issue:transition authority 双层 |

## 3. 权限

- 新增 `issue:transition`（V11 种子，ADMIN 绑定；系统权限 36→37）
- `PATCH /projects/{projectId}/issues/{issueId}/status` 从 issue:update **迁移**为 issue:transition（变更记录于 ADR-017；旧权限码 issue:update 保留用于其余字段更新）

## 4. 边界

- Comment/Attachment/Notification/Audit 均不在本阶段（transition 不写审计表、不发通知）
- reason/comment 字段不提前引入
