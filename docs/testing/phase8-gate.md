# Phase 8 Release Gate 记录 — Comment & Attachment

> 时间: 2026-09-22 ｜ 基线: 1fc138a（Phase 7 Gate）→ 02087a4（P8-15 类型修复）｜ 结论: **GATE PASS**

## 1. Scope

P8-01 影响分析 → P8-02~05 Comment（模型/Service/API/RBAC）→ P8-06~08 Attachment（模型/StorageService/安全规则）→ P8-09~11 上传/下载/删除 API 与一致性 → P8-12 Java 集成测试 → P8-13 Python 黑盒 → P8-14/15 前端 → P8-16 真实文件 E2E → P8-17 本 Gate。

## 2. Comment model（数据字典 §11）

`issue_comments`: id / issue_id (FK CASCADE) / author_id (索引) / content TEXT / created_at / updated_at；`idx_comments_issue(issue_id, created_at)`。author 服务端绑定操作者（DTO 无 authorId 字段，不可伪造）；content 业务上限 10000 字符（ADR-018）。

## 3. Attachment model（数据字典 §12）

`attachments`: id / issue_id (FK CASCADE) / object_key VARCHAR(200) UNIQUE / file_name / file_size / content_type / uploader_id / created_at；`idx_attachments_issue`。**库内仅元数据，文件二进制存 MinIO**（零 BLOB 入库）。

## 4. MinIO strategy

复用 compose.yaml 既有 `minio`(9000/9001) + `minio-init` 建的 `workflowx` bucket（凭据 .env 注入；dev profile 默认值对齐 MYSQL 先例；prod :? required）；`StorageService`(common.storage) 只管字节流，不感知业务；MinioClient 启动时 fail-fast 校验 bucket；objectKey 服务端生成 `issues/{issueId}/{uuid}-{safeName}`。

## 5. File security policy（ADR-018）

扩展名白名单 12 类（不信任 Content-Type）｜10MB 可配上限（413）｜白名单外/空文件/无扩展名/非法文件名 422｜文件名去路径分量+控制字符+限长｜multipart 11MB Spring 层兜底（MaxUploadSizeExceededException → 413）｜明令禁止 exe/dll/bat/cmd/sh/js/html/svg。

## 6. RBAC

V12 种子 9 项权限（comment×5 + attachment×4），系统权限 37→**46**（RbacConstants 守护测试同步）；Controller 全端点 hasAuthority。

## 7. Data-level authorization

读/写均要求**项目成员**（403）；编辑/删除第三层 **ownership**：仅作者/上传者本人，ADMIN 非 owner 同样 403（Java 集成 + Python 黑盒双验证，ADR-018）。

## 8. API（9 端点）

GET/POST `/projects/{projectId}/issues/{issueId}/comments`；GET/PUT/DELETE `…/comments/{commentId}`；GET/POST `…/attachments`；GET `…/attachments/{id}/download`；DELETE `…/attachments/{id}`。资源边界 projectId+issueId+commentId/attachmentId 由 Service 校验，跨项目/跨 Issue 拼接一律 404。Swagger 同步（@Tag/@SecurityRequirement）。

## 9. Java tests

**287/287 BUILD SUCCESS**（新增 CommentServiceIntegrationTest 12 + AttachmentServiceIntegrationTest 11；真实 MySQL + **真实 MinIO**——上传后断言对象存在、下载逐字节比对、删除后对象不存在；白名单/穿越/空文件/10MB+1 全覆盖）。

## 10. Python tests

**81/81 × 2 轮**（新增 test_comment_api.py 10 + test_attachment_api.py 11 真实 HTTP 黑盒；含 ownership/outsider/跨项目拼接/413/白名单/穿越/MinIO 终态=0）。

## 11. Frontend tests

**Vitest 105/105 × 2 轮**（新增 IssueCommentsPanel 8 + IssueAttachmentsPanel 8：加载/empty/error/创建重拉/空内容拦截/ownership 编辑/权限 UX/前端预检拦截）；**lint PASS / build PASS**（修复 Result.data 严格类型）。

前端交付：`api/comment.ts`+`api/attachment.ts`+`IssueCommentsPanel.vue`+`IssueAttachmentsPanel.vue`+IssuesView 评论/附件抽屉（权限 gating、上传进度、下载走鉴权 Blob、成功后一律重新拉取）。

## 12-13. lint / build

eslint 0 error；`vue-tsc -b && vite build` PASS。

## 14. E2E（tests/e2e/phase8_comment_attachment_e2e.sh，20 断言 ALL PASS × 2）

登录→组织→项目→成员→Issue→**Comment 创建(author=1)/编辑/DB 行核验/删除后行消失**→**无 authority 上传 403→授权→上传 201→MinIO 对象存在（mc stat）→元数据行核验（mysql）→下载逐字节一致→删除后对象+行双清→非上传者删除 403**→org 删除+角色回收+**MinIO objects=0**。幂等复跑 ALL PASS。

## 15-17. 清理终态（实测）

| 存储 | 终态 |
|---|---|
| MySQL | users=2 / orgs=0 / projects=0 / issues=0 / **comments=0 / attachments=0** ✅ |
| Redis | auth:* = **0** ✅ |
| MinIO | workflowx/issues/ 测试对象 = **0** ✅ |

Gate 前清理了调试期残留（P7/P8 手工调试遗留的 org 5 个级联数据 + 失效会话键 5 个；均为运行残留而非产品缺陷，已顺带证实 V12 FK 级联与 E2E 级联删除工作正常）。

## 18. Security checks

未认证 401 ✅｜无 authority 403 ✅｜非项目成员 403 ✅｜非 owner 写操作 403 ✅｜跨项目/跨 Issue 拼接 404 ✅｜超限 413（业务+Spring 双层）✅｜白名单外 422 ✅｜path traversal 文件名剥离 ✅｜Content-Type 不作为放行依据 ✅｜objectKey 服务端生成（无用户输入拼路径）✅｜bucket 非公开、前端零对象存储凭据 ✅｜下载永久 URL 不存在（每次鉴权读）✅｜无 BLOB 入库 ✅｜SQL 全参数绑定 ✅｜traceId/无堆栈泄漏 ✅

## 19. Bugs found / fixed（真实缺陷 1 个 + 测试侧问题 4 个）

1. **[产品缺陷] StreamingResponseBody 污染 keep-alive 连接**：下载后复用连接的下一个请求被服务器静默断开（Server disconnected）。修复：改同步 byte[] 响应（10MB 上限内存可控）；Java/Python/E2E 三线回归确认。
2. [测试] application.yml 重复 `spring:` 键（插入 multipart 配置时引入）→ 上下文启动失败；合并修复。
3. [测试] Java 集成测试缺组织成员前置（ADR-015）→ 补 addMember 前置。
4. [测试] E2E 中文 JSON 422（Git Bash curl 老坑）+ System32 curl 不认 MSYS /tmp 路径 → 全 ASCII + 相对路径。
5. [测试] 前端 Result.data 可选类型 build 失败 → 显式 cast。

## 20. Product defects

无未修复产品缺陷。级联删除（issue→comment/attachment）实测正常。

## 21. Known limitations

- org 级联删除**不回收 MinIO 对象**（孤儿对象需按 object_key 前缀清理；ADR-018 已记录，Phase 10 Audit 或运维脚本承接）
- 下载为同步读（无 presigned/Range/断点续传；Phase 14 性能测试若成瓶颈再评估）
- 评论编辑无历史版本（数据字典未定义）

## 22. Git commits

`0bf4c70` P8-02~11 后端 → `188350f` P8-12 Java 测试 → `8996e71` P8-13 Python+download 修复 → `d4b2637` P8-14/15 前端 → `02087a4` 类型修复 → `e2e 提交` → 本 Gate 提交

## 23. Working tree

clean @ 本 Gate 提交

## 24. Final DoD checklist

Comment 模型/规则/权限/前端 ✅｜Attachment 模型/MinIO/上传/下载/删除 ✅｜文件安全（白名单/大小/文件名/穿越）✅｜数据级+ownership 三层 ✅｜真实 MySQL/Redis/MinIO 集成测试 ✅｜Python 真实 HTTP 黑盒 ✅｜前端 Vitest/lint/build ✅｜真实文件 E2E ✅｜**关键测试连续两轮**（mvn 287×2 / pytest 81×2 / Vitest 105×2）✅｜三存储清理终态全零 ✅｜ADR-018 ✅｜Swagger 同步 ✅

## 25. Final

# Phase 8 PASS ✅
