# Phase 10 Release Gate 记录 — Audit & Dashboard

> 时间: 2026-09-22 ｜ 基线: da4dd09（Phase 9 Gate）→ 101bd0a（缺陷修复）→ 本 Gate 提交｜ 结论: **GATE PASS**

## 1. Scope

P10-01 影响分析（含 §14 遗留决策）→ P10-02~03 Audit 模型/Service → P10-04~05 触发接线与事务策略 → P10-06~07 Audit API/权限/数据范围 → P10-08 issue_status_transitions 最终决策 → P10-09~11 Dashboard 指标/API/数据范围 → P10-12~13 前端 → P10-14~16 三线测试 → P10-17 真实 E2E → P10-18 SQL review → P10-19 文档 → 本 Gate。

## 2. issue_status_transitions 最终决策（历史缺口收口）

**不建表（决策 D）**：状态规则=WorkflowService 内存矩阵（ADR-017）；操作事实=audit_logs（ISSUE/TRANSITION 含 `from -> to` 摘要）；用户告知=notifications。无任何 API/UI 消费领域状态历史，不为"补齐字典"建空表。数据字典 §14 已更新替代说明（🗑️ ADR-020 决策 D）。

## 3. Audit 数据模型（数据字典 §15 权威 + ADR-020 扩展）

`audit_logs`（V14）：user_id（UNSIGNED，匿名/占位归一化 NULL）/ module / action / http_method / uri / ip / target / summary / success / **trace_id** / **user_agent** / **created_at**。索引 ×3（user_time/module/created）。

## 4. AuditService 与事务策略（ADR-020）

`record` 同事务（REQUIRED，业务回滚不留"成功"审计——Java 事务模板测试实证）；`recordStandalone` REQUIRES_NEW（登录失败事实幸存——Java/Python 双实证）。**接线 16 点**：AUTH×3 / USER×2 / ORG×2 / PROJECT×3 / ISSUE×2 / COMMENT×2 / ATTACHMENT×2（读操作不记）。审计失败上抛不吞。

## 5. Audit API + 权限 + 数据范围

GET /api/v1/audit-logs（audit:list）+ GET /{id}（audit:get）；多条件筛选（operator/module/action/success/target/traceId/时间范围）+ created_at DESC,id DESC 稳定排序 + 分页（size≤100）。**权限即数据范围**：audit_logs 为全系统高敏感资源，V14 种子仅 ADMIN 绑定（46→49）；普通角色一律 403——不存在"改 ID 读他人审计"面。

## 6. 敏感数据保护（实测）

密码（WrongPass@123）/password 字段名/Bearer 头绝不出现在审计任何字段（Java 断言 + Python 黑盒守护）；登录失败摘要为固定文案不含用户输入；上传审计仅记文件名+字节数不含内容。

## 7. Dashboard 指标 / API / 数据范围

GET /dashboard/overview（dashboard:view）一次返回：项目（total/active/archived）、Issue（total/四维分布/bugCount）、近 14 天创建趋势（补零）。**不提供 resolved 趋势**（无可靠时间戳，拒绝造假）。数据范围：ADMIN=全系统；其他授权用户=仅成员项目（scope 下推 SQL，空成员短路零返回——Java 隔离测试实证 stranger=0）。

## 8. 前端

DashboardView 统计扩展（指标卡片 ×4 + ECharts 状态饼图 + 趋势折线，dashboard:view 权限 UX 按钮触发，loading/error/empty 齐备）+ AuditView（/system/audit：模块/操作/结果/时间范围筛选 + 分页 + 详情 + **无权限显示明确提示而非空白页**）+ 菜单入口（audit:list gating）。全部数字来自真实 API，零静态假数据。

## 9. 三线测试（关键测试连续两轮）

| 线 | 第一轮 | 第二轮 |
|---|---|---|
| Java mvn（真实 MySQL） | 311/311 | **311/311** |
| Python pytest（真实 HTTP） | 97/97（新增 audit 5 + dashboard 3） | **97/97** |
| Vitest | 128/128（新增 StatsSection 3 + AuditView 5） | **128/128** |
| lint / build | PASS / PASS | PASS / PASS |

## 10. 真实浏览器 E2E（两轮）

第一轮：admin 登录 → Dashboard 加载统计（**Issue=1 与 DB 一致** + 2 canvas 图表）→ API 触发真实业务链（组织/项目/成员/Issue/流转/评论）→ **审计表 7 条与业务动作一一对应** → AuditView 渲染（TRANSITION/LOGIN/traceId 全部可见）→ user1 登录：**无审计菜单 + 直访 /system/audit 显示权限提示**。
第二轮（缺陷修复后复验）：重新触发业务 → Dashboard 2 canvas + 审计含二轮事实（创建项目 P10R2/创建 Issue）。

## 11. SQL / Index review

聚合全部 COUNT+GROUP BY 下推数据库；无 N+1、无内存分页；命中 idx_audit_user_time/idx_audit_module/idx_audit_created（audit 查询）与既有 issues 索引（项目维度）；趋势 created_at 范围查询在当前数据量下可接受——`idx_issues_created_at` 记录为 Phase 14 待评估项。空 scope 短路零 SQL（修复 IN () 非法语法）。

## 12. 发现并修复的真实产品缺陷（2 个）

1. **审计 user_id 越界**：既有测试/调用方以 `-1L` 作系统操作者占位 → 写入 `BIGINT UNSIGNED` 列 Data truncation。修复：AuditServiceImpl 归一化（null/≤0 一律存 NULL，语义=非真实用户）。
2. **并发登录失败连接池死锁**：login 的 @Transactional + LOGIN_FAIL 审计 recordStandalone(REQUIRES_NEW) 在并发失败路径下每线程双连接需求（外层挂起+新事务）耗尽 Hikari 池（active=10 waiting=7，30s 超时）。修复：login 移除 @Transactional（仅单条 update+Redis，无多表原子性需求）；LOGIN_FAIL 无外层事务时独立提交，失败事实依然幸存。

第一轮按 Gate 规则未宣布 PASS，完成根因分析（产品缺陷分类）→ 修复 → 完整回归通过。

## 13. 测试侧问题（已修）

审计全局计数断言受历史运行审计累积污染（success=false 全局 108 条）→ 改 operator 维度精确断言；Dashboard projects 表 scope 列名误用 project_id→id；空成员范围 IN () 语法错误→短路返回；pytest 新增测试重登 seed 用户顶掉 conftest 单会话 token→改独立工厂用户。

## 14. Known limitations

- issues 表无 created_at 索引（数据量小可接受，Phase 14 评估）
- 审计无导出/保留策略（后续演进）
- Dashboard 无 resolved 趋势（待状态历史类需求出现，可与 §14 决策一起重评估）
- ISSUE TRANSITION 审计不含 issue 标题快照（摘要含状态对；可按需扩展）

## 15. Git commits

`53e0921` P10-02~10 后端 → `3c05ae4` Java 12 测试 → Python 8 测试 → `305fc94` P10-12~13 前端 → `b2d89d3` 类型修复 → `101bd0a` **两真实缺陷修复** → 本 Gate 提交

## 16. Working tree / 终态

clean @ 本 Gate 提交。终态实测：audit_logs=0 / notifications=0 / users=2 / orgs=0 / projects=0 / issues=0 / comments=0 / attachments=0 / Redis auth:*=0 / MinIO objects=0。

## 17. DoD / Gate checklist

[x] §14 缺口决策（D，字典已更新）｜[x] Audit 模型｜[x] AuditService（双事务语义）｜[x] Audit API｜[x] Audit 权限（仅 ADMIN）｜[x] 数据范围（权限即范围）｜[x] 敏感数据保护（测试守护）｜[x] 事务一致性（回滚实证）｜[x] 关键业务 Audit 16 点｜[x] Dashboard API｜[x] Dashboard 数据范围（隔离实证）｜[x] Dashboard 前端｜[x] Audit 前端｜[x] Java tests｜[x] Python tests｜[x] Vitest｜[x] lint｜[x] build｜[x] 真实 E2E ×2｜[x] SQL/index review｜[x] documentation（audit.md/dashboard.md/字典）｜[x] ADR-020｜[x] working tree clean｜[x] 关键测试连续两轮（311/97/128）

## 18. Final

# Phase 10 PASS ✅
