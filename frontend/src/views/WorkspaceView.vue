<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { fetchDashboardOverview, type DashboardOverview } from '@/api/dashboard'
import { listMyTodoIssues, type TodoIssueVO } from '@/api/me'
import { listMyProjects, listMyActivities, fetchWorkSummary, type MyProjectVO, type WorkSummaryVO } from '@/api/workspace'
import type { ActivityVO } from '@/api/activity'
import StatsSection from '@/components/dashboard/StatsSection.vue'
import {
  ISSUE_PRIORITY_LABELS,
  ISSUE_PRIORITY_TAG_TYPES,
  ISSUE_STATUS_LABELS,
  labelOf,
} from '@/utils/labels'

/**
 * 我的工作台（Final Edition FP-5）：登录首页。
 * 组成：问候 + 我的待办/工作项三视图计数 + 我的项目 + 我最近的操作动态
 * + 快捷操作 +（有 dashboard:view 时）全局统计图表。
 * 全部数据来自真实接口；无「最近访问」埋点，以「我加入的项目」承载（诚实原则）。
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
const summary = ref<WorkSummaryVO>({ assigned: 0, todo: 0, created: 0 })
const myProjects = ref<MyProjectVO[]>([])
const myActivities = ref<ActivityVO[]>([])
const loading = ref(false)

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

function goProject(projectId: number) {
  router.push(`/projects/${projectId}/overview`)
}

function fmt(t: string | null | undefined): string {
  return t ? t.replace('T', ' ').slice(0, 16) : ''
}

onMounted(async () => {
  if (!auth.currentUser) {
    auth.fetchMe().catch(() => undefined)
  }
  loading.value = true
  const [todoP, summaryP, projectsP, activitiesP] = await Promise.allSettled([
    listMyTodoIssues(),
    fetchWorkSummary(),
    listMyProjects(),
    listMyActivities(10),
  ])
  todos.value = todoP.status === 'fulfilled' ? todoP.value : []
  summary.value = summaryP.status === 'fulfilled' ? summaryP.value : summary.value
  myProjects.value = projectsP.status === 'fulfilled' ? projectsP.value : []
  myActivities.value = activitiesP.status === 'fulfilled' ? activitiesP.value : []
  loading.value = false
  void loadStats()
})
</script>

<template>
  <section v-loading="loading" class="ws">
    <!-- 问候 + 快捷操作 -->
    <div class="ws__welcome">
      <div>
        <h1 class="ws__title">
          {{ greeting }}，{{ auth.currentUser?.nickname || auth.currentUser?.username || '' }}
        </h1>
        <p class="ws__sub">今天也要顺利交付 ✨</p>
      </div>
      <div class="ws__quick">
        <el-button v-if="auth.hasPermission('issue:list')" @click="router.push('/work-items')">全局工作项</el-button>
        <el-button v-if="auth.hasPermission('project:list')" type="primary" @click="router.push('/projects')">
          进入项目
        </el-button>
      </div>
    </div>

    <!-- 我的数字 -->
    <div class="ws__cards">
      <el-card shadow="never" class="ws__card" @click="router.push('/work-items')">
        <div class="ws__num">{{ summary.todo }}</div>
        <div class="ws__label">待我处理</div>
      </el-card>
      <el-card shadow="never" class="ws__card" @click="router.push('/work-items')">
        <div class="ws__num">{{ summary.assigned }}</div>
        <div class="ws__label">我的工作项</div>
      </el-card>
      <el-card shadow="never" class="ws__card" @click="router.push('/work-items')">
        <div class="ws__num">{{ summary.created }}</div>
        <div class="ws__label">我创建的</div>
      </el-card>
      <el-card shadow="never" class="ws__card">
        <div class="ws__num">{{ myProjects.length }}</div>
        <div class="ws__label">我加入的项目</div>
      </el-card>
    </div>

    <div class="ws__grid">
      <!-- 左列：全局统计（dashboard:view）或我的项目 -->
      <div class="ws__main">
        <template v-if="hasStats">
          <el-alert v-if="statsStatus === 'failed'" type="error" :title="statsError" :closable="false" />
          <div v-else-if="statsStatus !== 'success'" v-loading="true" class="ws__loading" />
          <StatsSection v-else-if="overview" :overview="overview" />
        </template>

        <!-- 我的项目 -->
        <el-card shadow="never" class="ws__section">
          <template #header><span class="ws__h">我加入的项目</span></template>
          <div v-for="p in myProjects" :key="p.id" class="ws__project" @click="goProject(p.id)">
            <span class="ws__project-key">{{ p.key }}</span>
            <span class="ws__project-name" :title="p.name">{{ p.name }}</span>
            <el-tag size="small" type="info" class="ws__project-role">{{ p.myRole === 'OWNER' ? '负责人' : p.myRole === 'MANAGER' ? '管理者' : '成员' }}</el-tag>
          </div>
          <div v-if="myProjects.length === 0" class="ws__empty">尚未加入任何项目</div>
        </el-card>
      </div>

      <!-- 右列：待办 + 我的动态 -->
      <div class="ws__side">
        <el-card shadow="never" class="ws__section">
          <template #header>
            <div class="ws__head-row">
              <span class="ws__h">我的待办（{{ todos.length }}）</span>
              <el-button link size="small" @click="router.push('/profile')">全部</el-button>
            </div>
          </template>
          <div v-for="t in todos.slice(0, 5)" :key="t.issueId" class="ws__todo" @click="goProject(t.projectId)">
            <div class="ws__todo-meta">
              <span class="ws__todo-no">{{ t.projectKey }}-{{ t.issueNo }}</span>
              <el-tag size="small" :type="(ISSUE_PRIORITY_TAG_TYPES[t.priority] as never) || 'info'">
                {{ labelOf(ISSUE_PRIORITY_LABELS, t.priority) }}
              </el-tag>
              <el-tag size="small" type="info">{{ labelOf(ISSUE_STATUS_LABELS, t.status) }}</el-tag>
            </div>
            <div class="ws__todo-title">{{ t.title }}</div>
          </div>
          <div v-if="todos.length === 0" class="ws__empty">太棒了，没有待办 🎉</div>
        </el-card>

        <el-card shadow="never" class="ws__section">
          <template #header><span class="ws__h">我最近的操作</span></template>
          <div v-for="a in myActivities" :key="a.id" class="ws__act">
            <span class="ws__act-summary">{{ a.summary }}</span>
            <span class="ws__act-time">{{ fmt(a.createdAt) }}</span>
          </div>
          <div v-if="myActivities.length === 0" class="ws__empty">暂无操作记录</div>
        </el-card>
      </div>
    </div>
  </section>
</template>

<style scoped>
.ws {
  max-width: 1240px;
  margin: 0 auto;
  padding: 16px 24px 0;
}
.ws__welcome {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.ws__title { margin: 0; font-size: 22px; }
.ws__sub { margin: 4px 0 0; color: #909399; font-size: 13px; }
.ws__quick { display: flex; gap: 8px; }
.ws__cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.ws__card { cursor: pointer; }
.ws__card :deep(.el-card__body) { text-align: center; }
.ws__num { font-size: 28px; font-weight: 600; }
.ws__label { color: #909399; font-size: 13px; margin-top: 4px; }
.ws__grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr);
  gap: 16px;
  align-items: start;
}
.ws__loading { height: 260px; }
.ws__section { margin-bottom: 16px; }
.ws__h { font-weight: 600; font-size: 14px; }
.ws__head-row { display: flex; align-items: center; justify-content: space-between; }
.ws__project {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 4px;
  border-bottom: 1px solid #f5f7fa;
  cursor: pointer;
  font-size: 13px;
}
.ws__project:hover { background: #f5f7fa; }
.ws__project-key {
  color: #409eff;
  font-weight: 600;
  flex: none;
  width: 64px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ws__project-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ws__project-role { flex: none; }
.ws__todo { padding: 9px 4px; border-bottom: 1px solid #f0f2f5; cursor: pointer; }
.ws__todo:hover { background: #f5f7fa; }
.ws__todo-meta { display: flex; gap: 6px; align-items: center; margin-bottom: 3px; }
.ws__todo-no { color: #409eff; font-size: 12px; font-weight: 600; }
.ws__todo-title { font-size: 13px; line-height: 1.4; }
.ws__act {
  display: flex;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px solid #f5f7fa;
  font-size: 12px;
  align-items: baseline;
}
.ws__act-summary { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
.ws__act-time { color: #909399; flex: none; }
.ws__empty { color: #c0c4cc; font-size: 13px; text-align: center; padding: 20px 0; }
</style>
