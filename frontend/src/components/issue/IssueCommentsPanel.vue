<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createComment,
  deleteComment,
  listComments,
  updateComment,
  type CommentVO,
} from '@/api/comment'
import { useAuthStore } from '@/stores/auth'

/**
 * Issue 评论面板（P8-14）。权限 UX 用 auth store 权限码（后端 authority 才是安全边界）；
 * 编辑/删除按钮仅对自己的评论渲染（ownership 对齐 ADR-018：ADMIN 非 owner 也 403）。
 * 成功后重新从 API 拉取，禁止本地 push 冒充后端成功。
 */
const props = defineProps<{ projectId: number; issueId: number }>()
const auth = useAuthStore()

const comments = ref<CommentVO[]>([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const newContent = ref('')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const editContent = ref('')
const savingEdit = ref(false)
const MAX_LEN = 10000

async function refresh() {
  loading.value = true
  loadError.value = ''
  try {
    const page = await listComments(props.projectId, props.issueId)
    comments.value = page.list
    total.value = page.total
  } catch (e) {
    loadError.value = (e as { message?: string }).message ?? '评论加载失败'
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (submitting.value) return
  const content = newContent.value.trim()
  if (!content) {
    ElMessage.warning('评论内容不能为空')
    return
  }
  if (content.length > MAX_LEN) {
    ElMessage.warning(`评论最长 ${MAX_LEN} 字符`)
    return
  }
  submitting.value = true
  try {
    await createComment(props.projectId, props.issueId, content)
    newContent.value = ''
    ElMessage.success('评论已发布')
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '评论发布失败')
  } finally {
    submitting.value = false
  }
}

function startEdit(comment: CommentVO) {
  editingId.value = comment.id
  editContent.value = comment.content
}

async function saveEdit() {
  if (savingEdit.value || editingId.value === null) return
  const content = editContent.value.trim()
  if (!content) {
    ElMessage.warning('评论内容不能为空')
    return
  }
  savingEdit.value = true
  try {
    await updateComment(props.projectId, props.issueId, editingId.value, content)
    editingId.value = null
    ElMessage.success('评论已更新')
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '评论更新失败')
  } finally {
    savingEdit.value = false
  }
}

async function remove(comment: CommentVO) {
  try {
    await deleteComment(props.projectId, props.issueId, comment.id)
    ElMessage.success('评论已删除')
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '评论删除失败')
  }
}

onMounted(refresh)
</script>

<template>
  <div class="comments-panel">
    <div v-if="auth.hasPermission('comment:create')" class="comments-panel__editor">
      <el-input
        v-model="newContent"
        type="textarea"
        :rows="3"
        maxlength="10000"
        show-word-limit
        placeholder="写下评论…"
      />
      <el-button
        type="primary"
        size="small"
        :loading="submitting"
        :disabled="submitting || !newContent.trim()"
        class="comments-panel__submit"
        @click="submit"
      >
        发布评论
      </el-button>
    </div>

    <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" />
    <div v-else-if="loading" v-loading="loading" class="comments-panel__state" />
    <el-empty v-else-if="comments.length === 0" description="暂无评论" :image-size="60" />

    <ul v-else class="comments-panel__list">
      <li v-for="comment in comments" :key="comment.id" class="comments-panel__item">
        <template v-if="editingId === comment.id">
          <el-input v-model="editContent" type="textarea" :rows="2" maxlength="10000" />
          <div class="comments-panel__actions">
            <el-button size="small" :disabled="savingEdit" @click="editingId = null">取消</el-button>
            <el-button type="primary" size="small" :loading="savingEdit" :disabled="savingEdit" @click="saveEdit">
              保存
            </el-button>
          </div>
        </template>
        <template v-else>
          <div class="comments-panel__meta">
            <span>#{{ comment.authorId }}</span>
            <span class="comments-panel__time">{{ comment.createdAt }}</span>
          </div>
          <p class="comments-panel__content">{{ comment.content }}</p>
          <div
            v-if="auth.hasPermission('comment:update') || auth.hasPermission('comment:delete')"
            class="comments-panel__actions"
          >
            <el-button
              v-if="auth.hasPermission('comment:update')"
              link
              size="small"
              @click="startEdit(comment)"
            >
              编辑
            </el-button>
            <el-button
              v-if="auth.hasPermission('comment:delete')"
              link
              type="danger"
              size="small"
              @click="remove(comment)"
            >
              删除
            </el-button>
          </div>
        </template>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.comments-panel__editor {
  margin-bottom: 12px;
}
.comments-panel__submit {
  margin-top: 8px;
}
.comments-panel__state {
  min-height: 60px;
}
.comments-panel__list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.comments-panel__item {
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.comments-panel__meta {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.comments-panel__content {
  margin: 4px 0;
  white-space: pre-wrap;
  word-break: break-word;
}
.comments-panel__actions {
  display: flex;
  gap: 4px;
}
</style>
