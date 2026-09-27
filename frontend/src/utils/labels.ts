/**
 * 枚举值 ↔ 中文展示名统一映射（Phase A-② 起）。
 *
 * 原则：后端枚举与 API 契约保持英文不动；界面文案一律走本模块的中文映射。
 * 新增枚举时必须同步补齐映射，禁止在视图里硬编码英文枚举文案。
 */

export const ISSUE_STATUS_LABELS: Record<string, string> = {
  OPEN: '待处理',
  IN_PROGRESS: '处理中',
  RESOLVED: '已解决',
  TESTING: '测试中',
  CLOSED: '已关闭',
  REOPENED: '重新打开',
}

/** 看板列顺序 = 业务流转顺序（ADR-017 状态机：OPEN→IN_PROGRESS→RESOLVED→TESTING→CLOSED，REOPENED 回流） */
export const ISSUE_STATUS_ORDER = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'TESTING', 'CLOSED', 'REOPENED'] as const

export const ISSUE_TYPE_LABELS: Record<string, string> = {
  BUG: '缺陷',
  TASK: '任务',
  FEATURE: '新功能',
  IMPROVEMENT: '优化',
}

export const ISSUE_PRIORITY_LABELS: Record<string, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  URGENT: '紧急',
}

export const ISSUE_SEVERITY_LABELS: Record<string, string> = {
  S1: 'S1 致命',
  S2: 'S2 严重',
  S3: 'S3 一般',
  S4: 'S4 轻微',
}

export const ISSUE_PRIORITY_TAG_TYPES: Record<string, string> = {
  LOW: 'info',
  MEDIUM: '',
  HIGH: 'warning',
  URGENT: 'danger',
}

export const USER_STATUS_LABELS: Record<string, string> = {
  ACTIVE: '正常',
  DISABLED: '已禁用',
  LOCKED: '已锁定',
}

export const PROJECT_STATUS_LABELS: Record<string, string> = {
  ACTIVE: '进行中',
  ARCHIVED: '已归档',
}

export const ORG_ROLE_LABELS: Record<string, string> = {
  OWNER: '所有者',
  ADMIN: '管理员',
  MEMBER: '成员',
}

export const PROJECT_ROLE_LABELS: Record<string, string> = {
  OWNER: '负责人',
  MANAGER: '管理者',
  MEMBER: '成员',
}

export const PLAN_STATUS_LABELS: Record<string, string> = {
  NOT_STARTED: '未开始',
  RUNNING: '执行中',
  COMPLETED: '已完成',
}

export const ITEM_RESULT_LABELS: Record<string, string> = {
  PENDING: '待执行',
  PASS: '通过',
  FAIL: '失败',
  BLOCKED: '阻塞',
}

export const ITEM_RESULT_TAG_TYPES: Record<string, string> = {
  PENDING: 'info',
  PASS: 'success',
  FAIL: 'danger',
  BLOCKED: 'warning',
}

export const CASE_TYPE_LABELS: Record<string, string> = {
  FUNCTIONAL: '功能',
  REGRESSION: '回归',
  SMOKE: '冒烟',
  SECURITY: '安全',
  PERFORMANCE: '性能',
}

export const CASE_PRIORITY_LABELS: Record<string, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  CRITICAL: '关键',
}

export const CASE_STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  ACTIVE: '启用',
  DEPRECATED: '已废弃',
}

/** 通用取值：map[code] ?? code（未映射的兜底显示原值，避免界面出现空白） */
export function labelOf(map: Record<string, string>, code: string | null | undefined): string {
  if (code == null || code === '') return '—'
  return map[code] ?? code
}
