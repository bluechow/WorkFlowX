<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { useAuthStore } from '@/stores/auth'
import http from '@/api/http'
import type { Result } from '@/types/api'
import {
  ISSUE_SEVERITY_LABELS,
  ISSUE_STATUS_LABELS,
  labelOf,
} from '@/utils/labels'

/**
 * 数据分析（Final Edition FP-7）：成员工作量 + Bug 专项。
 * 权限=dashboard:view（无权限走快捷入口，不请求）。
 */
const auth = useAuthStore()
const router = useRouter()

interface MemberLoadVO {
  userId: number
  username: string | null
  nickname: string | null
  assigned: number
  open: number
  bugs: number
}

interface AnalyticsVO {
  members: MemberLoadVO[]
  bugs: { total: number; bySeverity: Record<string, number>; byStatus: Record<string, number> }
}

const analytics = ref<AnalyticsVO | null>(null)
const loading = ref(false)
const hasPerm = computed(() => auth.hasPermission('dashboard:view'))

const sevChartEl = ref<HTMLDivElement | null>(null)
const statusChartEl = ref<HTMLDivElement | null>(null)
let sevChart: echarts.ECharts | null = null
let statusChart: echarts.ECharts | null = null

const SEV_COLORS: Record<string, string> = { S1: '#f56c6c', S2: '#e6a23c', S3: '#409eff', S4: '#909399' }

function renderCharts() {
  if (!analytics.value || !sevChartEl.value || !statusChartEl.value) return
  sevChart?.dispose()
  sevChart = echarts.init(sevChartEl.value)
  sevChart.setOption({
    title: { text: 'Bug 严重程度分布', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie',
      radius: ['40%', '68%'],
      label: { formatter: '{b}: {c}' },
      data: Object.entries(analytics.value.bugs.bySeverity).map(([name, value]) => ({
        name: labelOf(ISSUE_SEVERITY_LABELS, name),
        value,
        itemStyle: { color: SEV_COLORS[name] ?? '#909399' },
      })),
    }],
  })
  statusChart?.dispose()
  statusChart = echarts.init(statusChartEl.value)
  statusChart.setOption({
    title: { text: 'Bug 状态分布', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie',
      radius: ['40%', '68%'],
      label: { formatter: '{b}: {c}' },
      data: Object.entries(analytics.value.bugs.byStatus).map(([name, value]) => ({
        name: labelOf(ISSUE_STATUS_LABELS, name),
        value,
      })),
    }],
  })
}

function onResize() {
  sevChart?.resize()
  statusChart?.resize()
}

async function refresh() {
  if (!hasPerm.value) return
  loading.value = true
  try {
    const { data: resp } = await http.get<Result<AnalyticsVO>>('/dashboard/analytics')
    analytics.value = resp.data ?? null
    requestAnimationFrame(renderCharts)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void refresh()
  window.addEventListener('resize', onResize)
})
</script>

<template>
  <section class="ana">
    <div class="ana__head">
      <h2>数据分析</h2>
      <el-button :loading="loading" @click="refresh">刷新</el-button>
    </div>

    <el-card v-if="!hasPerm" shadow="never">
      <el-empty description="需要「数据分析」权限（dashboard:view）" />
      <div class="ana__quick">
        <el-button @click="router.push('/work-items')">全局工作项</el-button>
        <el-button @click="router.push('/workspace')">返回工作台</el-button>
      </div>
    </el-card>

    <template v-else>
      <!-- 成员工作量 -->
      <el-card shadow="never" class="ana__section">
        <template #header><span class="ana__h">成员工作量（按经办工作项数排序，Top 30）</span></template>
        <el-table v-loading="loading" :data="analytics?.members ?? []" border empty-text="暂无数据">
          <el-table-column label="成员" min-width="140">
            <template #default="{ row }">
              {{ row.nickname || row.username || `#${row.userId}` }}
              <span class="ana__uname">@{{ row.username }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="assigned" label="经办总数" width="110" sortable />
          <el-table-column prop="open" label="未完结" width="110" sortable />
          <el-table-column prop="bugs" label="其中缺陷" width="110" sortable />
          <el-table-column label="负载条" min-width="200">
            <template #default="{ row }">
              <el-progress
                :percentage="Math.min(100, Math.round((row.open / Math.max(row.assigned, 1)) * 100))"
                :stroke-width="10"
                :color="row.open >= 8 ? '#f56c6c' : row.open >= 4 ? '#e6a23c' : '#67c23a'"
              />
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- Bug 专项 -->
      <div class="ana__charts">
        <el-card shadow="never">
          <template #header><span class="ana__h">缺陷专项（共 {{ analytics?.bugs.total ?? 0 }} 个）</span></template>
          <div ref="sevChartEl" class="ana__chart" />
        </el-card>
        <el-card shadow="never">
          <template #header><span class="ana__h">缺陷状态流转</span></template>
          <div ref="statusChartEl" class="ana__chart" />
        </el-card>
      </div>
    </template>
  </section>
</template>

<style scoped>
.ana { max-width: 1200px; margin: 0 auto; padding: 16px 24px 0; display: flex; flex-direction: column; gap: 16px; }
.ana__head { display: flex; align-items: center; justify-content: space-between; }
.ana__head h2 { margin: 0; font-size: 20px; }
.ana__h { font-weight: 600; font-size: 14px; }
.ana__section :deep(.el-card__header) { padding: 12px 20px; }
.ana__uname { color: #909399; font-size: 12px; margin-left: 4px; }
.ana__charts { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.ana__chart { height: 300px; }
.ana__quick { display: flex; justify-content: center; gap: 8px; margin-top: 8px; }
</style>
