<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/**
 * Dashboard（P2-16）: 登录后的首页占位。
 * 用户资料来自 /me（guard 已确认），后续业务页面在此扩展。
 */
const auth = useAuthStore()
const router = useRouter()

onMounted(() => {
  if (!auth.currentUser) {
    auth.fetchMe().catch(() => undefined) // 失效场景由守卫/拦截器处理
  }
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
