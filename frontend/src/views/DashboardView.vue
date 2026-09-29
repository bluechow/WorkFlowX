<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { fetchDashboardOverview, type DashboardOverview } from '@/api/dashboard'
import { listMyTodoIssues, type TodoIssueVO } from '@/api/me'
import StatsSection from '@/components/dashboard/StatsSection.vue'
import {
  ISSUE_PRIORITY_LABELS,
  ISSUE_PRIORITY_TAG_TYPES,
  ISSUE_STATUS_LABELS,
  labelOf,
} from '@/utils/labels'

/**
 * Dashboard（Phase A-⑥ 升级）：登录后首页。
 * - 有 dashboard:view：自动加载统计（卡片+趋势+分布图），数据范围按角色收敛（后端语义）；
 * - 全部用户：我的待办（跨项目）+ 快捷入口；
 * - 个人资料已迁移至 /profile（个人中心），本页不再重复。
 */
const auth = useAuthStore()
const router = useRouter()

const overview = ref<DashboardOverview | null>(null)
const statsStatus = ref<'idle' | 'loading' | 'success' | 'failed'>('idle')
const statsError = ref('')
const hasStats = computed(() => auth.hasPermission('dashboard:view'))

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const todos = ref<TodoIssueVO[]>([])
const loadingTodos = ref(false)

async function loadStats() {
  if (!hasStats.value) return
  statsStatus.value = 'loading'
  try {
    overview.value = await fetchDashboardOverview()
    statsStatus.value = 'success'
  } catch (e) {
    statsStatus.value = 'failed'
    statsError.value = (e as { message?: string }).message ?? '统计数据加载失败'
  }
}

async function loadTodos() {
  loadingTodos.value = true
  try {
    todos.value = await listMyTodoIssues()
  } catch {
    todos.value = []
  } finally {
    loadingTodos.value = false
  }
}

function goProject(projectId: number) {
  router.push(`/projects/${projectId}/board`)
}

onMounted(async () => {
  if (!auth.currentUser) {
    auth.fetchMe().catch(() => undefined) // 失效场景由守卫/拦截器处理
  }
  await Promise.allSettled([loadStats(), loadTodos()])
})
</script>

<template>
  <section class="dash">
    <!-- 欢迎横幅 -->
    <div class="dash__welcome">
      <h1 class="dash__title">
        {{ greeting }}，{{ auth.currentUser?.nickname || auth.currentUser?.username || '' }}
      </h1>
      <p class="dash__sub">今天也要顺利交付 ✨</p>
    </div>

    <div class="dash__grid">
      <!-- 左列：统计（dashboard:view）或快捷入口 -->
      <div class="dash__main">
        <template v-if="hasStats">
          <el-alert
            v-if="statsStatus === 'failed'"
            type="error"
            :title="statsError"
            :closable="false"
          />
          <div v-else-if="statsStatus !== 'success'" v-loading="true" class="dash__loading" />
          <StatsSection v-else-if="overview" :overview="overview" />
        </template>
        <el-card v-else shadow="never" class="dash__quick">
          <h3>快捷入口</h3>
          <div class="dash__links">
            <el-button @click="router.push('/profile')">我的待办与个人中心</el-button>
            <el-button
              v-if="auth.hasPermission('project:list')"
              @click="router.push('/system/projects')"
            >
              项目列表
            </el-button>
          </div>
        </el-card>
      </div>

      <!-- 右列：我的待办 -->
      <el-card shadow="never" class="dash__todos">
        <div class="dash__todos-head">
          <h3>我的待办（{{ todos.length }}）</h3>
          <el-button link size="small" @click="router.push('/profile')">全部</el-button>
        </div>
        <p class="dash__todos-hint">指派给我且尚未完结（最多 5 条）</p>
        <div
          v-for="t in todos.slice(0, 5)"
          :key="t.issueId"
          class="dash__todo"
          @click="goProject(t.projectId)"
        >
          <div class="dash__todo-meta">
            <span class="dash__todo-no">{{ t.projectKey }}-{{ t.issueNo }}</span>
            <el-tag size="small" :type="(ISSUE_PRIORITY_TAG_TYPES[t.priority] as never) || 'info'">
              {{ labelOf(ISSUE_PRIORITY_LABELS, t.priority) }}
            </el-tag>
            <el-tag size="small" type="info">{{ labelOf(ISSUE_STATUS_LABELS, t.status) }}</el-tag>
          </div>
          <div class="dash__todo-title">{{ t.title }}</div>
        </div>
        <div v-if="todos.length === 0 && !loadingTodos" class="dash__empty">
          太棒了，没有待办 🎉
        </div>
      </el-card>
    </div>
  </section>
</template>

<style scoped>
.dash {
  max-width: 1200px;
  margin: 16px auto;
}
.dash__welcome {
  margin-bottom: 16px;
}
.dash__title {
  margin: 0;
  font-size: 22px;
}
.dash__sub {
  margin: 4px 0 0;
  color: #909399;
  font-size: 13px;
}
.dash__grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(260px, 1fr);
  gap: 16px;
  align-items: start;
}
.dash__loading {
  height: 280px;
}
.dash__quick h3,
.dash__todos-head h3 {
  margin: 0 0 10px;
  font-size: 15px;
}
.dash__links {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.dash__todos-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.dash__todos-hint {
  color: #909399;
  font-size: 12px;
  margin: 0 0 8px;
}
.dash__todo {
  border-bottom: 1px solid #f0f2f5;
  padding: 9px 4px;
  cursor: pointer;
}
.dash__todo:hover {
  background: #f5f7fa;
}
.dash__todo-meta {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 3px;
}
.dash__todo-no {
  color: #409eff;
  font-size: 12px;
  font-weight: 600;
}
.dash__todo-title {
  font-size: 13px;
  line-height: 1.4;
}
.dash__empty {
  color: #c0c4cc;
  font-size: 13px;
  text-align: center;
  padding: 24px 0;
}
</style>
