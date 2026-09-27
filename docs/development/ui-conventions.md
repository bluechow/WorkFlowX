# WorkFlowX UI 设计规范（Phase A 起生效）

> 目的：让所有页面遵循同一套视觉语言，杜绝"字号不一、颜色无语义、操作堆砌"。
> 新页面必须遵守本规范；老页面在触碰时逐步迁移。

## 1. 颜色语义（核心规则）

**每个颜色都有唯一含义，不允许"顺手用个颜色"。**

| 语义 | 颜色 | 用于 |
|---|---|---|
| 导航 / 常规操作（查看、进入、编辑） | primary 蓝 | 操作链接、主按钮 |
| 可逆管理操作（归档/恢复、禁用/锁定/启用、归档状态标签） | warning 橙 | 操作链接、状态标签 |
| 危险且不可逆操作（删除） | danger 红 | 操作链接、确认按钮 |
| 正常/健康状态（启用中、进行中、PASS） | success 绿 | **仅状态标签**，不做操作按钮色 |
| 中性信息（计数、未开始、PENDING） | info 灰 | 标签、辅助文字 |

规则：
- 操作按钮只用 primary / warning / danger 三种；**success 只用于标签**（V1.1 前的"启用=绿色按钮"废弃）；
- 同一行内出现多种颜色时，必须能被本表解释（用户应能"看出为什么"）；
- 枚举标签的颜色映射统一维护在 `src/utils/labels.ts`（配色注释就地可查）。

## 2. 字号与间距

| 元素 | 规格 |
|---|---|
| 页面标题（`h2`） | 20px / 600，下边距 12px |
| 表格 / 正文 | 组件默认（14px） |
| 操作链接 | 统一 `size="small"`，**禁止混用默认与 small** |
| 辅助说明文字 | 12px，`#909399` |
| 页面容器 | `max-width` 居中，上下 `margin: 16px auto` |

## 3. 表格操作列（用 `RowActions` 组件，禁止手写零散按钮）

- **分组**：操作按类别分组，组间用竖线分隔。类别依次为：
  1. **导航组**（进入子模块，如 Issue 列表/看板/用例库/测试计划）
  2. **编辑组**（修改本对象）
  3. **状态管理组**（归档/禁用等可逆操作，warning 色）
  4. **危险组**（删除，danger 色）
- 字号统一、无换行（`white-space: nowrap`，列宽按最长组预留）；
- 按权限显隐，某组全部不可见时该组分隔线自动消失；
- 按钮文案：动词优先，2~4 字（"编辑"而不是"点击此处编辑此项目"）。

## 4. 枚举显示

- **界面文案一律中文**，统一走 `src/utils/labels.ts` 的映射 + `labelOf()` 兜底；
- 后端枚举与 API 契约保持英文（只翻译展示层）；
- 枚举标签用 `el-tag`，颜色按 §1 语义表。

## 5. 页面骨架

```
<section class="xxx-view">            ← max-width 居中容器
  <div class="xxx-view__toolbar">     ← 左：页面标题；右：主操作按钮（新建等，primary）
  <div class="xxx-view__filters">     ← 筛选区（搜索框/下拉 + 搜索按钮），gap 8px，可换行
  <el-table>                          ← empty-text 必须中文
  <el-pagination>                     ← 右对齐
  <el-dialog>                         ← 宽 520~640px；底部：取消(默认) + 主操作(primary)
</section>
```

## 6. 组件清单

| 组件 | 用途 |
|---|---|
| `RowActions` | 表格操作列统一渲染（分组/权限/语义色） |
| `utils/labels.ts` | 枚举中文映射 + `labelOf()` 兜底 |

## 7. 迁移状态

- [x] ProjectsView（操作列重组 + 状态中文）
- [x] RolesView / UsersView / OrganizationsView / TestPlansView（操作列统一）
- [x] IssuesView / ProjectBoardView（枚举中文化）
- [ ] TestCasesView / TestPlanDetailView / AuditView（触碰时迁移）
