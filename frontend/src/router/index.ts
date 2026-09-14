import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/health',
      name: 'health',
      component: () => import('@/views/HealthView.vue'),
      meta: { public: true },
    },
    { path: '/', redirect: '/dashboard' },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: () => import('@/views/DashboardView.vue'),
    },
    {
      path: '/system/roles',
      name: 'system-roles',
      component: () => import('@/views/system/RolesView.vue'),
    },
    {
      path: '/system/permissions',
      name: 'system-permissions',
      component: () => import('@/views/system/PermissionsView.vue'),
    },
    {
      path: '/system/user-roles',
      name: 'system-user-roles',
      component: () => import('@/views/system/UserRolesView.vue'),
    },
  ],
})

/**
 * 路由守卫（P2-17）。
 * - 公开路由放行；已登录访问 /login → 回 /dashboard
 * - 未登录访问受保护路由 → /login?redirect=<原目标>
 * - token 存在 ≠ 认证有效: 无 currentUser 时经 GET /auth/me 确认（后端会话/状态实时校验），
 *   失效则清理本地并回登录页（覆盖: 后端登出/二次登录覆盖/被禁用/JWT 过期）
 */
router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (to.meta.public) {
    if (auth.isAuthenticated && to.path === '/login') {
      return { path: '/dashboard' }
    }
    return true
  }

  if (!auth.isAuthenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (!auth.currentUser) {
    try {
      await auth.fetchMe()
    } catch {
      return { path: '/login', query: { reason: '401' } }
    }
  }
  return true
})

export default router
