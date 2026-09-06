# WorkFlowX API 设计约定

> 阶段: Phase 1 任务 1-5 ｜ 上位规则: Master Prompt §9、ADR-005
> 所有后端接口必须遵守本约定；违反约定视为实现缺陷。

## 1. 基本规则

| 项 | 约定 |
|---|---|
| 前缀 | 统一 `/api/v1/`（版本升级到 v2 时旧版本保留期由另行决策确定） |
| 风格 | RESTful：资源名复数、层级表达从属关系（如 `/api/v1/projects/{projectId}/issues`） |
| 大小写 | 路径全小写 kebab-case；查询参数 camelCase；JSON 字段 camelCase |
| 文档 | SpringDoc/Swagger UI（dev 启用，prod 默认关闭），注解与实现同步维护 |

## 2. HTTP 方法语义

| 方法 | 语义 | 幂等 |
|---|---|---|
| GET | 查询，无副作用 | 是 |
| POST | 创建 / 非幂等动作（如状态转换） | 否 |
| PUT | 全量更新 | 是 |
| PATCH | 部分更新（谨慎使用） | 否 |
| DELETE | 删除 | 是 |

## 3. 状态码使用（禁止一律 200）

| 状态码 | 场景 |
|---|---|
| 200 | 查询/更新/删除成功 |
| 201 | 创建成功（POST 建资源） |
| 400 | 请求格式/业务规则错误 |
| 401 | 未认证 / Token 无效 / 过期 |
| 403 | 已认证但权限不足 |
| 404 | 资源不存在（含未匹配路径） |
| 409 | 资源冲突（唯一约束、并发修改、非法状态转换） |
| 422 | 参数校验失败（Bean Validation / 领域校验） |
| 429 | 触发限流 |
| 500 | 服务器内部错误（不暴露细节） |

## 4. 统一响应结构

```json
{
  "code": 200,
  "message": "success",
  "data": { },
  "timestamp": "2026-09-06T08:51:32.123715Z",
  "traceId": "7231e5df2bc045d0883ecc038615dc44"
}
```

- `code`：与 HTTP 状态码同值（200 成功，其余为对应错误码）
- `message`：对用户安全的描述；错误信息禁止包含堆栈 / SQL / 内部实现
- `data`：业务数据；无数据时省略（`@JsonInclude(NON_NULL)`）
- `timestamp`：UTC ISO-8601
- `traceId`：与响应头 `X-Trace-Id` 一致；客户端请求可携带 `X-Trace-Id` 透传链路

## 5. 分页 / 排序 / 过滤

| 参数 | 约定 | 示例 |
|---|---|---|
| page | 页码，从 1 开始 | `?page=1` |
| size | 每页条数，默认 20，上限 100 | `?size=20` |
| sort | `字段,方向`，方向 asc/desc | `?sort=createdAt,desc` |
| 过滤 | 业务字段名作为查询参数 | `?status=OPEN&priority=HIGH` |

分页响应统一封装于 `data`：

```json
{
  "code": 200, "message": "success",
  "data": { "list": [], "total": 0, "page": 1, "size": 20 },
  "timestamp": "...", "traceId": "..."
}
```

## 6. 错误响应示例

```json
{
  "code": 422,
  "message": "validation failed: username: 用户名不能为空",
  "timestamp": "...",
  "traceId": "..."
}
```

异常映射：Business→400/409，Authentication→401，Authorization→403，
ResourceNotFound→404，Validation→422，未预期异常→500（仅 "internal server error"）。

## 7. 认证 API（Phase 2 已落地）

| 端点 | 方法 | 认证 | 说明 |
|---|---|---|---|
| /api/v1/auth/login | POST | 公开 | 返回 accessToken/tokenType/expiresIn/userId/username/roles |
| /api/v1/auth/logout | POST | Bearer | 删除 Redis 会话，原 Token 立即失效（幂等） |
| /api/v1/auth/me | GET | Bearer | 返回数据库最新 UserVO（无敏感字段） |

- 认证头：`Authorization: Bearer <JWT>`；无/非法/过期 Token → 401 统一 JSON
- 权限不足 → 403；登录失败统一 401（防枚举）；15 分钟窗口 5 次失败 → 429（锁定）
- Token 有效 = JWT 签名/过期校验通过 **且** Redis 单会话匹配（后登录覆盖先登录）
- 架构细节见 [docs/architecture/security.md](../architecture/security.md)

## 8. 安全约定

- 认证后接口通过 `Authorization: Bearer <JWT>` 传递凭证（Phase 2 落地）
- 权限校验强制在后端（Master Prompt §7），前端隐藏按钮不构成任何安全边界
- 写操作要求幂等控制的场景（如支付类）后续按模块补充 idempotency-key 约定
