<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listIssuesForBoard, listProjectOptions, transitionIssueStatus } from '@/api/issue'
import { useAuthStore } from '@/stores/auth'
import {
  ISSUE_PRIORITY_LABELS,
  ISSUE_PRIORITY_TAG_TYPES,
  ISSUE_SEVERITY_LABELS,
  ISSUE_STATUS_LABELS,
  ISSUE_STATUS_ORDER,
  labelOf,
} from '@/utils/labels'
import type { IssueStatus, IssueVO, ProjectVO } from '@/types/api'

/**
 * Issue 看板（Phase A-②）：
 * - 列 = 状态，列序即业务流转顺序（ADR-017 状态机），中文列名走 labels 映射；
 * - 拖拽 = 状态流转：仅允许拖到"合法目标状态"（复用后端 transition 端点，
 *   非法流转 409 兜底），无 issue:transition 权限时只读；
 * - 读语义与列表一致：issue:list authority（不限项目成员）。
 */
const auth = useAuthStore()
const route = useRoute()
const projectId = computed(() => Number(route.params.projectId))
const project = ref<ProjectVO | null>(null)

const loading = ref(false)
const transitioning = ref(false)
const issues = ref<IssueVO[]>([])

const columns = computed(() =>
  ISSUE_STATUS_ORDER.map((status) => ({
    status,
    label: ISSUE_STATUS_LABELS[status],
    items: issues.value.filter((i) => i.status === status),
  })),
)

/** 允许流转目标（与后端 ADR-017 矩阵一致；后端 409 兜底） */
const ALLOWED: Record<IssueStatus, IssueStatus[]> = {
  OPEN: ['IN_PROGRESS'],
  IN_PROGRESS: ['RESOLVED'],
  RESOLVED: ['TESTING'],
  TESTING: ['CLOSED', 'REOPENED'],
  CLOSED: [],
  REOPENED: ['IN_PROGRESS'],
}

function allowedTargets(status: IssueStatus): IssueStatus[] {
  return ALLOWED[status] ?? []
}

const dragIssue = ref<IssueVO | null>(null)

function onDragStart(issue: IssueVO, event: DragEvent) {
  if (!auth.hasPermission('issue:transition')) {
    event.preventDefault()
    return
  }
  dragIssue.value = issue
  event.dataTransfer?.setData('text/plain', String(issue.id))
}

function onDrop(target: IssueStatus, event: DragEvent) {
  event.preventDefault()
  const issue = dragIssue.value
  dragIssue.value = null
  if (!issue || issue.status === target || transitioning.value) return
  if (!allowedTargets(issue.status).includes(target)) {
    ElMessage.warning(
      `不允许的流转：${ISSUE_STATUS_LABELS[issue.status]} → ${ISSUE_STATUS_LABELS[target]}`,
    )
    return
  }
  doTransition(issue, target)
}

async function doTransition(issue: IssueVO, toStatus: IssueStatus) {
  transitioning.value = true
  const from = issue.status
  try {
    const updated = await transitionIssueStatus(projectId.value, issue.id, from, toStatus)
    // 原地替换，保持看板其他卡片不动
    const idx = issues.value.findIndex((i) => i.id === issue.id)
    if (idx >= 0) issues.value[idx] = updated
    ElMessage.success(`「${issue.title.slice(0, 12)}…」已流转为 ${ISSUE_STATUS_LABELS[toStatus]}`)
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '状态流转失败')
  } finally {
    transitioning.value = false
  }
}

async function refresh() {
  loading.value = true
  try {
    issues.value = await listIssuesForBoard(projectId.value)
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '加载看板失败')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    const all = await listProjectOptions()
    project.value = all.find((p) => p.id === projectId.value) ?? null
  } catch {
    project.value = null
  }
  await refresh()
})
</script>

<template>
  <section class="board-view">
    <div class="board-view__head">
      <h2>看板 — {{ project?.name ?? `#${projectId}` }}</h2>
      <div class="board-view__actions">
        <el-button @click="refresh" :loading="loading">刷新</el-button>
        <el-button
          v-if="auth.hasPermission('issue:list')"
          tag="router-link"
          :to="`/system/projects/${projectId}/issues`"
        >
          列表视图
        </el-button>
      </div>
    </div>
    <p class="board-view__hint">
      拖拽卡片即状态流转（仅允许沿业务流程移动）；无「流转」权限时为只读。
    </p>

    <div v-loading="loading" class="board-view__columns">
      <div
        v-for="col in columns"
        :key="col.status"
        class="board-col"
        :class="{ 'board-col--droppable': dragIssue && dragIssue.status !== col.status }"
        @dragover="dragIssue && dragIssue.status !== col.status && $event.preventDefault()"
        @drop="onDrop(col.status, $event as DragEvent)"
      >
        <div class="board-col__head">
          <span class="board-col__title">{{ col.label }}</span>
          <el-tag size="small" type="info">{{ col.items.length }}</el-tag>
        </div>
        <div class="board-col__body">
          <div
            v-for="issue in col.items"
            :key="issue.id"
            class="board-card"
            :draggable="auth.hasPermission('issue:transition')"
            @dragstart="onDragStart(issue, $event as DragEvent)"
          >
            <div class="board-card__meta">
              <span class="board-card__no">{{ project?.key }}-{{ issue.issueNo }}</span>
              <el-tag
                size="small"
                :type="(ISSUE_PRIORITY_TAG_TYPES[issue.priority] as never) || 'info'"
              >
                {{ labelOf(ISSUE_PRIORITY_LABELS, issue.priority) }}
              </el-tag>
              <el-tag v-if="issue.severity" size="small" type="danger">
                {{ labelOf(ISSUE_SEVERITY_LABELS, issue.severity) }}
              </el-tag>
            </div>
            <div class="board-card__title">{{ issue.title }}</div>
          </div>
          <div v-if="col.items.length === 0" class="board-col__empty">暂无卡片</div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.board-view {
  max-width: 1400px;
  margin: 16px auto;
}
.board-view__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}
.board-view__actions {
  display: flex;
  gap: 8px;
}
.board-view__hint {
  color: #909399;
  font-size: 12px;
  margin: 0 0 12px;
}
.board-view__columns {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  overflow-x: auto;
  padding-bottom: 12px;
}
.board-col {
  flex: 1 0 210px;
  min-width: 210px;
  background: #f5f7fa;
  border-radius: 8px;
  border: 1px dashed transparent;
}
.board-col--droppable {
  border-color: #409eff;
}
.board-col__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px 6px;
}
.board-col__title {
  font-weight: 600;
  font-size: 14px;
}
.board-col__body {
  padding: 4px 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 80px;
}
.board-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 8px 10px;
  cursor: grab;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}
.board-card:hover {
  border-color: #c6e2ff;
}
.board-card__meta {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 4px;
}
.board-card__no {
  color: #409eff;
  font-size: 12px;
  font-weight: 600;
}
.board-card__title {
  font-size: 13px;
  line-height: 1.4;
}
.board-col__empty {
  color: #c0c4cc;
  font-size: 12px;
  text-align: center;
  padding: 16px 0;
}
</style>
