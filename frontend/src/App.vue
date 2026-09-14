<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const displayName = computed(() => auth.currentUser?.nickname || auth.currentUser?.username || auth.username)
/** 系统管理菜单仅对 ADMIN 展示——纯 UX；后端 authority 才是安全边界（Master Prompt §7） */
const isAdmin = computed(() => auth.roles.includes('ADMIN'))

async function handleLogout() {
  await auth.logout()
  router.push('/login')
}
</script>

<template>
  <el-container class="app">
    <el-header class="app__header">
      <span class="app__brand">WorkFlowX</span>
      <el-menu v-if="isAdmin" mode="horizontal" router class="app__menu" :ellipsis="false">
        <el-menu-item index="/system/user-roles">用户角色</el-menu-item>
        <el-menu-item index="/system/roles">角色管理</el-menu-item>
        <el-menu-item index="/system/permissions">权限管理</el-menu-item>
      </el-menu>
      <span class="app__spacer" />
      <span v-if="auth.isAuthenticated" class="app__user">{{ displayName }}</span>
      <el-button v-if="auth.isAuthenticated" link type="danger" @click="handleLogout">退出登录</el-button>
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
  gap: 16px;
  border-bottom: 1px solid #ebeef5;
}
.app__brand {
  font-size: 20px;
  font-weight: 600;
}
.app__menu {
  flex: 0 1 auto;
  border-bottom: none;
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
