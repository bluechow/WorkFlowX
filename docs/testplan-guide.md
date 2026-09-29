# WorkFlowX 测试计划执行操作手册

> 版本注记：v2.0.0 Final Edition 起计划页位于项目空间「计划」页签（里程碑），测试计划在「测试」页签。

## 前置

- Docker 部署运行中（或开发模式 backend:8080 + frontend:5173）
- admin 账号登录

## 操作流程

1. **建用例**：项目管理 → 目标项目 → 用例库 → 新建根目录/子目录 → 新建用例（标题/前置/步骤/预期/类型/优先级）
2. **建计划**：项目管理 → 测试计划 → 新建计划（输入名称，创建后自动进入执行页）
3. **挑用例**：执行页 → 添加用例 → 从用例库勾选（已在本计划内的自动隐藏）
4. **执行**：每条用例点 PASS / FAIL / BLOCKED；FAIL 弹窗填备注（可填 Issue ID 关联 Bug）；首次执行自动 NOT_STARTED→RUNNING
5. **看进度**：顶部统计卡（总/通过/失败/阻塞/待执行 + 进度条），实时刷新
6. **收尾**：全部执行后手动把状态改为 COMPLETED（完成后计划只读）
7. **Bug 跟踪**：FAIL 行显示关联的 Issue ID；到 Issue 模块跟踪修复

## API 速查

```
GET    /api/v1/projects/{pid}/testplans                     # 计划分页（含统计）
POST   /api/v1/projects/{pid}/testplans                     # 创建 {"name":"..."}
GET    /api/v1/projects/{pid}/testplans/{id}                # 详情（含统计）
PUT    /api/v1/projects/{pid}/testplans/{id}                # 更新 {"name":"...","status":"RUNNING"}
DELETE /api/v1/projects/{pid}/testplans/{id}                # 删除（条目级联）
GET    /api/v1/projects/{pid}/testplans/{id}/items          # 条目列表
POST   /api/v1/projects/{pid}/testplans/{id}/items          # 添加 {"caseIds":[1,2]}
DELETE /api/v1/projects/{pid}/testplans/{id}/items/{iid}    # 移除
PUT    /api/v1/projects/{pid}/testplans/{id}/items/{iid}/execute  # 执行 {"result":"FAIL","note":"...","issueId":3}
```

## 权限

testplan:list/get/create/update/delete（ADMIN 默认全有）；数据级=项目成员（读写均要求，非成员 403）。
