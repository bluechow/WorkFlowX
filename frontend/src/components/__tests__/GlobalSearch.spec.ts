import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, getActivePinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import GlobalSearch from '@/components/GlobalSearch.vue'


const mockSearch = vi.hoisted(() => vi.fn())
vi.mock('@/api/search', () => ({ globalSearch: mockSearch }))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  createRouter: () => ({ beforeEach: vi.fn(), push: vi.fn() }),
  createWebHistory: () => ({}),
}))

const mocked = { globalSearch: mockSearch }

const mountIt = () =>
  mount(GlobalSearch, { global: { plugins: [getActivePinia() ?? createPinia(), ElementPlus] }, attachTo: document.body })

describe('GlobalSearch（FP-6 + P0 修复回归）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    vi.useFakeTimers()
    mocked.globalSearch.mockResolvedValue({ projects: [], issues: [], users: [] })
  })

  afterEach(() => {
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  it('输入关键词（防抖后）请求并展示分组结果', async () => {
    mocked.globalSearch.mockResolvedValue({
      projects: [{ id: 1, key: 'CAMPUS', name: '智慧校园' }],
      issues: [], users: [],
    })
    const wrapper = mountIt()
    await wrapper.find('input').setValue('校园')
    vi.advanceTimersByTime(350)
    await flushPromises()
    expect(mocked.globalSearch).toHaveBeenCalledWith('校园')
    expect(wrapper.find('.gs__panel').isVisible()).toBe(true)
    expect(wrapper.text()).toContain('智慧校园')
    wrapper.unmount()
  })

  it('空关键词不请求且面板关闭', async () => {
    const wrapper = mountIt()
    await wrapper.find('input').setValue('  ')
    vi.advanceTimersByTime(350)
    await flushPromises()
    expect(mocked.globalSearch).not.toHaveBeenCalled()
    expect(wrapper.find('.gs__panel').exists()).toBe(false)
    wrapper.unmount()
  })

  it('P0：点击组件外部关闭面板（document 监听 + contains）', async () => {
    const wrapper = mountIt()
    await wrapper.find('input').setValue('校园')
    vi.advanceTimersByTime(350)
    await flushPromises()
    expect(wrapper.find('.gs__panel').isVisible()).toBe(true)

    // 组件内部点击：不关闭
    await wrapper.find('.gs__input').trigger('click')
    expect(wrapper.find('.gs__panel').isVisible()).toBe(true)

    // 外部点击：关闭（jsdom 中真实 button.click() 冒泡至 document 监听）
    const outside = document.createElement('button')
    outside.textContent = '外部'
    document.body.appendChild(outside)
    outside.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()
    await wrapper.vm.$nextTick()
    // 面板 v-if 卸载（DOM 移除）即关闭的最终证据
    expect(wrapper.find('.gs__panel').exists()).toBe(false)
    wrapper.unmount()
  })

  it('卸载时移除 document 监听（无泄漏/无报错）', async () => {
    const wrapper = mountIt()
    await wrapper.find('input').setValue('校园')
    vi.advanceTimersByTime(350)
    await flushPromises()
    wrapper.unmount()
    // 卸载后外部点击不再触发任何逻辑（不应抛错）
    const btn = document.createElement('button')
    document.body.appendChild(btn)
    expect(() => btn.click()).not.toThrow()
  })
})
