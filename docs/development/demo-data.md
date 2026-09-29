# 演示数据包（Phase A-①，ADR-024）

一条开关让系统开箱即有真实体量的数据，解决"每个模块点进去空荡荡"的问题，同时为
日常演示、教学与测试活动提供数据底座（测试者面对的应当是"有数据的项目"）。

## 开启方式（幂等，可常开）

| 环境 | 方法 |
|---|---|
| 本地开发 | `DEMO_SEED=true mvn spring-boot:run`（backend 目录） |
| Docker 部署栈 | `deploy/.env` 里 `DEMO_SEED=true` 后 `docker compose up -d backend` |

重复启动不会重复生成：以 `demo_admin` 用户存在为标记，检测到即跳过。生成过程包在
事务里，中途失败整体回滚，不会留半截数据。

## 生成内容清单

| 类别 | 数量 | 说明 |
|---|---|---|
| 用户 | 30 | `demo_admin`（ADMIN）、`demo_pm`、`demo_u01`~`demo_u28`，统一密码 `Demo@123456` |
| 组织 | 3 | 星辰科技（STARLIGHT）/ 云帆信息（YUNFAN）/ 蓝鲸工作室（BLUEWHALE） |
| 部门 | 5 | 星辰 4 个（研发/测试/产品/设计）+ 云帆 1 个 |
| 项目 | 4 | CAMPUS 智慧校园 / SHOP 云帆电商 / MBANK 移动银行 / DEVOPS DevOps 平台 |
| Issue | 48 | 六种状态全覆盖（含 REOPENED/CLOSED），21 个 BUG 带严重度，时间分布在近 60 天 |
| 评论 | ~20 | 分布在 1/3 的 Issue 上 |
| 附件 | 4 | 直传 MinIO（复现步骤/接口响应/压测记录/乱码说明），挂在 3 个 Bug 上 |
| 通知 | 12 | ISSUE_ASSIGNED，半数已读 |
| 审计日志 | 12 | LOGIN/CREATE 样例（user_agent=demo-seeder/1.0，便于识别） |
| 用例库 | 36 用例 / 10 目录 | CAMPUS 14、SHOP 12、MBANK 10 |
| 测试计划 | 2 | MBANK V2.3 回归（RUNNING：5 PASS/3 FAIL/1 BLOCKED/1 PENDING，FAIL 关联 Bug）；智慧校园冒烟（COMPLETED：3 PASS/1 FAIL 关联 Bug） |
| 标签（V17） | 16 | 每项目 4 个语义化标签（核心链路/体验优化/技术债/安全），每条工作项 1~2 个 |
| 截止日期（V17） | ~30 | 未完结工作项的 2/3 带截止（18:00），约 1/4 已逾期（界面红色警示） |
| 关联（V17） | 8 | 每项目 2 条（第 1 条 BLOCKS 第 2 条 + 第 3 条 RELATES 第 4 条） |

一致性保证：Issue/用例编号连续且与 `issue_seq`/`testcase_seq` 计数器同步；
assignee 均为项目成员；测试计划 FAIL 项关联同项目 Bug（满足服务层约束）。

## 演示账号

| 账号 | 密码 | 角色 |
|---|---|---|
| `demo_admin` | `Demo@123456` | ADMIN（全部系统菜单） |
| `demo_pm` / `demo_u01`~`u28` | `Demo@123456` | MEMBER |

种子账号 `admin`（ADMIN）/ `user1`（MEMBER）不受影响，两套账号并存。

## 重置（如需重建演示数据）

```sql
-- 在 MySQL 中按顺序执行（外键安全顺序）
DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'demo\\_%');
DELETE FROM users WHERE username LIKE 'demo\\_%';
DELETE FROM notifications WHERE recipient_id IN (SELECT id FROM users WHERE username LIKE 'demo\\_%');
DELETE FROM audit_logs WHERE user_agent = 'demo-seeder/1.0';
DELETE FROM organizations WHERE code IN ('STARLIGHT','YUNFAN','BLUEWHALE');
-- 之后重启（DEMO_SEED=true）即可重新生成；MinIO 中可能残留少量孤立演示附件对象，可忽略
```

## 实现位置

- `com.workflowx.demo.DemoDataSeeder`：生成逻辑（JdbcTemplate 直插，列结构对齐 V1~V16，固定随机种子可复现）
- `com.workflowx.demo.DemoDataRunner`：`demo.seed=true` 时的启动钩子
- 集成测试：`DemoDataSeederTest`（数据完整性 + 幂等性）
