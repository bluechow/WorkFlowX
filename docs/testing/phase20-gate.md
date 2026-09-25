# Phase 20 Release Gate 记录 — 测试用例库（V1.1 起步）

> 时间: 2026-09-25 ｜ 基线: 723e79f（V1.0.0 hotfix）→ 本 Gate 提交｜ 结论: **GATE PASS**

## 1. Scope

P20-01 影响分析+ADR-022 → P20-02 V15 迁移 → P20-03 权限 54 项 → P20-04 双 Service → P20-05 Controllers → P20-06 Java 集成测试 → P20-07 pytest → P20-08~09 前端 → P20-10 Gate。

## 2. Feature（ADR-022 设计决策）

- **目录树**: 自引用 test_case_directories（parentId，NULL=根）；删除级联子目录，目录内用例退回未分类（SET NULL，不丢数据）；移动防环（祖先遍历）
- **用例**: test_cases（编号=projects.testcase_seq 行锁递增，ADR-016 同方案；uk(project_id, testcase_no)）；字段 title/preconditions/steps/expected/caseType(FUNCTIONAL|REGRESSION|SMOKE|SECURITY|PERFORMANCE)/priority(LOW~CRITICAL)/status(DRAFT→ACTIVE→DEPRECATED，创建禁 DEPRECATED)
- **权限三层**: testcase:list/get/create/update/delete（5 项，49→54）；读写均要求项目成员（用例库为项目内部资产，强于 Issue 读仅 authority——ADR-022 明示）；成员校验先于目标查找

## 3. V15 Migration

ALTER projects ADD testcase_seq；CREATE test_case_directories + test_cases（外键策略如上）；5 权限种子+ADMIN 绑定。Flyway 自动执行验证 ✅。

## 4. Backend（13 端点）

目录 list/create/update/delete + 用例 page/create/get/update/delete；Swagger 完整。

## 5. Tests（Round 1 发现问题→修复→Round 2 全绿）

| 套件 | Round 1 | 修复 | Round 2 |
|---|---|---|---|
| Java 集成（新增 TestCaseServiceIntegrationTest） | 13 用例：9 errors（testcase_seq SELECT 误用 @Update 致返回值错乱）+2 failures（VO 镜像枚举与 entity 枚举不等价——过度设计） | @Select 修正；VO 直接复用 entity 枚举 | **13/13** |
| pytest（新增 test_testcase_api.py） | 8 用例：member 会话污染（重登 seed user1 顶掉 session token——P17 教训复发）+ NON_NULL 断言 | 动态用户+角色授予；.get() 断言 | **8/8（全量 123/123）** |
| Vitest（新增 TestCasesView.spec） | 6 用例：vue-router 未 mock | 补 mock | **6/6（全量 142/142）** |
| Playwright（新增 testcases.spec） | 2/2（动态 ADMIN 用户） | — | **PASS ×2** |
| mvn 全量 | — | — | **324/324 ×2** |
| lint/build | PASS | — | PASS |

## 6. Frontend

TestCasesView（左目录树 el-tree + 右用例表格 + 目录/用例对话框 + 过滤分页）；路由 /system/projects/:projectId/testcases；ProjectsView 行入口「用例库」（testcase:list gating）；api/testcase.ts（正确解包 .data.data——P11 教训已内化）。

## 7. 终态（实测）

users=2｜user_roles=2（seed）｜roles=2（ADMIN/MEMBER）｜permissions=54｜orgs/projects/issues/test_cases/dirs=0｜notifications=0｜audit=0｜Redis auth:*=0｜MinIO=0。

## 8. Defects

- Product Defects: 0
- Test Defects: 3（Round1 发现全修复，见 §5）
- Environment Issues: 1（WSL 重启竞态——keepalive+healthy 等待处理）

## 9. Known Limitations

- 目录过滤为精确匹配（子树聚合过滤留 V1.1 后续）
- 用例步骤为文本（非结构化步骤表）
- 用例与 Issue/Bug 关联属 V1.1 后续阶段（roadmap）

## 10. Final commit

见 git log；本 Gate 提交为准。
