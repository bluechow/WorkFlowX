<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import NotificationBell from '@/components/notification/NotificationBell.vue'
import GlobalSearch from '@/components/GlobalSearch.vue'

const auth = useAuthStore()
const notificationStore = useNotificationStore()
const router = useRouter()

const displayName = computed(() => auth.currentUser?.nickname || auth.currentUser?.username || auth.username)

/**
 * 应用骨架（Final Edition FP-0）：左侧边栏 + 顶栏。
 * 菜单显隐一律权限码驱动（P13 先例）；纯 UX，后端 authority 才是安全边界。
 * 核心区：工作台/项目管理（工作项 FP-1、数据分析 FP-7 接入）；
 * 管理区：组织与团队、系统管理（仅相关权限可见）。
 */
/** 管理分组默认展开（用户可点击收起） */
const defaultOpeneds = ['team', 'system']

const hasTeamMenu = computed(() =>
  ['org:list', 'user:list', 'user:assign_role', 'role:list', 'permission:list']
    .some((code) => auth.hasPermission(code)),
)
const hasSystemMenu = computed(() =>
  ['user:status', 'audit:list'].some((code) => auth.hasPermission(code)),
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
    <!-- 侧边导航 -->
    <el-aside v-if="auth.isAuthenticated" width="208px" class="app__aside">
      <div class="app__brand" @click="router.push('/workspace')">
        <img src="/favicon.svg" alt="WorkFlowX" class="app__logo" />
        <span>WorkFlowX</span>
      </div>
      <el-menu router class="app__menu" :default-active="$route.path" :default-openeds="defaultOpeneds">
        <!-- 核心业务区：常驻平铺 -->
        <el-menu-item index="/workspace">工作台</el-menu-item>
        <el-menu-item v-if="auth.hasPermission('project:list')" index="/projects">项目管理</el-menu-item>
        <el-menu-item v-if="auth.hasPermission('issue:list')" index="/work-items">工作项</el-menu-item>
        <el-menu-item v-if="auth.hasPermission('dashboard:view')" index="/analytics">数据分析</el-menu-item>

        <!-- 管理区：可折叠分组（收起只显示大类） -->
        <el-sub-menu v-if="hasTeamMenu" index="team">
          <template #title>组织与团队</template>
          <el-menu-item v-if="auth.hasPermission('org:list')" index="/organizations">组织管理</el-menu-item>
          <el-menu-item v-if="auth.hasPermission('user:list')" index="/users">用户管理</el-menu-item>
          <el-menu-item v-if="auth.hasPermission('user:assign_role')" index="/user-roles">用户角色</el-menu-item>
          <el-menu-item v-if="auth.hasPermission('role:list')" index="/roles">角色管理</el-menu-item>
          <el-menu-item v-if="auth.hasPermission('permission:list')" index="/permissions">权限管理</el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="hasSystemMenu" index="system">
          <template #title>系统管理</template>
          <el-menu-item v-if="auth.hasPermission('user:status')" index="/sessions">在线会话</el-menu-item>
          <el-menu-item v-if="auth.hasPermission('audit:list')" index="/audit">审计日志</el-menu-item>
        </el-sub-menu>

        <el-menu-item index="/profile">个人中心</el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 顶栏 + 主内容 -->
    <el-container>
      <el-header class="app__header">
        <GlobalSearch v-if="auth.isAuthenticated" />
        <span class="app__spacer" />
        <NotificationBell v-if="auth.isAuthenticated" />
        <router-link v-if="auth.isAuthenticated" to="/profile" class="app__user" title="个人中心">
          {{ displayName }}
        </router-link>
        <el-button v-if="auth.isAuthenticated" link type="danger" @click="handleLogout">退出登录</el-button>
        <router-link v-else to="/login" class="app__login-link">登录</router-link>
      </el-header>
      <el-main class="app__main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.app {
  min-height: 100vh;
}
.app__aside {
  border-right: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  background: #fff;
}
.app__brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 700;
  padding: 18px 20px 12px;
  cursor: pointer;
  color: #409eff;
  letter-spacing: 0.5px;
}
.app__logo {
  width: 26px;
  height: 26px;
  border-radius: 6px;
}
.app__menu {
  border-right: none;
  flex: 1;
}
.app__header {
  display: flex;
  align-items: center;
  gap: 16px;
  border-bottom: 1px solid #ebeef5;
  background: #fff;
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
.app__main {
  background: #f5f7fa;
}
</style>
