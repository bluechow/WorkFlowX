<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import {
  fetchHeatmap,
  fetchTraceability,
  RISK_COLORS,
  RISK_LABELS,
  RESULT_LABELS,
  RESULT_TAG_TYPES,
  type HeatmapData,
  type TraceabilityData,
  type DirectoryStat,
} from '@/api/quality'
import { labelOf, CASE_TYPE_LABELS, CASE_PRIORITY_LABELS, ISSUE_STATUS_LABELS } from '@/utils/labels'

/**
 * 质量分析（创新点）：覆盖率热力图 + 需求-测试追溯矩阵。
 * 数据全部来自后端真实聚合（用例库 × 计划执行 × Bug 关联），无伪造。
 */
const route = useRoute()
const projectId = computed(() => Number(route.params.projectId))

const activeTab = ref<'heatmap' | 'matrix'>('heatmap')
const loading = ref(false)
const heatmap = ref<HeatmapData | null>(null)
const trace = ref<TraceabilityData | null>(null)

// ===== 热力图 =====
const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

function renderChart() {
  if (!chartEl.value || !heatmap.value || heatmap.value.directories.length === 0) return
  chart?.dispose()
  chart = echarts.init(chartEl.value)
  const dirs = heatmap.value.directories
  chart.setOption({
    title: {
      text: '测试覆盖率热力图',
      subtext: `总覆盖率 ${heatmap.value.overallCoverage}% ｜ ${heatmap.value.totalCases} 用例 / ${heatmap.value.totalExecuted} 已执行 / ${heatmap.value.totalBugs} Bug`,
      left: 'center',
      textStyle: { fontSize: 16 },
    },
    tooltip: {
      formatter: (params: { name: string; value: number; data: { stat: DirectoryStat } }) => {
        const s = params.data.stat
        return `<b>${s.directoryName}</b><br/>
          风险等级：<span style="color:${RISK_COLORS[s.riskLevel]}"><b>${RISK_LABELS[s.riskLevel]}</b></span><br/>
          用例数：${s.caseCount}（已执行 ${s.executedCount}）<br/>
          覆盖率：${s.coverageRate}% ｜ 通过率：${s.passRate}%<br/>
          PASS ${s.passCount} / FAIL ${s.failCount} / BLOCKED ${s.blockedCount} / PENDING ${s.pendingCount}<br/>
          关联 Bug：${s.bugCount} 个`
      },
    },
    series: [{
      type: 'treemap',
      left: 20, right: 20, top: 80, bottom: 20,
      roam: false,
      nodeClick: 'zoomToNode',
      breadcrumb: { show: true, bottom: 0 },
      label: {
        show: true,
        formatter: (p: { name: string; data: { stat: DirectoryStat } }) => {
          const s = p.data.stat
          return `{name|${s.directoryName}}\n{rate|${s.coverageRate}%}\n{risk|${RISK_LABELS[s.riskLevel]}}`
        },
        rich: {
          name: { fontSize: 14, fontWeight: 'bold', color: '#fff', lineHeight: 24 },
          rate: { fontSize: 22, fontWeight: 'bold', color: '#fff', lineHeight: 30 },
          risk: { fontSize: 12, color: 'rgba(255,255,255,0.85)', lineHeight: 18 },
        },
      },
      itemStyle: { borderColor: '#fff', borderWidth: 2, gapWidth: 2 },
      data: dirs.map(d => ({
        name: d.directoryName,
        value: Math.max(d.caseCount, 1), // 零用例目录也给最小面积
        stat: d,
        itemStyle: { color: RISK_COLORS[d.riskLevel] ?? '#909399' },
      })),
    }],
  })
}

function onResize() { chart?.resize() }

// ===== 追溯矩阵 =====
const expandedDirs = ref<number[]>([])

function toggleDir(dirId: number | null) {
  const key = dirId ?? -1
  const idx = expandedDirs.value.indexOf(key)
  if (idx >= 0) expandedDirs.value.splice(idx, 1)
  else expandedDirs.value.push(key)
}

function isExpanded(dirId: number | null) {
  return expandedDirs.value.includes(dirId ?? -1)
}

// ===== 风险分布统计卡 =====
const riskSummary = computed(() => {
  if (!heatmap.value) return { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 }
  const by: Record<string, number> = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 }
  for (const d of heatmap.value.directories) {
    by[d.riskLevel] = (by[d.riskLevel] ?? 0) + 1
  }
  return by
})

async function refresh() {
  loading.value = true
  try {
    const [hm, tr] = await Promise.all([
      fetchHeatmap(projectId.value),
      fetchTraceability(projectId.value),
    ])
    heatmap.value = hm
    trace.value = tr
    if (activeTab.value === 'heatmap') {
      requestAnimationFrame(renderChart)
    }
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '加载质量分析失败')
  } finally {
    loading.value = false
  }
}

watch(activeTab, (tab) => {
  if (tab === 'heatmap') {
    requestAnimationFrame(renderChart)
  }
})

onMounted(() => {
  void refresh()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
})
</script>

<template>
  <div v-loading="loading" class="qa">
    <!-- Tab 切换 -->
    <div class="qa__tabs">
      <button
        class="qa__tab"
        :class="{ 'qa__tab--active': activeTab === 'heatmap' }"
        @click="activeTab = 'heatmap'"
      >
        覆盖率热力图
      </button>
      <button
        class="qa__tab"
        :class="{ 'qa__tab--active': activeTab === 'matrix' }"
        @click="activeTab = 'matrix'"
      >
        需求-测试追溯矩阵
      </button>
    </div>

    <!-- ===== 热力图 ===== -->
    <template v-if="activeTab === 'heatmap'">
      <!-- 风险分布统计卡 -->
      <div v-if="heatmap" class="qa__cards">
        <el-card shadow="never" class="qa__card">
          <div class="qa__num" :style="{ color: RISK_COLORS.CRITICAL }">{{ riskSummary.CRITICAL }}</div>
          <div class="qa__label">零覆盖（盲区）</div>
        </el-card>
        <el-card shadow="never" class="qa__card">
          <div class="qa__num" :style="{ color: RISK_COLORS.HIGH }">{{ riskSummary.HIGH }}</div>
          <div class="qa__label">高风险</div>
        </el-card>
        <el-card shadow="never" class="qa__card">
          <div class="qa__num" :style="{ color: RISK_COLORS.MEDIUM }">{{ riskSummary.MEDIUM }}</div>
          <div class="qa__label">中风险</div>
        </el-card>
        <el-card shadow="never" class="qa__card">
          <div class="qa__num" :style="{ color: RISK_COLORS.LOW }">{{ riskSummary.LOW }}</div>
          <div class="qa__label">低风险</div>
        </el-card>
      </div>

      <!-- ECharts 树图 -->
      <el-card shadow="never" class="qa__chart-card">
        <div ref="chartEl" class="qa__chart" />
        <el-empty
          v-if="heatmap && heatmap.directories.length === 0"
          description="暂无用例数据，请先在用例库中添加测试用例"
          :image-size="60"
          class="qa__empty-overlay"
        />
      </el-card>

      <!-- 图例说明 -->
      <div class="qa__legend">
        <span class="qa__legend-item">
          <i :style="{ background: RISK_COLORS.CRITICAL }" /> 零覆盖：无任何测试用例的盲区
        </span>
        <span class="qa__legend-item">
          <i :style="{ background: RISK_COLORS.HIGH }" /> 高风险：覆盖率 &lt; 50% 或通过率 &lt; 70%
        </span>
        <span class="qa__legend-item">
          <i :style="{ background: RISK_COLORS.MEDIUM }" /> 中风险：覆盖率 50%~80%
        </span>
        <span class="qa__legend-item">
          <i :style="{ background: RISK_COLORS.LOW }" /> 低风险：覆盖率 ≥ 80% 且通过率 ≥ 80%
        </span>
      </div>
    </template>

    <!-- ===== 追溯矩阵 ===== -->
    <template v-if="activeTab === 'matrix' && trace">
      <!-- 覆盖概览 -->
      <el-alert
        :type="trace.gapModules > 0 ? 'warning' : 'success'"
        :closable="false"
        class="qa__alert"
      >
        <template #title>
          {{ trace.totalModules }} 个功能模块：{{ trace.coveredModules }} 个已有测试覆盖，
          {{ trace.gapModules > 0 ? `${trace.gapModules} 个缺少测试保护（测试缺口）` : '全部覆盖 ✓' }}
        </template>
      </el-alert>

      <!-- 矩阵表 -->
      <el-card shadow="never">
        <template #header><span class="qa__h">追溯矩阵（点击模块行展开用例明细）</span></template>
        <el-table :data="trace.directories" border row-class-name="qa__dir-row" empty-text="暂无模块数据">
          <el-table-column label="" width="40" align="center">
            <template #default="{ row }">
              <el-icon
                class="qa__expand-icon"
                :class="{ 'qa__expand-icon--open': isExpanded(row.directoryId) }"
                @click="toggleDir(row.directoryId)"
              >
                <component :is="isExpanded(row.directoryId) ? 'ArrowDown' : 'ArrowRight'" />
              </el-icon>
            </template>
          </el-table-column>
          <el-table-column prop="directoryName" label="功能模块" min-width="180" />
          <el-table-column label="用例数" width="80" align="center">
            <template #default="{ row }">
              <span :style="{ color: row.caseCount === 0 ? '#f56c6c' : '', fontWeight: row.caseCount === 0 ? 'bold' : '' }">
                {{ row.caseCount }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="已执行" width="80" align="center">
            <template #default="{ row }">{{ row.coveredCount }}</template>
          </el-table-column>
          <el-table-column label="测试缺口" width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.gapCount > 0" size="small" type="danger">{{ row.gapCount }} 条未执行</el-tag>
              <el-tag v-else size="small" type="success">✓</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="覆盖率" width="200">
            <template #default="{ row }">
              <el-progress
                :percentage="row.caseCount === 0 ? 0 : Math.round(row.coveredCount * 100 / row.caseCount)"
                :stroke-width="10"
                :color="row.caseCount === 0 ? '#f56c6c' : row.coveredCount === row.caseCount ? '#67c23a' : '#409eff'"
              />
            </template>
          </el-table-column>

          <!-- 展开行：用例明细 -->
          <el-table-column type="expand">
            <template #default="{ row }">
              <div v-if="row.cases.length === 0" class="qa__no-cases">
                ⚠ 此模块没有任何测试用例——测试盲区
              </div>
              <el-table v-else :data="row.cases" size="small" border>
                <el-table-column label="编号" width="80">
                  <template #default="{ row: c }">TC-{{ c.testcaseNo }}</template>
                </el-table-column>
                <el-table-column prop="title" label="用例标题" min-width="200" show-overflow-tooltip />
                <el-table-column label="类型" width="80">
                  <template #default="{ row: c }">{{ labelOf(CASE_TYPE_LABELS, c.caseType) }}</template>
                </el-table-column>
                <el-table-column label="优先级" width="80">
                  <template #default="{ row: c }">{{ labelOf(CASE_PRIORITY_LABELS, c.priority) }}</template>
                </el-table-column>
                <el-table-column label="最新结果" width="90" align="center">
                  <template #default="{ row: c }">
                    <el-tag
                      v-if="c.latestResult && c.latestResult !== 'PENDING'"
                      size="small"
                      :type="(RESULT_TAG_TYPES[c.latestResult] as never) || 'info'"
                    >
                      {{ RESULT_LABELS[c.latestResult] ?? c.latestResult }}
                    </el-tag>
                    <el-tag v-else size="small" type="info">未执行</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="关联 Bug" min-width="180" show-overflow-tooltip>
                  <template #default="{ row: c }">
                    <template v-if="c.linkedBugId">
                      <span class="qa__bug-link">BUG-{{ c.linkedBugNo }}</span>
                      {{ c.linkedBugTitle }}
                      <el-tag size="small" type="info">{{ labelOf(ISSUE_STATUS_LABELS, c.linkedBugStatus) }}</el-tag>
                    </template>
                    <span v-else class="qa__no-bug">—</span>
                  </template>
                </el-table-column>
              </el-table>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.qa { display: flex; flex-direction: column; gap: 16px; }
.qa__tabs {
  display: flex; gap: 6px; border-bottom: 1px solid #dcdfe6; padding-bottom: 0;
}
.qa__tab {
  border: none; background: none; padding: 10px 18px; font-size: 14px;
  color: #606266; cursor: pointer; border-bottom: 2px solid transparent; margin-bottom: -1px;
}
.qa__tab:hover { color: #409eff; }
.qa__tab--active { color: #409eff; border-bottom-color: #409eff; font-weight: 600; }
.qa__cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.qa__card :deep(.el-card__body) { text-align: center; padding: 14px; }
.qa__num { font-size: 32px; font-weight: 700; }
.qa__label { color: #909399; font-size: 13px; margin-top: 4px; }
.qa__chart-card { position: relative; }
.qa__chart { height: 480px; }
.qa__empty-overlay { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); }
.qa__legend { display: flex; gap: 20px; flex-wrap: wrap; padding: 0 8px; }
.qa__legend-item {
  display: inline-flex; align-items: center; gap: 6px; font-size: 12px; color: #606266;
}
.qa__legend-item i { width: 12px; height: 12px; border-radius: 3px; display: inline-block; }
.qa__alert { margin-bottom: 0; }
.qa__h { font-weight: 600; font-size: 14px; }
.qa__dir-row { cursor: pointer; }
.qa__expand-icon {
  cursor: pointer; font-size: 14px; color: #909399; transition: transform 0.2s;
}
.qa__expand-icon--open { transform: rotate(90deg); }
.qa__no-cases {
  color: #f56c6c; font-size: 13px; text-align: center; padding: 16px 0; font-weight: 600;
}
.qa__bug-link { color: #f56c6c; font-weight: 600; font-size: 12px; }
.qa__no-bug { color: #c0c4cc; }
</style>
