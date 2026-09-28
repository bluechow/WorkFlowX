<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import { getTestPlan, listPlanItems, type TestPlanItemVO, type TestPlanVO } from '@/api/testplan'
import { getIssue } from '@/api/issue'
import {
  ITEM_RESULT_LABELS,
  ITEM_RESULT_TAG_TYPES,
  labelOf,
} from '@/utils/labels'

/**
 * 测试报告页（Phase A-⑦）：一份计划的完整执行报告。
 * 数据全部来自真实接口（计划条目 + 关联 Issue），ECharts 渲染结果分布。
 */
const route = useRoute()
const projectId = computed(() => Number(route.params.projectId))
const planId = computed(() => Number(route.params.planId))

const plan = ref<TestPlanVO | null>(null)
const items = ref<TestPlanItemVO[]>([])
const loading = ref(false)
const issueTitles = ref<Record<number, string>>({})

const stats = computed(() => {
  const by = { PASS: 0, FAIL: 0, BLOCKED: 0, PENDING: 0 }
  for (const i of items.value) by[i.result] = (by[i.result] ?? 0) + 1
  const executed = items.value.length - by.PENDING
  const passRate = executed === 0 ? null : Math.round((by.PASS / executed) * 100)
  return { ...by, total: items.value.length, executed, passRate }
})

const defectRows = computed(() => items.value.filter((i) => i.issueId != null))
const executorRows = computed(() => {
  const map = new Map<number, { executed: number; pass: number }>()
  for (const i of items.value) {
    if (i.executedBy == null) continue
    const row = map.get(i.executedBy) ?? { executed: 0, pass: 0 }
    row.executed++
    if (i.result === 'PASS') row.pass++
    map.set(i.executedBy, row)
  }
  return [...map.entries()].map(([uid, v]) => ({ uid, ...v }))
})

const resultChartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

function renderChart() {
  if (!resultChartEl.value) return
  chart?.dispose()
  chart = echarts.init(resultChartEl.value)
  chart.setOption({
    title: { text: '执行结果分布', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'item' },
    series: [
      {
        type: 'pie',
        radius: ['45%', '70%'],
        label: { formatter: '{b}: {c}' },
        data: [
          { name: ITEM_RESULT_LABELS.PASS, value: stats.value.PASS, itemStyle: { color: '#67c23a' } },
          { name: ITEM_RESULT_LABELS.FAIL, value: stats.value.FAIL, itemStyle: { color: '#f56c6c' } },
          { name: ITEM_RESULT_LABELS.BLOCKED, value: stats.value.BLOCKED, itemStyle: { color: '#e6a23c' } },
          { name: ITEM_RESULT_LABELS.PENDING, value: stats.value.PENDING, itemStyle: { color: '#909399' } },
        ].filter((d) => d.value > 0),
      },
    ],
  })
}

function onResize() {
  chart?.resize()
}

function fmt(t: string | null): string {
  return t ? t.replace('T', ' ').slice(0, 16) : '—'
}

onMounted(async () => {
  loading.value = true
  try {
    const [p, itemsPage] = await Promise.all([
      getTestPlan(projectId.value, planId.value),
      listPlanItems(projectId.value, planId.value),
    ])
    plan.value = p
    items.value = itemsPage
    renderChart()
    window.addEventListener('resize', onResize)
    // 拉取关联缺陷标题（并行，失败静默）
    await Promise.allSettled(
      defectRows.value
        .filter((i) => i.issueId != null)
        .map(async (i) => {
          try {
            const issue = await getIssue(projectId.value, i.issueId!)
            issueTitles.value[i.issueId!] = `#${issue.issueNo} ${issue.title}`
          } catch {
            issueTitles.value[i.issueId!] = `#${i.issueId}`
          }
        }),
    )
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section v-loading="loading" class="report-view">
    <div class="report-view__head">
      <h2>测试报告 — {{ plan?.name ?? `#${planId}` }}</h2>
      <el-button @click="$router.back()">返回</el-button>
    </div>

    <!-- 核心指标 -->
    <div class="report-view__cards">
      <el-card shadow="never"><div class="report-view__num">{{ stats.total }}</div><div class="report-view__label">用例总数</div></el-card>
      <el-card shadow="never"><div class="report-view__num">{{ stats.executed }}</div><div class="report-view__label">已执行</div></el-card>
      <el-card shadow="never">
        <div class="report-view__num" :class="stats.passRate !== null && stats.passRate < 80 ? 'report-view__num--warn' : ''">
          {{ stats.passRate === null ? '—' : `${stats.passRate}%` }}
        </div>
        <div class="report-view__label">通过率（按已执行）</div>
      </el-card>
      <el-card shadow="never"><div class="report-view__num">{{ stats.FAIL }}</div><div class="report-view__label">失败（关联缺陷）</div></el-card>
    </div>

    <div class="report-view__grid">
      <!-- 图 + 执行人 -->
      <div>
        <el-card shadow="never" class="report-view__chart-card">
          <div ref="resultChartEl" class="report-view__chart" />
        </el-card>
        <el-card shadow="never" class="report-view__mt">
          <h4>执行人统计</h4>
          <el-table :data="executorRows" size="small" empty-text="暂无执行记录">
            <el-table-column label="执行人" width="120">
              <template #default="{ row }">执行人 #{{ row.uid }}</template>
            </el-table-column>
            <el-table-column prop="executed" label="执行条数" width="110" />
            <el-table-column label="通过条数" width="110">
              <template #default="{ row }">{{ row.pass }}</template>
            </el-table-column>
            <el-table-column label="通过率">
              <template #default="{ row }">{{ row.executed ? `${Math.round((row.pass / row.executed) * 100)}%` : '—' }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>

      <!-- 明细 + 缺陷关联 -->
      <div class="report-view__lists">
        <el-card shadow="never">
          <h4>结果明细</h4>
          <el-table :data="items" size="small" empty-text="计划为空">
            <el-table-column label="用例" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.caseTitle ?? `用例 #${row.caseId}` }}
              </template>
            </el-table-column>
            <el-table-column label="结果" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="(ITEM_RESULT_TAG_TYPES[row.result] as never) || 'info'">
                  {{ labelOf(ITEM_RESULT_LABELS, row.result) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="执行时间" width="130">
              <template #default="{ row }">{{ fmt(row.executedAt) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
        <el-card shadow="never" class="report-view__mt">
          <h4>关联缺陷（{{ defectRows.length }}）</h4>
          <el-table :data="defectRows" size="small" empty-text="无失败关联缺陷">
            <el-table-column label="缺陷" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ issueTitles[row.issueId!] ?? `#${row.issueId}` }}</template>
            </el-table-column>
            <el-table-column label="备注" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.note || '—' }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>
    </div>
  </section>
</template>

<style scoped>
.report-view {
  max-width: 1200px;
  margin: 16px auto;
}
.report-view__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.report-view__head h2 {
  margin: 0;
  font-size: 20px;
}
.report-view__cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.report-view__num {
  font-size: 28px;
  font-weight: 600;
}
.report-view__num--warn {
  color: #f56c6c;
}
.report-view__label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin-top: 4px;
  text-align: center;
}
.report-view__cards :deep(.el-card__body) {
  text-align: center;
}
.report-view__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}
.report-view__chart {
  height: 300px;
}
.report-view__mt {
  margin-top: 16px;
}
.report-view h4 {
  margin: 0 0 10px;
  font-size: 14px;
}
</style>
