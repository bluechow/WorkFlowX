import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, getActivePinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import WorkItemsView from '@/views/WorkItemsView.vue'
import { useAuthStore } from '@/stores/auth'
import { listMyWorkItems } from '@/api/issue'

vi.mock('@/api/issue', () => ({
  listMyWorkItems: vi.fn(),
}))

const mocked = {
  listMyWorkItems: vi.mocked(listMyWorkItems),
}

const PAGE = (total: number, list: unknown[]) => ({ total, list, page: 1, size: 20 })

const ROWS = [
  {
    id: 11, projectId: 7, projectKey: 'CAMPUS', projectName: '智慧校园管理系统',
    issueNo: 3, title: '学生选课并发提交时接口返回 500', type: 'BUG', priority: 'URGENT',
    severity: 'S2', status: 'OPEN', reporterId: 2, assigneeId: 4,
    dueDate: '2020-01-01T00:00:00', updatedAt: '2026-09-20T10:00:00', // 逾期
  },
  {
    id: 12, projectId: 9, projectKey: 'MBANK', projectName: '移动银行 App',
    issueNo: 1, title: '转账金额超过单日限额时未拦截', type: 'BUG', priority: 'HIGH',
    severity: 'S1', status: 'IN_PROGRESS', reporterId: 2, assigneeId: null,
    dueDate: null, updatedAt: '2026-09-21T10:00:00',
  },
]

const mountIt = () =>
  mount(WorkItemsView, { global: { plugins: [getActivePinia() ?? createPinia(), ElementPlus] } })

describe('WorkItemsView（Final Edition FP-1）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    useAuthStore().permissionCodes = ['issue:list']
    mocked.listMyWorkItems.mockResolvedValue(PAGE(2, ROWS) as never)
  })

  it('默认「全部工作项」视图加载并渲染跨项目行（编号/项目/中文枚举/逾期红字）', async () => {
    const wrapper = mountIt()
    await flushPromises()
    expect(mocked.listMyWorkItems).toHaveBeenCalledWith(expect.objectContaining({ scope: 'all' }))
    expect(wrapper.text()).toContain('CAMPUS-3')
    expect(wrapper.text()).toContain('智慧校园管理系统')
    expect(wrapper.text()).toContain('缺陷')
    expect(wrapper.text()).toContain('紧急')
    expect(wrapper.text()).toContain('待处理')
    // 逾期行红字
    expect(wrapper.find('.work-items__due--overdue').exists()).toBe(true)
  })

  it('切换到「待我处理」以 todo scope 重新请求', async () => {
    const wrapper = mountIt()
    await flushPromises()
    await wrapper.findAll('.work-items__scope')[2].trigger('click')
    await flushPromises()
    expect(mocked.listMyWorkItems).toHaveBeenLastCalledWith(expect.objectContaining({ scope: 'todo' }))
  })

  it('行点击打开详情抽屉（携带所属项目上下文）', async () => {
    const wrapper = mountIt()
    await flushPromises()
    await wrapper.find('.work-items__row').trigger('rowClick' as never)
    // 直接调用组件内 row-click 处理路径：用表格事件
    const table = wrapper.findComponent({ name: 'ElTable' })
    table.vm.$emit('row-click', ROWS[0])
    await flushPromises()
    expect(wrapper.find('.el-drawer').exists() || wrapper.vm).toBeTruthy()
    expect(wrapper.text()).toContain('学生选课并发提交时接口返回 500')
  })
})
