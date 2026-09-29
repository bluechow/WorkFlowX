<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  allowedTargets,
  createIssue,
  listIssues,
  listProjectOptions,
  transitionIssueStatus,
  updateIssue,
} from '@/api/issue'
import IssueDetailDrawer from '@/components/IssueDetailDrawer.vue'
import { createLabel, deleteLabel, listLabels, updateLabel, type LabelVO } from '@/api/issue'
import { useAuthStore } from '@/stores/auth'
import {
  ISSUE_PRIORITY_LABELS,
  ISSUE_SEVERITY_LABELS,
  ISSUE_STATUS_LABELS,
  ISSUE_TYPE_LABELS,
  labelOf,
} from '@/utils/labels'
import type { IssueStatus, IssueVO, ProjectVO } from '@/types/api'

/**
 * Issue 管理（P6-08）：真实 API 列表/筛选/创建/编辑/状态/分派。
 * reporter 由后端自动绑定（前端无 reporter 输入）；按钮按 issue:* 权限码渲染（纯 UX）。
 */
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const projectId = computed(() => Number(route.params.projectId))
const project = ref<ProjectVO | null>(null)
const issues = ref<IssueVO[]>([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)
const transitioning = ref(false)
const pager = reactive({ page: 1, size: 10, keyword: '' })
const filters = reactive({
  type: undefined as undefined | string,
  priority: undefined as undefined | string,
  severity: undefined as undefined | string,
  status: undefined as undefined | string,
  assigneeId: undefined as undefined | number,
  labelId: undefined as undefined | number,
})

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  title: '',
  description: '',
  type: 'TASK',
  priority: 'MEDIUM',
  severity: null as null | string,
  assigneeId: null as number | null,
  dueDate: null as string | null,
  labelIds: [] as number[],
})

const members = ref<{ userId: number; role: string }[]>([])
const projectOptions = ref<ProjectVO[]>([])
const labels = ref<LabelVO[]>([])

// ===== 标签管理对话框（V17）=====
const labelDialogVisible = ref(false)
const labelForm = reactive({ id: null as number | null, name: '', color: '#409EFF' })
const labelSaving = ref(false)

async function loadLabels() {
  try {
    labels.value = await listLabels(projectId.value)
  } catch {
    labels.value = []
  }
}

function openLabelDialog() {
  labelForm.id = null
  labelForm.name = ''
  labelForm.color = '#409EFF'
  labelDialogVisible.value = true
}

function editLabel(label: LabelVO) {
  labelForm.id = label.id
  labelForm.name = label.name
  labelForm.color = label.color
}

async function submitLabel() {
  if (!labelForm.name.trim() || labelSaving.value) return
  labelSaving.value = true
  try {
    if (labelForm.id === null) {
      await createLabel(projectId.value, { name: labelForm.name.trim(), color: labelForm.color })
      ElMessage.success('标签已创建')
    } else {
      await updateLabel(projectId.value, labelForm.id, { name: labelForm.name.trim(), color: labelForm.color })
      ElMessage.success('标签已更新')
    }
    labelForm.id = null
    labelForm.name = ''
    await loadLabels()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '保存标签失败')
  } finally {
    labelSaving.value = false
  }
}

async function removeLabel(label: LabelVO) {
  try {
    await ElMessageBox.confirm(`确认删除标签「${label.name}」？工作项上的该标签将一并移除。`, '确认操作',
      { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteLabel(projectId.value, label.id)
    ElMessage.success('标签已删除')
    await loadLabels()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '删除标签失败')
  }
}

// Phase A-③: 详情抽屉（点击编号打开，聚合信息/流转/评论/附件）
const drawerVisible = ref(false)
const detailIssue = ref<IssueVO | null>(null)

function openPanels(issue: IssueVO) {
  detailIssue.value = issue
  drawerVisible.value = true
}

/** 抽屉内流转后原位刷新该行 */
function onDetailUpdated(updated: IssueVO) {
  detailIssue.value = updated
  const idx = issues.value.findIndex((i) => i.id === updated.id)
  if (idx >= 0) issues.value[idx] = updated
}

function dueClass(row: IssueVO): string {
  if (!row.dueDate || row.status === 'CLOSED') return ''
  const days = Math.ceil((new Date(row.dueDate).getTime() - Date.now()) / 86400000)
  if (days < 0) return 'issues-view__due--overdue'
  if (days <= 3) return 'issues-view__due--soon'
  return ''
}

async function refresh() {
  loading.value = true
  try {
    const page = await listIssues(projectId.value, {
      keyword: pager.keyword || undefined,
      type: filters.type as never,
      priority: filters.priority as never,
      severity: filters.severity as never,
      status: filters.status as never,
      assigneeId: filters.assigneeId,
      labelId: filters.labelId,
      page: pager.page,
      size: pager.size,
    })
    issues.value = page.list
    total.value = page.total
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '加载 Issue 失败')
  } finally {
    loading.value = false
  }
}

function search() {
  pager.page = 1
  refresh()
}

async function loadMembers() {
  try {
    const { listProjectMembers } = await import('@/api/projectMember')
    members.value = await listProjectMembers(projectId.value)
  } catch {
    members.value = []
  }
}

async function openCreate() {
  editingId.value = null
  form.title = ''
  form.description = ''
  form.type = 'TASK'
  form.priority = 'MEDIUM'
  form.severity = null
  form.assigneeId = null
  form.dueDate = null
  form.labelIds = []
  await Promise.all([loadMembers(), loadLabels()])
  dialogVisible.value = true
}

async function openEdit(issue: IssueVO) {
  editingId.value = issue.id
  form.title = issue.title
  form.description = issue.description ?? ''
  form.type = issue.type
  form.priority = issue.priority
  form.severity = issue.severity
  form.assigneeId = issue.assigneeId
  form.dueDate = issue.dueDate
  form.labelIds = (issue.labels ?? []).map((l) => l.id)
  await Promise.all([loadMembers(), loadLabels()])
  dialogVisible.value = true
}

async function submit() {
  if (submitting.value) return
  if (!form.title.trim()) {
    ElMessage.warning('请输入标题')
    return
  }
  if (form.type === 'BUG' && form.severity) {
    // severity 仅 BUG（与后端一致）
  }
  submitting.value = true
  try {
    if (editingId.value === null) {
      await createIssue(projectId.value, {
        title: form.title.trim(),
        description: form.description.trim() || undefined,
        type: form.type as never,
        priority: form.priority as never,
        severity: form.type === 'BUG' ? (form.severity as never) : null,
        assigneeId: form.assigneeId ?? null,
        dueDate: form.dueDate || null,
        labelIds: form.labelIds,
      })
      ElMessage.success('Issue 已创建')
    } else {
      await updateIssue(projectId.value, editingId.value, {
        title: form.title.trim(),
        description: form.description.trim() || undefined,
        priority: form.priority as never,
        severity: form.type === 'BUG' ? (form.severity as never) : null,
        assigneeId: form.assigneeId ?? null,
        dueDate: form.dueDate || null,
        clearDueDate: !form.dueDate,
        labelIds: form.labelIds,
      })
      ElMessage.success('Issue 已更新')
    }
    dialogVisible.value = false
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '保存失败')
  } finally {
    submitting.value = false
  }
}

async function doTransition(issue: IssueVO, toStatus: IssueStatus) {
  if (transitioning.value) return
  transitioning.value = true
  try {
    await transitionIssueStatus(projectId.value, issue.id, issue.status, toStatus)
    ElMessage.success(`状态已流转为 ${ISSUE_STATUS_LABELS[toStatus] ?? toStatus}`)
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '状态流转失败')
  } finally {
    transitioning.value = false
  }
}

async function changeAssignee(issue: IssueVO, userId: number | null) {
  try {
    const { assignIssue } = await import('@/api/issue')
    await assignIssue(projectId.value, issue.id, userId)
    ElMessage.success(userId ? `已分派给 #${userId}` : '已取消分派')
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '分派失败')
  }
}

onMounted(async () => {
  try {
    const all = await listProjectOptions()
    projectOptions.value = all
    project.value = all.find((p) => p.id === projectId.value) ?? null
  } catch {
    project.value = null
  }
  await refresh()
  await Promise.all([loadMembers(), loadLabels()])
})
</script>

<template>
  <section class="issues-view">
    <div class="issues-view__toolbar">
      <h2>Issues — {{ project?.name ?? `#${projectId}` }}</h2>
      <div class="issues-view__actions">
        <el-button @click="router.push(`/projects/${projectId}/board`)">看板视图</el-button>
        <el-button
          v-if="auth.hasPermission('issue:update')"
          @click="openLabelDialog"
        >
          标签管理
        </el-button>
        <el-button
          v-if="auth.hasPermission('issue:create')"
          type="primary"
          @click="openCreate"
        >
          新建 Issue
        </el-button>
      </div>
    </div>

    <div class="issues-view__filters">
      <el-input
        v-model="pager.keyword"
        placeholder="搜索标题/描述"
        clearable
        style="width: 200px"
        @keyup.enter="search"
      />
      <el-select v-model="filters.type" clearable placeholder="类型" style="width: 120px" @change="search">
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
      <el-select v-model="filters.severity" clearable placeholder="严重程度" style="width: 100px" @change="search">
        <el-option v-for="s in ['S1', 'S2', 'S3', 'S4']" :key="s" :label="labelOf(ISSUE_SEVERITY_LABELS, s)" :value="s" />
      </el-select>
      <el-select v-model="filters.status" clearable placeholder="状态" style="width: 130px" @change="search">
        <el-option
          v-for="s in ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'TESTING', 'CLOSED', 'REOPENED']"
          :key="s"
          :label="labelOf(ISSUE_STATUS_LABELS, s)"
          :value="s"
        />
      </el-select>
      <el-select
        v-if="labels.length"
        v-model="filters.labelId"
        clearable
        placeholder="标签"
        style="width: 130px"
        @change="search"
      >
        <el-option v-for="l in labels" :key="l.id" :label="l.name" :value="l.id" />
      </el-select>
      <el-button @click="search">搜索</el-button>
    </div>

    <el-table v-loading="loading" :data="issues" border empty-text="暂无 Issue">
      <el-table-column label="编号" width="130">
        <template #default="{ row }">
          <el-button
            v-if="auth.hasPermission('issue:get')"
            link
            type="primary"
            @click="openPanels(row)"
          >
            {{ project?.key ?? '' }}-{{ row.issueNo }}
          </el-button>
          <span v-else>{{ project?.key ?? '' }}-{{ row.issueNo }}</span>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ labelOf(ISSUE_TYPE_LABELS, row.type) }}</template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
      <el-table-column label="优先级" width="90">
        <template #default="{ row }">{{ labelOf(ISSUE_PRIORITY_LABELS, row.priority) }}</template>
      </el-table-column>
      <el-table-column label="严重程度" width="110">
        <template #default="{ row }">{{ labelOf(ISSUE_SEVERITY_LABELS, row.severity) }}</template>
      </el-table-column>
      <el-table-column label="标签" width="150">
        <template #default="{ row }">
          <el-tag
            v-for="l in row.labels"
            :key="l.id"
            size="small"
            class="issues-view__label"
            :color="l.color"
            style="border: none; color: #fff"
          >
            {{ l.name }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="截止日期" width="110">
        <template #default="{ row }">
          <span :class="dueClass(row)">{{ row.dueDate ? row.dueDate.slice(0, 10) : '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="130">
        <template #default="{ row }">
          <el-select
            v-if="auth.hasPermission('issue:transition') && allowedTargets(row.status).length"
            :model-value="row.status"
            size="small"
            :disabled="transitioning"
            placeholder="流转…"
            @change="(s: IssueStatus) => doTransition(row, s)"
          >
            <!-- 当前状态以禁用项呈现：收起时显示中文标签而非原始枚举 -->
            <el-option
              :label="labelOf(ISSUE_STATUS_LABELS, row.status)"
              :value="row.status"
              disabled
            />
            <el-option
              v-for="t in allowedTargets(row.status)"
              :key="t"
              :label="labelOf(ISSUE_STATUS_LABELS, t)"
              :value="t"
            />
          </el-select>
          <el-tag v-else :type="row.status === 'CLOSED' ? 'info' : 'success'" size="small">
            {{ labelOf(ISSUE_STATUS_LABELS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80">
        <template #default="{ row }">
          <el-button
            v-if="auth.hasPermission('issue:update')"
            link
            type="primary"
            size="small"
            @click="openEdit(row)"
          >
            编辑
          </el-button>
        </template>
      </el-table-column>
      <el-table-column label="报告人" width="90">
        <template #default="{ row }">#{{ row.reporterId }}</template>
      </el-table-column>
      <el-table-column label="经办人" width="150">
        <template #default="{ row }">
          <el-select
            v-if="auth.hasPermission('issue:assign')"
            :model-value="row.assigneeId"
            size="small"
            clearable
            placeholder="未分派"
            @change="(u: number | null) => changeAssignee(row, u)"
          >
            <el-option v-for="m in members" :key="m.userId" :label="`#${m.userId} ${m.role}`" :value="m.userId" />
          </el-select>
          <span v-else>{{ row.assigneeId ? `#${row.assigneeId}` : '未分派' }}</span>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > pager.size"
      class="issues-view__pager"
      layout="prev, pager, next, total"
      :total="total"
      :page-size="pager.size"
      :current-page="pager.page"
      @current-change="(p: number) => { pager.page = p; refresh() }"
    />

    <el-dialog v-model="dialogVisible" :title="editingId === null ? '新建 Issue' : '编辑 Issue'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="类型">
          <el-select v-model="form.type" :disabled="editingId !== null" style="width: 100%">
            <el-option
              v-for="t in ['BUG', 'TASK', 'FEATURE', 'IMPROVEMENT']"
              :key="t"
              :label="labelOf(ISSUE_TYPE_LABELS, t)"
              :value="t"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="Issue 标题" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="form.priority" style="width: 100%">
            <el-option
              v-for="p in ['LOW', 'MEDIUM', 'HIGH', 'URGENT']"
              :key="p"
              :label="labelOf(ISSUE_PRIORITY_LABELS, p)"
              :value="p"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.type === 'BUG'" label="严重程度">
          <el-select v-model="form.severity" clearable placeholder="仅 BUG" style="width: 100%">
            <el-option
              v-for="s in ['S1', 'S2', 'S3', 'S4']"
              :key="s"
              :label="labelOf(ISSUE_SEVERITY_LABELS, s)"
              :value="s"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editingId !== null" label="经办人">
          <el-select v-model="form.assigneeId" clearable placeholder="未分派" style="width: 100%">
            <el-option v-for="m in members" :key="m.userId" :label="`#${m.userId} ${m.role}`" :value="m.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker
            v-model="form.dueDate"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择截止日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item v-if="labels.length" label="标签">
          <el-select v-model="form.labelIds" multiple placeholder="选择标签" style="width: 100%">
            <el-option v-for="l in labels" :key="l.id" :label="l.name" :value="l.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="submitting" @click="submit">
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 标签管理（V17）-->
    <el-dialog v-model="labelDialogVisible" title="标签管理" width="480px">
      <div class="issues-view__label-editor">
        <el-input v-model="labelForm.name" placeholder="标签名" maxlength="50" style="width: 160px" />
        <el-color-picker v-model="labelForm.color" />
        <el-button type="primary" size="small" :loading="labelSaving" @click="submitLabel">
          {{ labelForm.id === null ? '新增' : '保存修改' }}
        </el-button>
        <el-button v-if="labelForm.id !== null" size="small" @click="labelForm.id = null; labelForm.name = ''">
          取消编辑
        </el-button>
      </div>
      <div v-for="l in labels" :key="l.id" class="issues-view__label-row">
        <el-tag size="small" :color="l.color" style="border: none; color: #fff">{{ l.name }}</el-tag>
        <span class="issues-view__label-actions">
          <el-button link size="small" @click="editLabel(l)">编辑</el-button>
          <el-button link size="small" type="danger" @click="removeLabel(l)">删除</el-button>
        </span>
      </div>
      <div v-if="labels.length === 0" class="issues-view__label-empty">暂无标签</div>
    </el-dialog>

    <IssueDetailDrawer
      v-model:visible="drawerVisible"
      :project-id="projectId"
      :project-key="project?.key"
      :issue="detailIssue"
      @updated="onDetailUpdated"
      @edit="openEdit"
    />
  </section>
</template>

<style scoped>
.issues-view {
  max-width: 1100px;
  margin: 16px auto;
}
.issues-view__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.issues-view__actions {
  display: flex;
  gap: 8px;
}
.issues-view__label {
  margin-right: 4px;
}
.issues-view__due--overdue {
  color: #f56c6c;
  font-weight: 600;
}
.issues-view__due--soon {
  color: #e6a23c;
  font-weight: 600;
}
.issues-view__label-editor {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}
.issues-view__label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  border-bottom: 1px solid #f0f2f5;
}
.issues-view__label-empty {
  color: #c0c4cc;
  font-size: 13px;
  text-align: center;
  padding: 12px 0;
}
.issues-view__filters {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.issues-view__pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
