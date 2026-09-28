<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { DashboardOverview } from '@/api/dashboard'
import { ISSUE_STATUS_LABELS, labelOf } from '@/utils/labels'

/**
 * 统计区块（P10-12）：指标卡片 + Issue 状态分布（饼图）+ 近 14 天创建趋势（折线）。
 * 全部数据来自后端真实聚合（/dashboard/overview），ECharts 渲染；无静态假数据。
 */
const props = defineProps<{ overview: DashboardOverview }>()

const statusChartEl = ref<HTMLDivElement | null>(null)
const trendChartEl = ref<HTMLDivElement | null>(null)
let statusChart: echarts.ECharts | null = null
let trendChart: echarts.ECharts | null = null

function renderCharts() {
  if (statusChartEl.value) {
    statusChart?.dispose()
    statusChart = echarts.init(statusChartEl.value)
    statusChart.setOption({
      title: { text: 'Issue 状态分布', left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'item' },
      series: [
        {
          type: 'pie',
          radius: '62%',
          data: Object.entries(props.overview.issues.byStatus).map(([name, value]) => ({
            name: labelOf(ISSUE_STATUS_LABELS, name),
            value,
          })),
          label: { formatter: '{b}: {c}' },
        },
      ],
    })
  }
  if (trendChartEl.value) {
    trendChart?.dispose()
    trendChart = echarts.init(trendChartEl.value)
    trendChart.setOption({
      title: { text: '近 14 天 Issue 创建趋势', left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: props.overview.createdTrend.map((p) => p.date.slice(5)) },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        {
          type: 'line',
          data: props.overview.createdTrend.map((p) => p.created),
          smooth: true,
          areaStyle: {},
        },
      ],
    })
  }
}

function onResize() {
  statusChart?.resize()
  trendChart?.resize()
}

watch(
  () => props.overview,
  () => renderCharts(),
  { deep: true },
)

onMounted(() => {
  renderCharts()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  statusChart?.dispose()
  trendChart?.dispose()
})
</script>

<template>
  <div class="stats-section" data-test="stats-content">
    <div class="stats-section__cards">
      <el-card class="stats-section__card" data-test="card-projects">
        <div class="stats-section__num">{{ overview.projects.total }}</div>
        <div class="stats-section__label">项目总数</div>
      </el-card>
      <el-card class="stats-section__card" data-test="card-active">
        <div class="stats-section__num">{{ overview.projects.active }}</div>
        <div class="stats-section__label">活跃项目</div>
      </el-card>
      <el-card class="stats-section__card" data-test="card-issues">
        <div class="stats-section__num">{{ overview.issues.total }}</div>
        <div class="stats-section__label">Issue 总数</div>
      </el-card>
      <el-card class="stats-section__card" data-test="card-bugs">
        <div class="stats-section__num">{{ overview.issues.bugCount }}</div>
        <div class="stats-section__label">BUG 数量</div>
      </el-card>
    </div>

    <el-empty
      v-if="overview.issues.total === 0"
      description="暂无 Issue 数据"
      :image-size="60"
    />
    <div v-else class="stats-section__charts">
      <div ref="statusChartEl" class="stats-section__chart" />
      <div ref="trendChartEl" class="stats-section__chart" />
    </div>
  </div>
</template>

<style scoped>
.stats-section__cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.stats-section__card {
  text-align: center;
}
.stats-section__num {
  font-size: 28px;
  font-weight: 600;
}
.stats-section__label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin-top: 4px;
}
.stats-section__charts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.stats-section__chart {
  height: 280px;
}
@media (max-width: 900px) {
  .stats-section__cards {
    grid-template-columns: repeat(2, 1fr);
  }
  .stats-section__charts {
    grid-template-columns: 1fr;
  }
}
</style>
