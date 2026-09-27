<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  allowedTargets,
  createIssue,
  listIssues,
  listProjectOptions,
  transitionIssueStatus,
  updateIssue,
} from '@/api/issue'
import IssueCommentsPanel from '@/components/issue/IssueCommentsPanel.vue'
import IssueAttachmentsPanel from '@/components/issue/IssueAttachmentsPanel.vue'
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
})

const members = ref<{ userId: number; role: string }[]>([])
const projectOptions = ref<ProjectVO[]>([])

// P8-14/15: 评论与附件抽屉（点击编号打开，tab 切换）
const drawerVisible = ref(false)
const activeIssue = ref<IssueVO | null>(null)

function openPanels(issue: IssueVO) {
  activeIssue.value = issue
  drawerVisible.value = true
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
  await loadMembers()
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
  await loadMembers()
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
      })
      ElMessage.success('Issue 已创建')
    } else {
      await updateIssue(projectId.value, editingId.value, {
        title: form.title.trim(),
        description: form.description.trim() || undefined,
        priority: form.priority as never,
        severity: form.type === 'BUG' ? (form.severity as never) : null,
        assigneeId: form.assigneeId ?? null,
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
  await loadMembers()
})
</script>

<template>
  <section class="issues-view">
    <div class="issues-view__toolbar">
      <h2>Issues — {{ project?.name ?? `#${projectId}` }}</h2>
      <div class="issues-view__actions">
        <el-button @click="router.push(`/system/projects/${projectId}/board`)">看板视图</el-button>
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
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="submitting" @click="submit">
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="drawerVisible"
      :title="activeIssue ? `${project?.key ?? ''}-${activeIssue.issueNo} ${activeIssue.title}` : ''"
      size="480px"
    >
      <el-tabs v-if="activeIssue" model-value="comments">
        <el-tab-pane label="评论" name="comments">
          <IssueCommentsPanel :project-id="projectId" :issue-id="activeIssue.id" />
        </el-tab-pane>
        <el-tab-pane label="附件" name="attachments">
          <IssueAttachmentsPanel :project-id="projectId" :issue-id="activeIssue.id" />
        </el-tab-pane>
      </el-tabs>
    </el-drawer>
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
