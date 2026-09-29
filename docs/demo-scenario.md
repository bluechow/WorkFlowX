# 演示路径（5~10 分钟）

> 版本注记：v2.0.0 起演示入口为 /workspace（工作台）与项目空间页签（概览/工作项/看板/计划/动态/测试）；演示数据已含标签/截止/关联/里程碑/活动/@提及样本。

> 全程真实系统演示；前置：deploy 全栈已启动（`docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d`），访问 `http://localhost:${FRONTEND_PORT}`。

## 演示脚本

| 步骤 | 操作 | 展示点 |
|---|---|---|
| 1 | 登录 admin | JWT 认证、统一错误处理 |
| 2 | Dashboard → 加载数据统计 | 真实聚合、ECharts 图表 |
| 3 | 新建组织 | 表单校验、成功反馈 |
| 4 | 进入组织详情 → 新建根部门/子部门 | 部门树、OWNER 行移除禁用 |
| 5 | 新建项目 | key 规则、成员自动 OWNER |
| 6 | 项目成员 → 添加 user1 | 组织成员前置校验（ADR-015） |
| 7 | Issues → 新建 Issue（BUG，分派 user1） | 编号生成、severity 仅 BUG、通知触发 |
| 8 | 状态下拉 OPEN→IN_PROGRESS | 状态机约束（非法项不出现） |
| 9 | 打开 Issue 抽屉 → 发表评论 | author 绑定、实时刷新 |
| 10 | 上传附件（txt）→ 下载 | MinIO 存储下载一致 |
| 11 | 切换 user1 登录 | 通知 badge（分派/状态/评论 3 条）、菜单权限差异 |
| 12 | user1 直访 /system/audit | 权限提示而非空白（后端强制） |
| 13 | 切回 admin → 审计日志 | 全操作留痕、traceId |
| 14 | Dashboard | 数据随演示变化 |

## 测试能力展示（演示后补充）

- `pytest -m security`（54 安全用例）、`npx playwright test`（25 UI 用例）
- `mvn test`（311 后端用例）
- `bash scripts/ci/secrets-scan.sh`（安全扫描）
- docs/testing/ 各 Gate 记录（phase2~18）

## 工程能力展示

- `deploy/docker-compose.yml`（5 容器编排、healthcheck 链）
- `scripts/backup/backup.sh` + `restore.sh`（备份恢复）
- `.github/workflows/ci.yml`（CI 流水线定义）
