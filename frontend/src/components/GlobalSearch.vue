<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { globalSearch, type IssueHit, type ProjectHit, type UserHit } from '@/api/search'
import { ISSUE_STATUS_LABELS, labelOf } from '@/utils/labels'

/** 顶栏全局搜索（Final Edition FP-6）：项目/工作项/用户，结果按权限由后端收敛。 */
const router = useRouter()
const keyword = ref('')
const visible = ref(false)
const loading = ref(false)
const projects = ref<ProjectHit[]>([])
const issues = ref<IssueHit[]>([])
const users = ref<UserHit[]>([])

let timer: ReturnType<typeof setTimeout> | null = null

function onInput() {
  if (timer) clearTimeout(timer)
  const kw = keyword.value.trim()
  if (!kw) {
    visible.value = false
    return
  }
  timer = setTimeout(async () => {
    loading.value = true
    visible.value = true
    try {
      const r = await globalSearch(kw)
      projects.value = r.projects
      issues.value = r.issues
      users.value = r.users
    } catch {
      projects.value = []
      issues.value = []
      users.value = []
    } finally {
      loading.value = false
    }
  }, 300)
}

const empty = () => !projects.value.length && !issues.value.length && !users.value.length

function goProject(p: ProjectHit) {
  visible.value = false
  router.push(`/projects/${p.id}/overview`)
}

function goIssue(i: IssueHit) {
  visible.value = false
  router.push(`/projects/${i.projectId}/issues`)
}
</script>

<template>
  <div class="gs" @click.outside="visible = false">
    <el-input
      v-model="keyword"
      placeholder="搜索项目 / 工作项 / 用户"
      clearable
      class="gs__input"
      :prefix-icon="undefined"
      @input="onInput"
      @focus="keyword.trim() && (visible = true)"
    >
      <template #prefix>🔍</template>
    </el-input>
    <div v-if="visible" class="gs__panel" v-loading="loading">
      <template v-if="projects.length">
        <div class="gs__group">项目</div>
        <div v-for="p in projects" :key="p.id" class="gs__item" @click="goProject(p)">
          <span class="gs__key">{{ p.key }}</span>{{ p.name }}
        </div>
      </template>
      <template v-if="issues.length">
        <div class="gs__group">工作项</div>
        <div v-for="i in issues" :key="i.id" class="gs__item" @click="goIssue(i)">
          <span class="gs__key">{{ i.projectKey }}-{{ i.issueNo }}</span>
          {{ i.title }}
          <el-tag size="small" type="info">{{ labelOf(ISSUE_STATUS_LABELS, i.status) }}</el-tag>
        </div>
      </template>
      <template v-if="users.length">
        <div class="gs__group">用户</div>
        <div v-for="u in users" :key="u.id" class="gs__item">
          <span class="gs__key">#{{ u.id }}</span>{{ u.nickname || u.username }}（{{ u.username }}）
        </div>
      </template>
      <div v-if="!loading && empty()" class="gs__empty">无匹配结果</div>
    </div>
  </div>
</template>

<style scoped>
.gs { position: relative; width: 280px; }
.gs__input :deep(.el-input__wrapper) { background: #f5f7fa; }
.gs__panel {
  position: absolute;
  top: 40px;
  left: 0;
  right: 0;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  z-index: 2000;
  max-height: 420px;
  overflow-y: auto;
  padding: 4px 0;
}
.gs__group {
  font-size: 12px;
  color: #909399;
  padding: 8px 14px 4px;
}
.gs__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 14px;
  font-size: 13px;
  cursor: pointer;
}
.gs__item:hover { background: #f5f7fa; }
.gs__key { color: #409eff; font-weight: 600; font-size: 12px; flex: none; }
.gs__item .el-tag { margin-left: auto; }
.gs__empty { color: #c0c4cc; font-size: 13px; text-align: center; padding: 18px 0; }
</style>
