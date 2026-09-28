import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, getActivePinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import SessionsView from '@/views/system/SessionsView.vue'
import { kickSession, listSessions } from '@/api/session'

vi.mock('@/api/session', () => ({
  listSessions: vi.fn(),
  kickSession: vi.fn(),
}))

const mocked = {
  listSessions: vi.mocked(listSessions),
  kickSession: vi.mocked(kickSession),
}

// ElMessageBox.confirm 打桩为"确认"
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn().mockResolvedValue(true) },
  }
})

const SESSIONS = [
  { userId: 1, username: 'demo_admin', nickname: '演示管理员', roles: ['ADMIN'], expiresInSeconds: 7000, self: true },
  { userId: 30, username: 'demo_u05', nickname: '陈静', roles: ['MEMBER'], expiresInSeconds: 3600, self: false },
]

const mountIt = () =>
  mount(SessionsView, { global: { plugins: [getActivePinia() ?? createPinia(), ElementPlus] } })

describe('SessionsView（Phase A-⑤）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    mocked.listSessions.mockResolvedValue(SESSIONS)
  })

  it('渲染会话列表：自己显示「当前登录」（禁用），他人显示「踢下线」', async () => {
    const wrapper = mountIt()
    await flushPromises()
    const rows = wrapper.findAll('.el-table__row')
    expect(rows.length).toBe(2)
    expect(wrapper.text()).toContain('1 小时 57 分')
    // 自己行：禁用按钮 + 提示文案
    const selfBtn = rows[0].findAll('button').find((b) => b.text() === '当前登录')!
    expect(selfBtn.attributes('disabled')).toBeDefined()
    // 他人行：危险色踢下线
    const kickBtn = rows[1].findAll('button').find((b) => b.text() === '踢下线')!
    expect(kickBtn.classes().some((c) => c.includes('danger'))).toBe(true)
  })

  it('踢下线：确认后调用 API 并刷新列表', async () => {
    mocked.kickSession.mockResolvedValue(undefined)
    const wrapper = mountIt()
    await flushPromises()
    const kickBtn = wrapper.findAll('.el-table__row')[1]
      .findAll('button').find((b) => b.text() === '踢下线')!
    await kickBtn.trigger('click')
    await flushPromises()
    expect(mocked.kickSession).toHaveBeenCalledWith(30)
    expect(mocked.listSessions).toHaveBeenCalledTimes(2) // 初始 + 踢后刷新
  })

  it('空列表展示空状态', async () => {
    mocked.listSessions.mockResolvedValue([])
    const wrapper = mountIt()
    await flushPromises()
    expect(wrapper.text()).toContain('当前没有在线会话')
  })
})
