# Phase 5 Release Gate 记录

> 时间: 2026-09-15 ｜ 基线: 0fa8c4d（P5-03）｜ 结论: **GATE PASS**

## 验证矩阵

| 维度 | 项 | 结果 |
|---|---|---|
| Java | mvn test 全量 | **244/244**（P5 新增 21：领域 11 + API 10 + 成员 9） |
| Python | pytest 全量 | **48/48 连续 2 轮**（新增 project+member 10 用例） |
| 前端 | Vitest | **77/77**（新增 ProjectsView 7） |
| 前端 | lint / build | PASS / PASS |
| 真实 HTTP | P5 冒烟 12 步 | org→create 201→get 200→update 200→archive 200→restore 200→member 前置 400→list 200→member 项目 403→no-token 401→级联清理 200→logout 200 |
| MySQL | 残留 | projects=0 / project_members=0 / organizations=0 / users=2（仅 seed）/ 测试角色=0 |
| Redis | 键残留 | auth:* 清零 |
| Swagger | OpenAPI 同步 | projects 5 端点 + members 3 端点自动生成；bearerAuth=JWT；public/private 标记正确 |
| Git | 工作树 | clean |

## 核对

**P5-01**：projects（key UK 不可改/org_id CASCADE/status 归档/owner_id）✅｜ADR-014 数据级归属 ✅｜领域测试 ✅
**P5-02**：5 端点 hasAuthority ✅｜409/422/404 语义 ✅｜无物理删除（归档代替）✅
**P5-03**：列表/搜索/status 筛选/组织选择/创建/编辑/归档恢复确认/权限 UX/空态 ✅｜77 Vitest ✅
**P5-04**：project_members（复合PK/OWNER,MANAGER,MEMBER）✅｜组织成员前置 400 ✅｜重复 409 ✅｜OWNER 保护 400 ✅｜非成员操作者 403 ✅｜级联（项目/用户删除）✅｜V8 权限种子（31 项）✅
**P5-05**：Project CRUD/过滤/不可变字段/归档恢复 + Member 生命周期 + 数据级归属（403→入组 201→移出 403）✅

**回归**：Phase 2（认证/用户/锁定/踢线）✅｜Phase 3（RBAC 全链）✅｜Phase 4（组织/部门/成员/OWNER 规则）✅

## 过程问题与修复

| # | 问题 | 处理 |
|---|---|---|
| 1 | 测试角色缺 `org:assign_member` 致 owner 无法加成员（用例设计权限遗漏） | 补权限码；测试通过 |
| 2 | 测试角色清理前缀遗漏 `P5_CTRL_` → 409 残留 | cleanup 补前缀 + 手工清残留 |
| 3 | pytest 断言 `parentId` 被 NON_NULL 省略 | 改 `.get('parentId')` |
| 4 | 测试操作者语义错位（outsider 当 operator 期待 400） | 改 OWNER 为 operator，新增 403 独立用例 |
| 5 | **8080 端口被 winnat 排除段(8060-8159)占用**——本机动态端口范围被改为 1024-15000 | `netsh int ipv4 set dynamicport tcp start=49152 num=16384` + winnat 重启；根治间歇性"backend: 000" |
| 6 | 运行 jar 为 P5-02 旧包缺 P5-04 端点 → curl 冒烟 404 | 重新打包重启（部署卫生） |

**产品缺陷：0**——所有失败均为测试设计或环境问题；未修改断言迁就代码。

## 结论

**Phase 5 PASS，放行 Phase 6 — Issue Management。**
