<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  listIssuesForBoard,
  listProjectOptions,
} from '@/api/issue'
import { listProjectMembers } from '@/api/projectMember'
import { listActivities } from '@/api/activity'
import type { IssueVO, ProjectVO } from '@/types/api'
import type { ActivityVO } from '@/api/activity'
import {
  ISSUE_STATUS_LABELS,
  ISSUE_STATUS_ORDER,
  labelOf,
} from '@/utils/labels'

/**
 * 项目概览（Final Edition FP-3）：项目空间首页。
 * 信息区（负责人/成员/创建时间）+ 工作项统计（状态分布条）+ 即将到期 + 最近动态。
 * 全部数据来自既有真实接口（前端聚合），无新后端端点。
 */
const route = useRoute()
const router = useRouter()
const projectId = computed(() => Number(route.params.projectId))

const project = ref<ProjectVO | null>(null)
const members = ref<{ userId: number; role: string }[]>([])
const issues = ref<IssueVO[]>([])
const activities = ref<ActivityVO[]>([])
const loading = ref(false)

const statusCounts = computed(() => {
  const by: Record<string, number> = {}
  for (const s of ISSUE_STATUS_ORDER) by[s] = 0
  for (const i of issues.value) by[i.status] = (by[i.status] ?? 0) + 1
  return by
})
const totalCount = computed(() => issues.value.length)
const openCount = computed(
  () => (statusCounts.value.OPEN ?? 0) + (statusCounts.value.IN_PROGRESS ?? 0) + (statusCounts.value.REOPENED ?? 0),
)
const closedCount = computed(() => (statusCounts.value.CLOSED ?? 0) + (statusCounts.value.RESOLVED ?? 0))
const progressPercent = computed(() =>
  totalCount.value === 0 ? 0 : Math.round((closedCount.value / totalCount.value) * 100),
)

const dueSoon = computed(() =>
  issues.value
    .filter((i) => i.dueDate && !['CLOSED', 'RESOLVED'].includes(i.status))
    .sort((a, b) => a.dueDate!.localeCompare(b.dueDate!))
    .slice(0, 8),
)

function dueClass(due: string): string {
  const days = Math.ceil((new Date(due).getTime() - Date.now()) / 86400000)
  if (days < 0) return 'po__due--overdue'
  if (days <= 3) return 'po__due--soon'
  return ''
}

function fmt(t: string | null | undefined): string {
  return t ? t.replace('T', ' ').slice(0, 10) : '—'
}

async function refresh() {
  loading.value = true
  try {
    const [options, memberPage, board, acts] = await Promise.all([
      listProjectOptions(),
      listProjectMembers(projectId.value),
      listIssuesForBoard(projectId.value),
      listActivities(projectId.value, 10),
    ])
    project.value = options.find((p) => p.id === projectId.value) ?? null
    members.value = memberPage
    issues.value = board
    activities.value = acts
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '加载概览失败')
  } finally {
    loading.value = false
  }
}

onMounted(refresh)
</script>

<template>
  <div v-loading="loading" class="po">
    <!-- 基本信息 -->
    <div class="po__cards">
      <el-card shadow="never" class="po__card">
        <div class="po__k">负责人</div>
        <div class="po__v">#{{ project?.ownerId ?? '—' }}</div>
      </el-card>
      <el-card shadow="never" class="po__card">
        <div class="po__k">成员</div>
        <div class="po__v">{{ members.length }} 人</div>
      </el-card>
      <el-card shadow="never" class="po__card">
        <div class="po__k">工作项</div>
        <div class="po__v">{{ totalCount }}</div>
      </el-card>
      <el-card shadow="never" class="po__card">
        <div class="po__k">未完结</div>
        <div class="po__v">{{ openCount }}</div>
      </el-card>
    </div>

    <!-- 进度 + 状态分布 -->
    <el-card shadow="never" class="po__section">
      <template #header><span class="po__title">项目进度</span></template>
      <div class="po__progress-row">
        <el-progress
          :percentage="progressPercent"
          :stroke-width="14"
          class="po__progress"
        />
        <span class="po__progress-text">{{ closedCount }}/{{ totalCount }} 已完结</span>
      </div>
      <div class="po__status-bar">
        <div
          v-for="s in ISSUE_STATUS_ORDER"
          :key="s"
          v-show="statusCounts[s] > 0"
          class="po__status-seg"
          :class="'po__status-seg--' + s"
          :style="{ flex: statusCounts[s] }"
          :title="`${labelOf(ISSUE_STATUS_LABELS, s)} ${statusCounts[s]}`"
        />
      </div>
      <div class="po__legend">
        <span v-for="s in ISSUE_STATUS_ORDER" :key="s" v-show="statusCounts[s] > 0" class="po__legend-item">
          <i :class="'po__dot po__status-seg--' + s" />
          {{ labelOf(ISSUE_STATUS_LABELS, s) }} {{ statusCounts[s] }}
        </span>
      </div>
    </el-card>

    <div class="po__grid">
      <!-- 即将到期 -->
      <el-card shadow="never" class="po__section">
        <template #header><span class="po__title">即将到期</span></template>
        <div v-for="i in dueSoon" :key="i.id" class="po__due-row">
          <span class="po__due-no">{{ project?.key }}-{{ i.issueNo }}</span>
          <span class="po__due-title">{{ i.title }}</span>
          <span :class="dueClass(i.dueDate!)">{{ fmt(i.dueDate) }}</span>
        </div>
        <div v-if="dueSoon.length === 0" class="po__empty">暂无带截止日期的未完结工作项</div>
      </el-card>

      <!-- 最近动态 -->
      <el-card shadow="never" class="po__section">
        <template #header>
          <div class="po__head-row">
            <span class="po__title">最近动态</span>
            <el-button link size="small" @click="router.push(`/projects/${projectId}/activity`)">
              查看全部
            </el-button>
          </div>
        </template>
        <div v-for="a in activities.slice(0, 8)" :key="a.id" class="po__act-row">
          <span class="po__act-actor">#{{ a.actorId }}</span>
          <span class="po__act-summary">{{ a.summary }}</span>
          <span class="po__act-time">{{ fmt(a.createdAt) }}</span>
        </div>
        <div v-if="activities.length === 0" class="po__empty">暂无动态</div>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.po {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.po__cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.po__card :deep(.el-card__body) {
  text-align: center;
}
.po__k {
  color: #909399;
  font-size: 13px;
}
.po__v {
  font-size: 26px;
  font-weight: 600;
  margin-top: 4px;
}
.po__title {
  font-weight: 600;
  font-size: 14px;
}
.po__section :deep(.el-card__header) {
  padding: 12px 20px;
}
.po__progress-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.po__progress {
  flex: 1;
}
.po__progress-text {
  color: #606266;
  font-size: 13px;
  white-space: nowrap;
}
.po__status-bar {
  display: flex;
  height: 12px;
  border-radius: 6px;
  overflow: hidden;
  margin-top: 14px;
}
.po__status-seg--OPEN { background: #909399; }
.po__status-seg--IN_PROGRESS { background: #409eff; }
.po__status-seg--RESOLVED { background: #67c23a; }
.po__status-seg--TESTING { background: #e6a23c; }
.po__status-seg--CLOSED { background: #c0c4cc; }
.po__status-seg--REOPENED { background: #f56c6c; }
.po__legend {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  margin-top: 10px;
}
.po__legend-item {
  font-size: 12px;
  color: #606266;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.po__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}
.po__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  align-items: start;
}
.po__due-row {
  display: flex;
  gap: 10px;
  align-items: baseline;
  padding: 6px 0;
  border-bottom: 1px solid #f5f7fa;
  font-size: 13px;
}
.po__due-no {
  color: #409eff;
  font-weight: 600;
  font-size: 12px;
  flex: none;
}
.po__due-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.po__due--overdue { color: #f56c6c; font-weight: 600; }
.po__due--soon { color: #e6a23c; font-weight: 600; }
.po__act-row {
  display: flex;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px solid #f5f7fa;
  font-size: 13px;
  align-items: baseline;
}
.po__act-actor {
  color: #409eff;
  font-weight: 600;
  flex: none;
}
.po__act-summary {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.po__act-time {
  color: #909399;
  font-size: 12px;
  flex: none;
}
.po__empty {
  color: #c0c4cc;
  font-size: 13px;
  text-align: center;
  padding: 16px 0;
}
.po__head-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
