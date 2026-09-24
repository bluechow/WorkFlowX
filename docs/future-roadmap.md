# Future Roadmap（仅记录，未实现）

> V1.0.0 已封版。以下为后续版本规划素材，均未实现。

## V1.1 — 测试管理

- 测试用例管理（用例库/目录树）
- 测试计划（计划 ↔ 用例关联、执行轮次）
- 测试执行记录（执行人/结果/证据）
- Bug ↔ 测试用例关联（Issue 与用例双向链接）

## V1.2 — 敏捷协作

- Sprint（迭代规划/时间盒）
- Backlog（待办池与优先级排序）
- Epic（跨 Sprint 需求聚合）
- Board（看板视图，拖拽即状态流转）
- Story Point（估算）
- Burndown（燃尽数据，接 Dashboard）

## V1.3 — 通知与协作增强

- WebSocket 实时推送（替代拉取式）
- @mention（评论内提及触发定向通知）
- 通知偏好设置（按类型/渠道订阅）
- Email 通知渠道

## V2.0 — AI 辅助测试

- 需求 → 测试点生成
- 需求 → 测试用例生成
- AI 测试数据生成
- 自动化脚本辅助生成

## 前置条件提醒

任一扩展启动前需重新执行：Requirement → Impact → Plan → Implement → Test → Review → Gate（AI_WORKFLOW 流程）；不得直接在 V1.0 分支上叠加。
