<script setup lang="ts">
import { onMounted, ref, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { fetchDashboardOverview, type DashboardOverview } from '@/api/dashboard'
import StatsSection from '@/components/dashboard/StatsSection.vue'

/**
 * Dashboard（P2-16 基础 + P10-12 统计扩展）。
 * 用户资料来自 /me；统计数据来自 /dashboard/overview（真实业务聚合，dashboard:view 权限 UX）。
 */
const auth = useAuthStore()
const router = useRouter()

const overview = ref<DashboardOverview | null>(null)
const statsStatus = ref<'idle' | 'loading' | 'success' | 'failed'>('idle')
const statsError = ref('')
const statsVisible = ref(false)

async function loadStats() {
  statsVisible.value = true
  statsStatus.value = 'loading'
  statsError.value = ''
  try {
    overview.value = await fetchDashboardOverview()
    statsStatus.value = 'success'
  } catch (e) {
    statsStatus.value = 'failed'
    statsError.value = (e as { message?: string }).message ?? '统计数据加载失败'
  }
}

onMounted(() => {
  if (!auth.currentUser) {
    auth.fetchMe().catch(() => undefined) // 失效场景由守卫/拦截器处理
  }
})

onBeforeUnmount(() => {
  // ECharts 实例由 StatsSection 自行释放
})

async function handleLogout() {
  // 先尝试后端登出（尽力而为），无论结果都清理本地并回登录页
  await auth.logout()
  router.push('/login')
}
</script>

<template>
  <section class="dashboard">
    <h1>WorkFlowX Dashboard</h1>

    <el-alert
      v-if="auth.currentUser"
      type="success"
      :closable="false"
      show-icon
      :title="`欢迎回来，${auth.currentUser.nickname || auth.currentUser.username}`"
    />
    <el-skeleton v-else :rows="2" animated />

    <div class="dashboard__stats" data-test="stats-section">
      <div v-if="!statsVisible && auth.hasPermission('dashboard:view')" class="dashboard__stats-entry">
        <el-button type="primary" @click="loadStats">加载数据统计</el-button>
      </div>
      <template v-if="statsVisible">
        <el-alert v-if="statsStatus === 'failed'" type="error" :title="statsError" :closable="false" />
        <div v-else-if="statsStatus === 'loading'" v-loading="true" class="dashboard__stats-loading" />
        <StatsSection v-else-if="overview" :overview="overview" />
      </template>
    </div>

    <el-descriptions v-if="auth.currentUser" :column="1" border class="dashboard__detail">
      <el-descriptions-item label="username">{{ auth.currentUser.username }}</el-descriptions-item>
      <el-descriptions-item label="email">{{ auth.currentUser.email }}</el-descriptions-item>
      <el-descriptions-item label="status">{{ auth.currentUser.status }}</el-descriptions-item>
      <el-descriptions-item label="roles">{{ auth.roles.join(', ') || '（无）' }}</el-descriptions-item>
      <el-descriptions-item label="lastLoginAt">{{ auth.currentUser.lastLoginAt ?? '—' }}</el-descriptions-item>
    </el-descriptions>

    <el-button type="danger" plain @click="handleLogout">退出登录</el-button>
  </section>
</template>

<style scoped>
.dashboard {
  max-width: 640px;
  margin: 24px auto;
}
.dashboard__detail {
  margin: 16px 0;
}
</style>
