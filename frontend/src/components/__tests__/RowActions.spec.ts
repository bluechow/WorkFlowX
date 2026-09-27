import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, getActivePinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import RowActions from '@/components/RowActions.vue'
import { useAuthStore } from '@/stores/auth'

const mountIt = () =>
  mount(RowActions, {
    props: {
      groups: [
        [
          { label: 'Issue 列表', permission: 'issue:list', onClick: () => {} },
          { label: '用例库', permission: 'testcase:list', onClick: () => {} },
          { label: '行级隐藏项', hidden: true, onClick: () => {} },
        ],
        [{ label: '编辑', permission: 'project:update', onClick: () => {} }],
        [{ label: '归档', permission: 'project:update', type: 'warning', onClick: () => {} }],
        [{ label: '删除', type: 'danger', disabled: true, onClick: () => {} }],
      ],
    },
    global: { plugins: [getActivePinia() ?? createPinia(), ElementPlus] },
  })

describe('RowActions（ui-conventions §3）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('按语义色渲染四个分组，字号统一 small，组间有分隔线', async () => {
    useAuthStore().permissionCodes = [
      'issue:list',
      'testcase:list',
      'project:update',
    ]
    const wrapper = mountIt()
    await flushPromises()
    const btns = wrapper.findAll('button')
    expect(btns.length).toBe(5)
    expect(wrapper.findAll('.row-actions__divider').length).toBe(3)
    // 语义色
    expect(btns.map((b) => b.classes()).filter((c) => c.includes('el-button--danger')).length).toBe(1)
    expect(btns.map((b) => b.classes()).filter((c) => c.includes('el-button--warning')).length).toBe(1)
    // 统一字号
    expect(btns.every((b) => b.classes().includes('el-button--small'))).toBe(true)
    // 危险按钮 disabled
    expect(btns.find((b) => b.text() === '删除')!.attributes('disabled')).toBeDefined()
  })

  it('无权限的按钮不渲染，整组不可见时分隔线消失', async () => {
    useAuthStore().permissionCodes = ['issue:list']
    const wrapper = mountIt()
    await flushPromises()
    const btns = wrapper.findAll('button')
    // 编辑/归档被权限过滤；「删除」未声明权限码 → 始终可见（危险操作由后端兜底）
    expect(btns.map((b) => b.text())).toEqual(['Issue 列表', '删除'])
    // 导航组与危险组之间保留分隔线
    expect(wrapper.findAll('.row-actions__divider').length).toBe(1)
  })

  it('hidden 谓词过滤行级动作', async () => {
    useAuthStore().permissionCodes = ['issue:list', 'testcase:list', 'project:update']
    const wrapper = mountIt()
    await flushPromises()
    expect(wrapper.text()).not.toContain('行级隐藏项')
  })
})
