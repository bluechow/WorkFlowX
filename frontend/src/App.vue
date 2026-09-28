<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import NotificationBell from '@/components/notification/NotificationBell.vue'

const auth = useAuthStore()
const notificationStore = useNotificationStore()
const router = useRouter()

const displayName = computed(() => auth.currentUser?.nickname || auth.currentUser?.username || auth.username)
/**
 * 系统管理菜单显隐：由权限码驱动（P13 修复——原 isAdmin 依赖 login 响应的 roles，
 * 刷新后 roles 不恢复导致 ADMIN 菜单消失；权限码经 /auth/me/permissions 实时恢复）。
 * 纯 UX；后端 authority 才是安全边界（Master Prompt §7）。
 */
const hasAnySystemMenu = computed(() =>
  ['org:list', 'project:list', 'user:assign_role', 'role:list', 'permission:list', 'audit:list']
    .some((code) => auth.hasPermission(code)),
)

async function handleLogout() {
  await auth.logout()
  // P9-09: 登出清理通知状态；切换用户时不残留上一用户未读数
  notificationStore.reset()
  router.push('/login')
}
</script>

<template>
  <el-container class="app">
    <el-header class="app__header">
      <span class="app__brand">WorkFlowX</span>
      <el-menu v-if="hasAnySystemMenu" mode="horizontal" router class="app__menu" :ellipsis="false">
        <el-menu-item v-if="auth.hasPermission('org:list')" index="/system/organizations">
          组织管理
        </el-menu-item>
        <el-menu-item v-if="auth.hasPermission('project:list')" index="/system/projects">
          项目管理
        </el-menu-item>
        <el-menu-item v-if="auth.hasPermission('user:list')" index="/system/users">
          用户管理
        </el-menu-item>
        <el-menu-item v-if="auth.hasPermission('user:assign_role')" index="/system/user-roles">
          用户角色
        </el-menu-item>
        <el-menu-item v-if="auth.hasPermission('role:list')" index="/system/roles">
          角色管理
        </el-menu-item>
        <el-menu-item v-if="auth.hasPermission('permission:list')" index="/system/permissions">
          权限管理
        </el-menu-item>
        <el-menu-item v-if="auth.hasPermission('audit:list')" index="/system/audit">
          审计日志
        </el-menu-item>
      </el-menu>
      <span class="app__spacer" />
      <NotificationBell v-if="auth.isAuthenticated" />
      <router-link v-if="auth.isAuthenticated" to="/profile" class="app__user" title="个人中心">
        {{ displayName }}
      </router-link>
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
  text-decoration: none;
}
.app__user:hover {
  color: #409eff;
}
.app__login-link {
  font-size: 14px;
}
</style>
