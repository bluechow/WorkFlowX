# Release Candidate 双轮验证记录（Phase 19; P19-30）

## Round 1

| 项 | 结果 | 说明 |
|---|---|---|
| RC 审计（git/HEAD/clean/敏感文件） | PASS | 无 untracked/临时/敏感文件；secrets scan PASS |
| secrets scan（Python 版） | PASS | 重写后性能稳定 |
| workflowx-release fresh deployment | PASS（修复后） | Round1 初次失败：容器名固定与旧 deploy 栈冲突 → down -v 后以独立 project 重启；5 容器 healthy |
| Flyway V1~V14 | PASS | history 14 行全 success |
| Seed | PASS | admin/user1 ACTIVE |
| Smoke 15 断言 | PASS | 全真实 HTTP 经 Nginx 反代 |
| 全量 pytest 对 release 部署栈 | 部分阻塞 | 部署栈 DB 不暴露宿主端口+部分用例直连 8080 → 分类为测试设计边界（记录，非产品缺陷）；全量 pytest 对 dev 栈执行 |
| 全量 pytest 对 dev 栈 | 115/115（修复后） | Round1 暴露 conftest 缺陷：游标作用域、组织兜底清光 seed user_roles、非 seed 角色残留 → 修复（角色清理收敛至非 seed） |
| mvn | 311/311 | PASS |
| Vitest | 136/136 | PASS |
| Playwright | 25/25 | PASS（serial ×2） |
| 文档一致性 | 2 项修复 | README（Phase 2 状态过时）、how-to-run-tests（覆盖说明过时） |

## Round 1 发现（分类）

- Test Defect ×3：conftest 兜底清理误清 seed user_roles（后经内联恢复并收敛清理范围）；conftest 组织兜底游标作用域错误；security 用例认证头辅助函数作用域问题
- Test/设计边界 ×1：全量 pytest 对部署栈受限（DB 不暴露）——记录为部署栈测试边界
- Documentation Defect（P18 已修）：README/how-to-run-tests 过时
- Environment Issue ×2：wsl 启动竞态（重启处理）；后端存活窗口与测试竞争

## Round 2

全部 PASS：secrets scan PASS｜workflowx-release 5 容器 healthy｜Flyway 14/14｜smoke 15/15｜mvn 311｜pytest 115（dev 栈，终态归零）｜Vitest 136｜Playwright 25｜lint/build PASS｜终态 users=2、roles=2（seed）、permissions=49、业务数据全 0、Redis auth:*=0、MinIO objects=0。

## 遗留说明

- GitHub hosted CI 执行需先配置 remote（workflow 已就绪）
- 部署栈 MySQL/Redis/MinIO 不暴露宿主端口为安全设计；如需对部署栈跑全量 pytest，可临时映射端口或经由 Nginx 暴露的 API 完成 smoke 层验证
