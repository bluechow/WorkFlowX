<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const displayName = computed(() => auth.currentUser?.nickname || auth.currentUser?.username || auth.username)

async function handleLogout() {
  await auth.logout()
  router.push('/login')
}
</script>

<template>
  <el-container class="app">
    <el-header class="app__header">
      <span class="app__brand">WorkFlowX</span>
      <span class="app__subtitle">项目协作与工单管理平台</span>
      <span class="app__spacer" />
      <template v-if="auth.isAuthenticated">
        <span class="app__user">{{ displayName }}</span>
        <el-button link type="danger" @click="handleLogout">退出登录</el-button>
      </template>
      <router-link v-else to="/login" class="app__login-link">登录</router-link>
    </el-header>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.app__header {
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid #ebeef5;
}
.app__brand {
  font-size: 20px;
  font-weight: 600;
}
.app__subtitle {
  color: #909399;
  font-size: 13px;
}
.app__spacer {
  flex: 1;
}
.app__user {
  color: #606266;
  font-size: 14px;
}
.app__login-link {
  font-size: 14px;
}
</style>
