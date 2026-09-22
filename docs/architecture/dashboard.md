# Dashboard 统计架构（Phase 10; ADR-020）

## 原则

- 全部指标由**后端从真实业务表聚合**（projects/issues），前端只渲染；无随机增长率/假 KPI/假实时数据。
- 数据范围收敛：ADMIN 角色=全系统；其他授权用户=仅其 `project_members` 成员项目（scope 下推 `WHERE project_id IN`，空成员范围直接零返回）。

## API

GET `/api/v1/dashboard/overview`（dashboard:view，V14 种子仅 ADMIN 绑定）一次返回：

| 区块 | 字段 | 数据源 |
|---|---|---|
| projects | total / active / archived | projects 表 COUNT（ARCHIVED 状态区分） |
| issues | total / byStatus / byType / byPriority / bySeverity / bugCount | issues 表 GROUP BY 聚合（无数据的维度不出现在 Map） |
| createdTrend | 近 14 天按天创建数（无数据日补零） | issues.created_at 按天聚合 |

**不提供 resolved 趋势**：issues 表无"解决时刻"时间戳（updated_at 是最后修改），拒绝造假数据（ADR-020）。

## SQL/性能审查

- 聚合全部为 `COUNT + GROUP BY` 下推数据库，无全表捞取内存计算，无 N+1。
- 命中索引：issues 项目维度走 `idx_issues_project_status`/`uk_issues_project_no` 前缀；趋势按 `created_at` 范围过滤（`idx_audit_created` 同类策略已在 audit_logs 建索引；issues 表当前数据量小，全量按天聚合可接受——Phase 14 性能测试若成瓶颈再补 `idx_issues_created_at`，已记录为已知限制）。
- 空 scope（非 ADMIN 且无成员项目）短路返回零统计，不发 SQL。

## 前端

DashboardView（/dashboard）：加载数据统计按钮（dashboard:view 权限 UX）→ 指标卡片 ×4（项目总数/活跃项目/Issue 总数/BUG 数量）+ ECharts 状态分布饼图 + 近 14 天创建趋势折线图；loading/error/empty 状态齐备；数字全部来自 /dashboard/overview。
