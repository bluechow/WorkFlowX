import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import HealthView from '../HealthView.vue'
import { fetchHealth } from '@/api/health'

vi.mock('@/api/health', () => ({
  fetchHealth: vi.fn(),
}))

const mockedFetch = vi.mocked(fetchHealth)

const mountView = () =>
  mount(HealthView, {
    global: {
      plugins: [createPinia(), ElementPlus],
    },
  })

describe('HealthView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('渲染成功状态（后端健康）', async () => {
    mockedFetch.mockResolvedValue({
      status: 'UP',
      service: 'workflowx-backend',
      checkedAt: '2026-09-06T00:00:00Z',
    })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('后端服务正常')
    expect(wrapper.text()).toContain('workflowx-backend')
  })

  it('渲染错误状态（后端不可达）', async () => {
    mockedFetch.mockRejectedValue({ code: 500, message: 'network error' })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('后端不可达')
    expect(wrapper.text()).toContain('network error')
  })
})
