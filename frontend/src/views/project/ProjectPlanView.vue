<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createMilestone,
  deleteMilestone,
  listMilestones,
  updateMilestone,
  type MilestoneVO,
} from '@/api/milestone'
import { listIssuesForBoard, updateIssue } from '@/api/issue'
import type { IssueVO } from '@/types/api'
import { useAuthStore } from '@/stores/auth'
import { ISSUE_STATUS_LABELS, labelOf } from '@/utils/labels'

/**
 * 项目计划（Final Edition FP-4）：里程碑 + 到期视图 + 简易时间线。
 * 里程碑 CRUD=project:update；工作项归属经 issue:update（0=清除哨兵）。
 */
const route = useRoute()
const auth = useAuthStore()
const projectId = computed(() => Number(route.params.projectId))

const milestones = ref<MilestoneVO[]>([])
const issues = ref<IssueVO[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const form = reactive({
  name: '',
  description: '',
  dueDate: null as string | null,
})

const canManage = computed(() => auth.hasPermission('project:update'))

/** 无里程碑的未完结带截止工作项（到期视图） */
const dueItems = computed(() =>
  issues.value
    .filter((i) => i.dueDate && !['CLOSED', 'RESOLVED'].includes(i.status))
    .sort((a, b) => a.dueDate!.localeCompare(b.dueDate!)),
)

function dueClass(due: string): string {
  const days = Math.ceil((new Date(due).getTime() - Date.now()) / 86400000)
  if (days < 0) return 'plan__due--overdue'
  if (days <= 3) return 'plan__due--soon'
  return ''
}

function fmt(t: string | null): string {
  return t ? t.replace('T', ' ').slice(0, 10) : '—'
}

async function refresh() {
  loading.value = true
  try {
    const [ms, board] = await Promise.all([
      listMilestones(projectId.value),
      listIssuesForBoard(projectId.value),
    ])
    milestones.value = ms
    issues.value = board
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '加载计划失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.name = ''
  form.description = ''
  form.dueDate = null
  dialogVisible.value = true
}

function openEdit(m: MilestoneVO) {
  editingId.value = m.id
  form.name = m.name
  form.description = m.description ?? ''
  form.dueDate = m.dueDate
  dialogVisible.value = true
}

async function submit() {
  if (!form.name.trim() || submitting.value) return
  submitting.value = true
  try {
    const payload = {
      name: form.name.trim(),
      description: form.description.trim() || null,
      dueDate: form.dueDate || null,
    }
    if (editingId.value === null) {
      await createMilestone(projectId.value, payload)
      ElMessage.success('里程碑已创建')
    } else {
      await updateMilestone(projectId.value, editingId.value, payload)
      ElMessage.success('里程碑已更新')
    }
    dialogVisible.value = false
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '保存失败')
  } finally {
    submitting.value = false
  }
}

async function toggleDone(m: MilestoneVO) {
  try {
    await updateMilestone(projectId.value, m.id, {
      name: m.name,
      description: m.description,
      dueDate: m.dueDate,
      status: m.status === 'DONE' ? 'OPEN' : 'DONE',
    })
    ElMessage.success(m.status === 'DONE' ? '里程碑已重新开启' : '里程碑已完成')
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '操作失败')
  }
}

async function remove(m: MilestoneVO) {
  try {
    await ElMessageBox.confirm(
      `确认删除里程碑「${m.name}」？归属的工作项将退回未归属（不会删除）。`,
      '确认操作',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteMilestone(projectId.value, m.id)
    ElMessage.success('里程碑已删除')
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '删除失败')
  }
}

async function assignIssue(issue: IssueVO, milestoneId: number | null) {
  try {
    await updateIssue(projectId.value, issue.id, { milestoneId: milestoneId ?? 0 })
    ElMessage.success('归属已更新')
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '更新归属失败')
  }
}

onMounted(refresh)
</script>

<template>
  <div v-loading="loading" class="plan">
    <!-- 里程碑时间线 -->
    <el-card shadow="never" class="plan__section">
      <template #header>
        <div class="plan__head-row">
          <span class="plan__title">里程碑</span>
          <el-button v-if="canManage" type="primary" size="small" @click="openCreate">新建里程碑</el-button>
        </div>
      </template>
      <el-empty v-if="milestones.length === 0" description="暂无里程碑" :image-size="60" />
      <el-timeline v-else>
        <el-timeline-item
          v-for="m in milestones"
          :key="m.id"
          :type="m.status === 'DONE' ? 'success' : 'primary'"
          :hollow="m.status !== 'DONE'"
          :timestamp="m.dueDate ? '目标 ' + fmt(m.dueDate) : '目标日期未定'"
          placement="top"
        >
          <div class="plan__ms">
            <div class="plan__ms-head">
              <span class="plan__ms-name" :class="{ 'plan__ms-name--done': m.status === 'DONE' }">
                {{ m.name }}
              </span>
              <el-tag v-if="m.status === 'DONE'" size="small" type="success">已完成</el-tag>
              <span class="plan__ms-actions" v-if="canManage">
                <el-button link size="small" @click="openEdit(m)">编辑</el-button>
                <el-button link size="small" :type="m.status === 'DONE' ? 'warning' : 'success'" @click="toggleDone(m)">
                  {{ m.status === 'DONE' ? '重新开启' : '标记完成' }}
                </el-button>
                <el-button link size="small" type="danger" @click="remove(m)">删除</el-button>
              </span>
            </div>
            <p v-if="m.description" class="plan__ms-desc">{{ m.description }}</p>
            <div class="plan__ms-progress">
              <el-progress
                :percentage="m.totalIssues === 0 ? 0 : Math.round((m.doneIssues / m.totalIssues) * 100)"
                :stroke-width="10"
                class="plan__progress"
              />
              <span class="plan__ms-count">{{ m.doneIssues }}/{{ m.totalIssues }} 工作项已完结</span>
            </div>
            <!-- 归属该里程碑的工作项 -->
            <div v-for="i in issues.filter(x => x.milestoneId === m.id)" :key="i.id" class="plan__ms-issue">
              <span class="plan__ms-issue-no">#{{ i.issueNo }}</span>
              <span class="plan__ms-issue-title">{{ i.title }}</span>
              <el-tag size="small" type="info">{{ labelOf(ISSUE_STATUS_LABELS, i.status) }}</el-tag>
              <el-button
                v-if="canManage"
                link
                size="small"
                type="danger"
                title="移出里程碑"
                @click="assignIssue(i, null)"
              >
                移出
              </el-button>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <!-- 到期视图 -->
    <el-card shadow="never" class="plan__section">
      <template #header><span class="plan__title">到期工作项（未完结 · 按截止日期）</span></template>
      <el-empty v-if="dueItems.length === 0" description="暂无带截止日期的未完结工作项" :image-size="60" />
      <div v-for="i in dueItems" :key="i.id" class="plan__due-row">
        <span class="plan__due-date" :class="dueClass(i.dueDate!)">{{ fmt(i.dueDate) }}</span>
        <span class="plan__due-no">#{{ i.issueNo }}</span>
        <span class="plan__due-title">{{ i.title }}</span>
        <el-select
          v-if="canManage"
          :model-value="i.milestoneId ?? 0"
          size="small"
          style="width: 150px"
          placeholder="归属里程碑"
          @change="(v: number) => assignIssue(i, v === 0 ? null : v)"
        >
          <el-option label="未归属" :value="0" />
          <el-option v-for="m in milestones" :key="m.id" :label="m.name" :value="m.id" />
        </el-select>
      </div>
    </el-card>

    <!-- 里程碑编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editingId === null ? '新建里程碑' : '编辑里程碑'" width="480px">
      <el-form label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="form.name" maxlength="100" placeholder="如 V1.0 发布" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
        <el-form-item label="目标日期">
          <el-date-picker
            v-model="form.dueDate"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择目标日期"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.plan {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.plan__section :deep(.el-card__header) {
  padding: 12px 20px;
}
.plan__head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.plan__title {
  font-weight: 600;
  font-size: 14px;
}
.plan__ms-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.plan__ms-name {
  font-size: 15px;
  font-weight: 600;
}
.plan__ms-name--done {
  text-decoration: line-through;
  color: #909399;
}
.plan__ms-actions {
  margin-left: auto;
}
.plan__ms-desc {
  margin: 6px 0;
  color: #606266;
  font-size: 13px;
}
.plan__ms-progress {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 6px 0;
}
.plan__progress {
  flex: 1;
  max-width: 260px;
}
.plan__ms-count {
  color: #909399;
  font-size: 12px;
}
.plan__ms-issue {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 13px;
  border-bottom: 1px dashed #f0f2f5;
}
.plan__ms-issue-no {
  color: #409eff;
  font-weight: 600;
  font-size: 12px;
}
.plan__ms-issue-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.plan__due-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 0;
  border-bottom: 1px solid #f5f7fa;
  font-size: 13px;
}
.plan__due-date {
  flex: none;
  width: 90px;
  font-weight: 600;
}
.plan__due--overdue { color: #f56c6c; }
.plan__due--soon { color: #e6a23c; }
.plan__due-no {
  color: #409eff;
  font-weight: 600;
  font-size: 12px;
  flex: none;
}
.plan__due-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
