<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ALLOWED_EXTENSIONS,
  MAX_SIZE_BYTES,
  deleteAttachment,
  downloadAttachment,
  listAttachments,
  uploadAttachment,
  type AttachmentVO,
} from '@/api/attachment'
import { useAuthStore } from '@/stores/auth'

/**
 * Issue 附件面板（P8-15）。前端预检（扩展名/大小）仅为 UX；后端白名单与 10MB 限制才是安全边界。
 * 下载走后端鉴权接口（Blob），不接触对象存储凭据。成功后重新拉取列表。
 */
const props = defineProps<{ projectId: number; issueId: number }>()
const auth = useAuthStore()

const attachments = ref<AttachmentVO[]>([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const uploading = ref(false)
const progress = ref(0)
const fileInput = ref<HTMLInputElement | null>(null)

async function refresh() {
  loading.value = true
  loadError.value = ''
  try {
    const page = await listAttachments(props.projectId, props.issueId)
    attachments.value = page.list
    total.value = page.total
  } catch (e) {
    loadError.value = (e as { message?: string }).message ?? '附件加载失败'
  } finally {
    loading.value = false
  }
}

function pick() {
  fileInput.value?.click()
}

async function onFileChosen(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || uploading.value) return

  const extension = file.name.includes('.')
    ? file.name.split('.').pop()!.toLowerCase()
    : ''
  if (!ALLOWED_EXTENSIONS.includes(extension as never)) {
    ElMessage.warning(`不支持的文件类型: ${extension || '(无扩展名)'}`)
    return
  }
  if (file.size <= 0) {
    ElMessage.warning('不能上传空文件')
    return
  }
  if (file.size > MAX_SIZE_BYTES) {
    ElMessage.warning(`文件超过 ${MAX_SIZE_BYTES / 1024 / 1024}MB 限制`)
    return
  }

  uploading.value = true
  progress.value = 0
  try {
    await uploadAttachment(props.projectId, props.issueId, file, (p) => {
      progress.value = p
    })
    ElMessage.success('附件已上传')
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '附件上传失败')
  } finally {
    uploading.value = false
  }
}

async function download(attachment: AttachmentVO) {
  try {
    await downloadAttachment(props.projectId, props.issueId, attachment)
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '附件下载失败')
  }
}

async function remove(attachment: AttachmentVO) {
  try {
    await deleteAttachment(props.projectId, props.issueId, attachment.id)
    ElMessage.success('附件已删除')
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '附件删除失败')
  }
}

function formatSize(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)}MB`
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(0)}KB`
  return `${bytes}B`
}

onMounted(refresh)
</script>

<template>
  <div class="attachments-panel">
    <input
      ref="fileInput"
      type="file"
      class="attachments-panel__input"
      :accept="ALLOWED_EXTENSIONS.map((e) => '.' + e).join(',')"
      @change="onFileChosen"
    />
    <div class="attachments-panel__toolbar">
      <el-button
        v-if="auth.hasPermission('attachment:upload')"
        type="primary"
        size="small"
        :loading="uploading"
        :disabled="uploading"
        @click="pick"
      >
        上传附件
      </el-button>
      <span class="attachments-panel__hint">支持 {{ ALLOWED_EXTENSIONS.join('/') }}，≤10MB</span>
    </div>
    <el-progress
      v-if="uploading"
      :percentage="progress"
      class="attachments-panel__progress"
    />

    <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" />
    <div v-else-if="loading" v-loading="loading" class="attachments-panel__state" />
    <el-empty v-else-if="attachments.length === 0" description="暂无附件" :image-size="60" />

    <ul v-else class="attachments-panel__list">
      <li v-for="item in attachments" :key="item.id" class="attachments-panel__item">
        <span class="attachments-panel__name">{{ item.fileName }}</span>
        <span class="attachments-panel__size">{{ formatSize(item.fileSize) }}</span>
        <span class="attachments-panel__meta">#{{ item.uploaderId }} · {{ item.createdAt }}</span>
        <span class="attachments-panel__actions">
          <el-button
            v-if="auth.hasPermission('attachment:get')"
            link
            type="primary"
            size="small"
            @click="download(item)"
          >
            下载
          </el-button>
          <el-button
            v-if="auth.hasPermission('attachment:delete')"
            link
            type="danger"
            size="small"
            @click="remove(item)"
          >
            删除
          </el-button>
        </span>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.attachments-panel__input {
  display: none;
}
.attachments-panel__toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.attachments-panel__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.attachments-panel__progress {
  margin-bottom: 8px;
}
.attachments-panel__state {
  min-height: 60px;
}
.attachments-panel__list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.attachments-panel__item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 13px;
}
.attachments-panel__name {
  flex: 1;
  word-break: break-all;
}
.attachments-panel__size,
.attachments-panel__meta {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.attachments-panel__actions {
  display: flex;
  gap: 4px;
}
</style>
