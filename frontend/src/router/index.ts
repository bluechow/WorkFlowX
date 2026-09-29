import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/**
 * Final Edition FP-0 路由架构：
 * - 新路径：/workspace（工作台）、/projects/:id/*（项目空间，ProjectLayout 页签壳）、
 *   /organizations|/users|/user-roles|/roles|/permissions|/sessions|/audit（管理区）
 * - 旧 /system/* 与 /dashboard 路径全部 redirect 保留（E2E/书签兼容，零破坏）
 * - 概览（FP-3）/计划（FP-4）/动态（FP-2）随各 FP 在项目空间 children 中接入
 */
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
    { path: '/', redirect: '/workspace' },
    { path: '/dashboard', redirect: '/workspace' },
    {
      path: '/workspace',
      name: 'workspace',
      component: () => import('@/views/WorkspaceView.vue'),
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('@/views/ProfileView.vue'),
    },
    {
      path: '/work-items',
      name: 'work-items',
      component: () => import('@/views/WorkItemsView.vue'),
    },

    // ===== 项目空间（页签壳 + 子页） =====
    {
      path: '/projects',
      name: 'projects',
      component: () => import('@/views/system/ProjectsView.vue'),
    },
    {
      path: '/projects/:projectId',
      component: () => import('@/views/project/ProjectLayout.vue'),
      children: [
        { path: '', redirect: (to) => `/projects/${to.params.projectId}/overview` },
        {
          path: 'overview',
          name: 'project-overview',
          component: () => import('@/views/project/ProjectOverviewView.vue'),
        },
        {
          path: 'issues',
          name: 'project-issues',
          component: () => import('@/views/system/IssuesView.vue'),
        },
        {
          path: 'board',
          name: 'project-board',
          component: () => import('@/views/system/ProjectBoardView.vue'),
        },
        {
          path: 'activity',
          name: 'project-activity',
          component: () => import('@/views/project/ProjectActivityView.vue'),
        },
        {
          path: 'plan',
          name: 'project-plan',
          component: () => import('@/views/project/ProjectPlanView.vue'),
        },
        {
          path: 'testcases',
          name: 'project-testcases',
          component: () => import('@/views/system/TestCasesView.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'testplans',
          name: 'project-testplans',
          component: () => import('@/views/system/TestPlansView.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'testplans/:planId',
          name: 'project-testplan-detail',
          component: () => import('@/views/system/TestPlanDetailView.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'testplans/:planId/report',
          name: 'project-plan-report',
          component: () => import('@/views/system/TestPlanReportView.vue'),
          meta: { requiresAuth: true },
        },
      ],
    },

    // ===== 组织与团队 =====
    {
      path: '/organizations',
      name: 'organizations',
      component: () => import('@/views/system/OrganizationsView.vue'),
    },
    {
      path: '/organizations/:id',
      name: 'organization-detail',
      component: () => import('@/views/system/OrganizationDetailView.vue'),
    },
    {
      path: '/users',
      name: 'users',
      component: () => import('@/views/system/UsersView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/user-roles',
      name: 'user-roles',
      component: () => import('@/views/system/UserRolesView.vue'),
    },
    {
      path: '/roles',
      name: 'roles',
      component: () => import('@/views/system/RolesView.vue'),
    },
    {
      path: '/permissions',
      name: 'permissions',
      component: () => import('@/views/system/PermissionsView.vue'),
    },

    // ===== 系统管理 =====
    {
      path: '/sessions',
      name: 'sessions',
      component: () => import('@/views/system/SessionsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/audit',
      name: 'audit',
      component: () => import('@/views/system/AuditView.vue'),
      meta: { requiresAuth: true },
    },

    // ===== 旧路径兼容重定向（/system/* → 新路径；书签与 E2E 兼容） =====
    { path: '/system/projects/:projectId/issues', redirect: (to) => `/projects/${to.params.projectId}/issues` },
    { path: '/system/projects/:projectId/board', redirect: (to) => `/projects/${to.params.projectId}/board` },
    { path: '/system/projects/:projectId/testcases', redirect: (to) => `/projects/${to.params.projectId}/testcases` },
    { path: '/system/projects/:projectId/testplans/:planId/report', redirect: (to) => `/projects/${to.params.projectId}/testplans/${to.params.planId}/report` },
    { path: '/system/projects/:projectId/testplans/:planId', redirect: (to) => `/projects/${to.params.projectId}/testplans/${to.params.planId}` },
    { path: '/system/projects/:projectId/testplans', redirect: (to) => `/projects/${to.params.projectId}/testplans` },
    { path: '/system/projects', redirect: '/projects' },
    { path: '/system/organizations/:id', redirect: (to) => `/organizations/${to.params.id}` },
    { path: '/system/organizations', redirect: '/organizations' },
    { path: '/system/users', redirect: '/users' },
    { path: '/system/user-roles', redirect: '/user-roles' },
    { path: '/system/roles', redirect: '/roles' },
    { path: '/system/permissions', redirect: '/permissions' },
    { path: '/system/sessions', redirect: '/sessions' },
    { path: '/system/audit', redirect: '/audit' },

    {
      // P11-03: 未知路由统一兜底 404（不出现空白页）
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
    },
  ],
})

/**
 * 路由守卫（P2-17）。
 * - 公开路由放行；已登录访问 /login → 回工作台
 * - 未登录访问受保护路由 → /login?redirect=<原目标>
 * - token 存在 ≠ 认证有效: 无 currentUser 时经 GET /auth/me 确认（后端会话/状态实时校验），
 *   失效则清理本地并回登录页（覆盖: 后端登出/二次登录覆盖/被禁用/JWT 过期）
 */
router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (to.meta.public) {
    if (auth.isAuthenticated && to.path === '/login') {
      return { path: '/workspace' }
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
