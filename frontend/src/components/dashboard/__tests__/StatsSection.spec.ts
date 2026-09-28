import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, getActivePinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import StatsSection from '../StatsSection.vue'
import type { DashboardOverview } from '@/api/dashboard'

// ECharts 在 jsdom 下无真实尺寸：mock init 捕获 setOption 数据映射
const setOptionSpy = vi.fn()
vi.mock('echarts', () => ({
  init: vi.fn(() => ({ setOption: setOptionSpy, dispose: vi.fn(), resize: vi.fn() })),
}))

const OVERVIEW: DashboardOverview = {
  projects: { total: 3, active: 2, archived: 1 },
  issues: {
    total: 4,
    byStatus: { OPEN: 3, IN_PROGRESS: 1 },
    byType: { BUG: 2, TASK: 2 },
    byPriority: { HIGH: 1, LOW: 3 },
    bySeverity: { S1: 1 },
    bugCount: 2,
  },
  createdTrend: Array.from({ length: 14 }, (_, i) => ({
    date: `2026-09-${String(9 + i).padStart(2, '0')}`,
    created: i === 13 ? 2 : 0,
  })),
}

const mountSection = () =>
  mount(StatsSection, {
    props: { overview: OVERVIEW },
    global: { plugins: [getActivePinia() ?? createPinia(), ElementPlus] },
  })

describe('StatsSection（P10-12）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('渲染真实指标卡片（项目/活跃/Issue/BUG）', () => {
    const wrapper = mountSection()
    expect(wrapper.find('[data-test="card-projects"]').text()).toContain('3')
    expect(wrapper.find('[data-test="card-active"]').text()).toContain('2')
    expect(wrapper.find('[data-test="card-issues"]').text()).toContain('4')
    expect(wrapper.find('[data-test="card-bugs"]').text()).toContain('2')
  })

  it('状态分布图数据来自真实 byStatus 映射', () => {
    mountSection()
    const call = setOptionSpy.mock.calls.find((c) =>
      JSON.stringify(c[0]).includes('Issue 状态分布'),
    )
    expect(call).toBeTruthy()
    const pie = call![0].series[0].data as Array<{ name: string; value: number }>
    // 图例走中文映射（Phase A 中文化）
    expect(pie).toContainEqual({ name: '待处理', value: 3 })
    expect(pie).toContainEqual({ name: '处理中', value: 1 })
  })

  it('趋势图 14 天且末点为今日真实创建数', () => {
    mountSection()
    const call = setOptionSpy.mock.calls.find((c) =>
      JSON.stringify(c[0]).includes('创建趋势'),
    )
    expect(call).toBeTruthy()
    expect(call![0].series[0].data).toHaveLength(14)
    expect(call![0].series[0].data[13]).toBe(2)
  })
})
