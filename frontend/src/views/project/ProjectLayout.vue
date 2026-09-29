<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { listProjectOptions } from '@/api/issue'
import { useAuthStore } from '@/stores/auth'
import { PROJECT_STATUS_LABELS, labelOf } from '@/utils/labels'
import type { ProjectVO } from '@/types/api'

/**
 * 项目空间布局（Final Edition FP-0）：进入项目后的页签式工作区壳。
 * 页签按权限渲染（issue:list / testcase:list / testplan:list）；
 * 概览（FP-3）、计划（FP-4）、动态（FP-2）随各 FP 接入。
 */
const route = useRoute()
const auth = useAuthStore()
const projectId = computed(() => Number(route.params.projectId))
const project = ref<ProjectVO | null>(null)

const tabs = computed(() => {
  const t: { name: string; to: string; match: RegExp }[] = []
  t.push({ name: '概览', to: `/projects/${projectId.value}/overview`, match: /\/overview$/ })
  if (auth.hasPermission('issue:list')) {
    t.push({ name: '工作项', to: `/projects/${projectId.value}/issues`, match: /\/issues$/ })
    t.push({ name: '看板', to: `/projects/${projectId.value}/board`, match: /\/board$/ })
    t.push({ name: '动态', to: `/projects/${projectId.value}/activity`, match: /\/activity$/ })
  }
  if (auth.hasPermission('testcase:list') || auth.hasPermission('testplan:list')) {
    t.push({ name: '测试', to: `/projects/${projectId.value}/testcases`, match: /\/test(cases|plans)/ })
  }
  return t
})

onMounted(async () => {
  try {
    const all = await listProjectOptions()
    project.value = all.find((p) => p.id === projectId.value) ?? null
  } catch {
    project.value = null
  }
})
</script>

<template>
  <section class="proj-space">
    <div class="proj-space__head">
      <h2 class="proj-space__title">{{ project?.name ?? `项目 #${projectId}` }}</h2>
      <span v-if="project" class="proj-space__key">{{ project.key }}</span>
      <el-tag v-if="project" size="small" :type="project.status === 'ACTIVE' ? 'success' : 'info'">
        {{ labelOf(PROJECT_STATUS_LABELS, project.status) }}
      </el-tag>
    </div>
    <nav class="proj-space__tabs">
      <router-link
        v-for="t in tabs"
        :key="t.name"
        :to="t.to"
        class="proj-space__tab"
        :class="{ 'proj-space__tab--active': t.match.test($route.path) }"
      >
        {{ t.name }}
      </router-link>
    </nav>
    <div class="proj-space__body">
      <router-view />
    </div>
  </section>
</template>

<style scoped>
.proj-space {
  max-width: 1400px;
  margin: 0 auto;
  padding: 16px 24px 0;
}
.proj-space__head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.proj-space__title {
  margin: 0;
  font-size: 20px;
}
.proj-space__key {
  color: #909399;
  font-size: 13px;
  font-weight: 600;
}
.proj-space__tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid #dcdfe6;
  margin-bottom: 16px;
}
.proj-space__tab {
  padding: 8px 16px;
  font-size: 14px;
  color: #606266;
  text-decoration: none;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}
.proj-space__tab:hover {
  color: #409eff;
}
.proj-space__tab--active {
  color: #409eff;
  border-bottom-color: #409eff;
  font-weight: 600;
}
</style>
