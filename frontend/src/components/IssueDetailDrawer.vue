<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createIssueLink,
  deleteIssueLink,
  listIssueLinks,
  listIssues,
  transitionIssueStatus,
  type IssueLinkVO,
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
import type { IssueStatus, IssueVO } from '@/types/api'

/**
 * Issue 详情抽屉（Phase A-③）：看板卡片 / 列表编号点击后右侧滑出。
 * 聚合：基本信息 + 状态流转 + 描述 + 评论/附件（复用 P8 面板组件，逻辑不重复实现）。
 * 流转按钮按 ADR-017 矩阵渲染，复用后端 transition 端点（权限/审计/通知全链路）。
 */
const props = defineProps<{
  projectId: number
  projectKey?: string
  issue: IssueVO | null
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  /** 流转后把最新 Issue 回传父级（父级原位刷新） */
  (e: 'updated', issue: IssueVO): void
  /** 请求父级打开编辑对话框 */
  (e: 'edit', issue: IssueVO): void
}>()

const auth = useAuthStore()

/** 与后端 ADR-017 矩阵一致；非法流转由后端 409 兜底 */
const ALLOWED: Record<IssueStatus, IssueStatus[]> = {
  OPEN: ['IN_PROGRESS'],
  IN_PROGRESS: ['RESOLVED'],
  RESOLVED: ['TESTING'],
  TESTING: ['CLOSED', 'REOPENED'],
  CLOSED: [],
  REOPENED: ['IN_PROGRESS'],
}
const targets = computed(() => (props.issue ? ALLOWED[props.issue.status] ?? [] : []))
const transitioning = ref(false)

// ===== 关联（V17）=====
const links = ref<IssueLinkVO[]>([])
const linksLoading = ref(false)
const linkDialogVisible = ref(false)
const linkForm = reactive({ targetId: undefined as number | undefined, type: 'RELATES' as 'RELATES' | 'BLOCKS' })
const linkableIssues = ref<{ id: number; issueNo: number; title: string; status: string }[]>([])
const linkSaving = ref(false)

async function loadLinks() {
  if (!props.issue) return
  linksLoading.value = true
  try {
    links.value = await listIssueLinks(props.projectId, props.issue.id)
  } catch {
    links.value = []
  } finally {
    linksLoading.value = false
  }
}

async function openLinkDialog() {
  if (!props.issue) return
  try {
    const page = await listIssues(props.projectId, { page: 1, size: 100 })
    linkableIssues.value = page.list
      .filter((i) => i.id !== props.issue!.id)
      .map((i) => ({ id: i.id, issueNo: i.issueNo, title: i.title, status: i.status }))
  } catch {
    linkableIssues.value = []
  }
  linkForm.targetId = undefined
  linkForm.type = 'RELATES'
  linkDialogVisible.value = true
}

async function submitLink() {
  if (!props.issue || !linkForm.targetId || linkSaving.value) return
  linkSaving.value = true
  try {
    links.value = await createIssueLink(props.projectId, props.issue.id, {
      targetIssueId: linkForm.targetId,
      linkType: linkForm.type,
    })
    linkDialogVisible.value = false
    ElMessage.success('关联已建立')
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '建立关联失败')
  } finally {
    linkSaving.value = false
  }
}

async function removeLink(link: IssueLinkVO) {
  if (!props.issue) return
  try {
    await deleteIssueLink(props.projectId, props.issue.id, link.linkId)
    links.value = links.value.filter((l) => l.linkId !== link.linkId)
    ElMessage.success('关联已解除')
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '解除关联失败')
  }
}

const LINK_TYPE_LABELS: Record<string, string> = { RELATES: '相关', BLOCKS: '阻塞' }

watch(
  () => [props.visible, props.issue?.id],
  ([visible]) => {
    if (visible && props.issue) {
      // 重开时清理上一单的流转状态
      transitioning.value = false
      void loadLinks()
    }
  },
)

async function doTransition(to: IssueStatus) {
  if (!props.issue || transitioning.value) return
  transitioning.value = true
  try {
    const updated = await transitionIssueStatus(
      props.projectId,
      props.issue.id,
      props.issue.status,
      to,
    )
    emit('updated', updated)
    ElMessage.success(`已流转为 ${ISSUE_STATUS_LABELS[to]}`)
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '状态流转失败')
  } finally {
    transitioning.value = false
  }
}

function fmt(time: string | null): string {
  return time ? time.replace('T', ' ').slice(0, 16) : '—'
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    size="500px"
    :with-header="false"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
  >
    <div v-if="issue" class="drawer">
      <!-- 头部：编号 + 状态 + 严重程度 -->
      <div class="drawer__head">
        <span class="drawer__no">{{ projectKey }}-{{ issue.issueNo }}</span>
        <el-tag size="small" type="info">{{ labelOf(ISSUE_STATUS_LABELS, issue.status) }}</el-tag>
        <el-tag v-if="issue.severity" size="small" type="warning">
          {{ labelOf(ISSUE_SEVERITY_LABELS, issue.severity) }}
        </el-tag>
      </div>
      <h3 class="drawer__title">{{ issue.title }}</h3>

      <!-- 状态流转 -->
      <div v-if="auth.hasPermission('issue:transition') && targets.length" class="drawer__flow">
        <span class="drawer__label">流转到</span>
        <el-button
          v-for="t in targets"
          :key="t"
          size="small"
          :type="t === 'REOPENED' ? 'warning' : 'primary'"
          :disabled="transitioning"
          @click="doTransition(t)"
        >
          {{ ISSUE_STATUS_LABELS[t] }}
        </el-button>
      </div>

      <!-- 基本信息 -->
      <dl class="drawer__meta">
        <div><dt>类型</dt><dd>{{ labelOf(ISSUE_TYPE_LABELS, issue.type) }}</dd></div>
        <div><dt>优先级</dt><dd>{{ labelOf(ISSUE_PRIORITY_LABELS, issue.priority) }}</dd></div>
        <div><dt>严重程度</dt><dd>{{ labelOf(ISSUE_SEVERITY_LABELS, issue.severity) }}</dd></div>
        <div><dt>报告人</dt><dd>#{{ issue.reporterId }}</dd></div>
        <div><dt>经办人</dt><dd>{{ issue.assigneeId ? `#${issue.assigneeId}` : '未分派' }}</dd></div>
        <div><dt>创建时间</dt><dd>{{ fmt(issue.createdAt) }}</dd></div>
        <div v-if="issue.dueDate"><dt>截止日期</dt><dd>{{ fmt(issue.dueDate) }}</dd></div>
      </dl>
      <div v-if="issue.labels && issue.labels.length" class="drawer__labels">
        <el-tag
          v-for="l in issue.labels"
          :key="l.id"
          size="small"
          :color="l.color"
          style="border: none; color: #fff"
        >
          {{ l.name }}
        </el-tag>
      </div>

      <!-- 描述 -->
      <div class="drawer__section">
        <h4>描述</h4>
        <p class="drawer__desc">{{ issue.description || '（无描述）' }}</p>
      </div>

      <!-- 评论 / 附件（复用 P8 面板） -->
      <div class="drawer__section">
        <h4>评论</h4>
        <IssueCommentsPanel :project-id="projectId" :issue-id="issue.id" />
      </div>
      <div class="drawer__section">
        <h4>附件</h4>
        <IssueAttachmentsPanel :project-id="projectId" :issue-id="issue.id" />
      </div>

      <!-- 关联工作项（V17）-->
      <div class="drawer__section">
        <div class="drawer__section-head">
          <h4>关联（{{ links.length }}）</h4>
          <el-button
            v-if="auth.hasPermission('issue:update')"
            link
            size="small"
            type="primary"
            @click="openLinkDialog"
          >
            添加关联
          </el-button>
        </div>
        <div v-for="l in links" :key="l.linkId" class="drawer__link">
          <el-tag size="small" :type="l.linkType === 'BLOCKS' ? 'danger' : 'info'">
            {{ LINK_TYPE_LABELS[l.linkType] }}
          </el-tag>
          <el-tag size="small" type="info">{{ l.direction === 'OUTGOING' ? '→' : '←' }}</el-tag>
          <span class="drawer__link-title">#{{ l.otherIssueNo }} {{ l.otherTitle }}</span>
          <span class="drawer__link-status">{{ labelOf(ISSUE_STATUS_LABELS, l.otherStatus) }}</span>
          <el-button
            v-if="auth.hasPermission('issue:update')"
            link
            size="small"
            type="danger"
            @click="removeLink(l)"
          >
            解除
          </el-button>
        </div>
        <div v-if="links.length === 0 && !linksLoading" class="drawer__empty">暂无关联</div>
      </div>

      <!-- 底部操作 -->
      <el-dialog v-model="linkDialogVisible" title="添加关联" width="420px" append-to-body>
        <el-form label-width="80px">
          <el-form-item label="目标工作项">
            <el-select v-model="linkForm.targetId" filterable placeholder="搜索选择同项目工作项" style="width: 100%">
              <el-option
                v-for="i in linkableIssues"
                :key="i.id"
                :label="`#${i.issueNo} ${i.title}`"
                :value="i.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="关联类型">
            <el-radio-group v-model="linkForm.type">
              <el-radio value="RELATES">相关</el-radio>
              <el-radio value="BLOCKS">阻塞</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="linkDialogVisible = false">取消</el-button>
          <el-button type="primary" :disabled="!linkForm.targetId" :loading="linkSaving" @click="submitLink">
            确定关联
          </el-button>
        </template>
      </el-dialog>

      <div class="drawer__footer">
        <el-button
          v-if="auth.hasPermission('issue:update')"
          size="small"
          @click="emit('edit', issue)"
        >
          编辑
        </el-button>
        <el-button size="small" @click="emit('update:visible', false)">关闭</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped>
.drawer {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.drawer__head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.drawer__no {
  color: #409eff;
  font-weight: 600;
  font-size: 13px;
}
.drawer__title {
  margin: 0;
  font-size: 16px;
  line-height: 1.4;
}
.drawer__flow {
  display: flex;
  align-items: center;
  gap: 8px;
}
.drawer__label {
  font-size: 12px;
  color: #909399;
}
.drawer__meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px 16px;
  margin: 0;
  background: #f5f7fa;
  border-radius: 6px;
  padding: 10px 12px;
}
.drawer__meta > div {
  display: flex;
  gap: 8px;
  align-items: baseline;
}
.drawer__meta dt {
  color: #909399;
  font-size: 12px;
  flex: none;
}
.drawer__meta dd {
  margin: 0;
  font-size: 13px;
}
.drawer__section h4 {
  margin: 0 0 8px;
  font-size: 14px;
}
.drawer__desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  color: #303133;
}
.drawer__labels {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.drawer__link {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 0;
  border-bottom: 1px solid #f0f2f5;
}
.drawer__link-title {
  flex: 1;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.drawer__link-status {
  color: #909399;
  font-size: 12px;
}
.drawer__footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  border-top: 1px solid #f0f2f5;
  padding-top: 12px;
}
</style>
