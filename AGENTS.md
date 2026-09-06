# AGENTS.md — WorkFlowX AI 协作入口

本文件是 AI 编码工具（Codex、ZCode 等）的入口文件，会被工具自动加载。

规则本体不在本文件，而在项目根目录的总控文件体系中。本文件只负责规定加载顺序与首要指令。

## 强制加载顺序

任何 AI 会话开始工作时，必须按以下顺序阅读并遵守：

1. `AI_MASTER_PROMPT.md` — 永久规则（角色、原则、技术栈、架构、禁止事项）
2. `AI_WORKFLOW.md` — 标准工作流、STOP 停止条件、自主处理边界
3. `AI_CONTEXT.md` — 项目当前状态（动手前必读）
4. `AI_TASKS.md` — 当前任务清单
5. `AI_DECISIONS.md` — 已接受的技术决策（不得静默违反）

## 首要指令

1. **任何任务开始前**，先读 `AI_CONTEXT.md` 和 `AI_TASKS.md`，确认当前 Phase 与任务状态，禁止凭空假设项目进度。
2. **执行过程**遵守 `AI_WORKFLOW.md`：
   - 命中 STOP 条件（S1–S7）→ 立即停止，按其规定格式向用户汇报并等待决策
   - 普通代码错误 / 编译错误 / 测试失败 / 依赖问题 / 普通 Bug → 自主分析、修复、重新验证，**禁止**动不动就问用户
3. **任务完成后**：更新 `AI_CONTEXT.md` 与 `AI_TASKS.md`；若做出重要技术决策，追加到 `AI_DECISIONS.md`。
4. **始终遵守** `AI_MASTER_PROMPT.md` 全部规则，特别是 §28 Definition of Done、§29 绝对禁止事项、§32 决策优先级。

## 文件职责速查

| 文件 | 作用 |
|---|---|
| `AI_MASTER_PROMPT.md` | 永久规则 |
| `AI_WORKFLOW.md` | AI 如何工作 |
| `AI_CONTEXT.md` | 当前项目状态 |
| `AI_TASKS.md` | 当前任务清单 |
| `AI_DECISIONS.md` | 重要技术决策记录 |
