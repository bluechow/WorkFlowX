<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listMyWorkItems,
  type WorkItemScope,
  type WorkItemVO,
} from '@/api/issue'
import IssueDetailDrawer from '@/components/IssueDetailDrawer.vue'
import {
  ISSUE_PRIORITY_LABELS,
  ISSUE_PRIORITY_TAG_TYPES,
  ISSUE_SEVERITY_LABELS,
  ISSUE_STATUS_LABELS,
  ISSUE_TYPE_LABELS,
  labelOf,
} from '@/utils/labels'
import type { IssuePriority, IssueStatus, IssueType, IssueVO } from '@/types/api'

/**
 * 全局工作项视图（Final Edition FP-1）：跨项目四视图（全部/我的/待我处理/我创建的）。
 * 读语义=issue:list（与项目内列表一致）；点击行打开详情抽屉（携带所属项目上下文）。
 */
const SCOPES: { key: WorkItemScope; label: string }[] = [
  { key: 'all', label: '全部工作项' },
  { key: 'assigned', label: '我的工作项' },
  { key: 'todo', label: '待我处理' },
  { key: 'created', label: '我创建的' },
]

const scope = ref<WorkItemScope>('all')
const items = ref<WorkItemVO[]>([])
const total = ref(0)
const loading = ref(false)
const pager = reactive({ page: 1, size: 20, keyword: '' })
const filters = reactive({
  type: undefined as undefined | IssueType,
  priority: undefined as undefined | IssuePriority,
  status: undefined as undefined | IssueStatus,
})

const drawerVisible = ref(false)
const detailIssue = ref<IssueVO | null>(null)
const drawerProjectKey = ref<string | undefined>(undefined)

function openDetail(row: WorkItemVO) {
  drawerProjectKey.value = row.projectKey ?? undefined
  drawerIssue(row)
}

/** WorkItemVO → 抽屉所需 IssueVO 形状（字段兼容映射） */
function drawerIssue(row: WorkItemVO) {
  detailIssue.value = {
    id: row.id,
    projectId: row.projectId,
    issueNo: row.issueNo,
    title: row.title,
    description: null,
    type: row.type,
    priority: row.priority,
    severity: row.severity,
    status: row.status,
    reporterId: row.reporterId,
    assigneeId: row.assigneeId,
    dueDate: row.dueDate,
    milestoneId: null,
    createdAt: '',
    updatedAt: row.updatedAt,
    labels: [],
  }
  drawerVisible.value = true
}

function onDetailUpdated(updated: IssueVO) {
  detailIssue.value = updated
  void refresh()
}

async function refresh() {
  loading.value = true
  try {
    const page = await listMyWorkItems({
      scope: scope.value,
      keyword: pager.keyword || undefined,
      type: filters.type,
      priority: filters.priority,
      status: filters.status,
      page: pager.page,
      size: pager.size,
    })
    items.value = page.list
    total.value = page.total
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '加载工作项失败')
  } finally {
    loading.value = false
  }
}

function search() {
  pager.page = 1
  void refresh()
}

function switchScope(s: WorkItemScope) {
  scope.value = s
  pager.page = 1
  void refresh()
}

function dueClass(due: string | null, status: IssueStatus): string {
  if (!due || status === 'CLOSED') return ''
  const days = Math.ceil((new Date(due).getTime() - Date.now()) / 86400000)
  if (days < 0) return 'work-items__due--overdue'
  if (days <= 3) return 'work-items__due--soon'
  return ''
}

function fmtDue(due: string | null): string {
  return due ? due.replace('T', ' ').slice(0, 10) : '—'
}

onMounted(refresh)
</script>

<template>
  <section class="work-items">
    <div class="work-items__head">
      <h2>工作项</h2>
    </div>

    <div class="work-items__scopes">
      <button
        v-for="s in SCOPES"
        :key="s.key"
        class="work-items__scope"
        :class="{ 'work-items__scope--active': scope === s.key }"
        @click="switchScope(s.key)"
      >
        {{ s.label }}
      </button>
    </div>

    <div class="work-items__filters">
      <el-input
        v-model="pager.keyword"
        placeholder="搜索标题/描述"
        clearable
        style="width: 220px"
        @keyup.enter="search"
      />
      <el-select v-model="filters.type" clearable placeholder="类型" style="width: 110px" @change="search">
        <el-option
          v-for="t in ['BUG', 'TASK', 'FEATURE', 'IMPROVEMENT']"
          :key="t"
          :label="labelOf(ISSUE_TYPE_LABELS, t)"
          :value="t"
        />
      </el-select>
      <el-select v-model="filters.priority" clearable placeholder="优先级" style="width: 110px" @change="search">
        <el-option
          v-for="p in ['LOW', 'MEDIUM', 'HIGH', 'URGENT']"
          :key="p"
          :label="labelOf(ISSUE_PRIORITY_LABELS, p)"
          :value="p"
        />
      </el-select>
      <el-select v-model="filters.status" clearable placeholder="状态" style="width: 130px" @change="search">
        <el-option
          v-for="st in ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'TESTING', 'CLOSED', 'REOPENED']"
          :key="st"
          :label="labelOf(ISSUE_STATUS_LABELS, st)"
          :value="st"
        />
      </el-select>
      <el-button @click="search">搜索</el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="items"
      border
      empty-text="暂无工作项"
      row-class-name="work-items__row"
      @row-click="openDetail"
    >
      <el-table-column label="编号" width="130">
        <template #default="{ row }">
          <span class="work-items__no">{{ row.projectKey }}-{{ row.issueNo }}</span>
        </template>
      </el-table-column>
      <el-table-column label="标题" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">{{ row.title }}</template>
      </el-table-column>
      <el-table-column label="项目" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.projectName ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="类型" width="90">
        <template #default="{ row }">{{ labelOf(ISSUE_TYPE_LABELS, row.type) }}</template>
      </el-table-column>
      <el-table-column label="优先级" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="(ISSUE_PRIORITY_TAG_TYPES[row.priority] as never) || 'info'">
            {{ labelOf(ISSUE_PRIORITY_LABELS, row.priority) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="严重程度" width="110">
        <template #default="{ row }">{{ labelOf(ISSUE_SEVERITY_LABELS, row.severity) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ labelOf(ISSUE_STATUS_LABELS, row.status) }}</template>
      </el-table-column>
      <el-table-column label="截止日期" width="110">
        <template #default="{ row }">
          <span :class="dueClass(row.dueDate, row.status)">{{ fmtDue(row.dueDate) }}</span>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > pager.size"
      class="work-items__pager"
      layout="prev, pager, next, total"
      :total="total"
      :page-size="pager.size"
      :current-page="pager.page"
      @current-change="(p: number) => { pager.page = p; refresh() }"
    />

    <IssueDetailDrawer
      v-model:visible="drawerVisible"
      :project-id="detailIssue?.projectId ?? 0"
      :project-key="drawerProjectKey"
      :issue="detailIssue"
      @updated="onDetailUpdated"
    />
  </section>
</template>

<style scoped>
.work-items {
  max-width: 1200px;
  margin: 0 auto;
  padding: 16px 24px 0;
}
.work-items__head h2 {
  margin: 0 0 10px;
  font-size: 20px;
}
.work-items__scopes {
  display: flex;
  gap: 6px;
  margin-bottom: 12px;
  border-bottom: 1px solid #dcdfe6;
  padding-bottom: 0;
}
.work-items__scope {
  border: none;
  background: none;
  padding: 8px 14px;
  font-size: 14px;
  color: #606266;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}
.work-items__scope:hover {
  color: #409eff;
}
.work-items__scope--active {
  color: #409eff;
  border-bottom-color: #409eff;
  font-weight: 600;
}
.work-items__filters {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.work-items__row {
  cursor: pointer;
}
.work-items__no {
  color: #409eff;
  font-size: 12px;
  font-weight: 600;
}
.work-items__due--overdue {
  color: #f56c6c;
  font-weight: 600;
}
.work-items__due--soon {
  color: #e6a23c;
  font-weight: 600;
}
.work-items__pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
