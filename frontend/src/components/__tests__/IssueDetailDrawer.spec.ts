import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, getActivePinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import IssueDetailDrawer from '@/components/IssueDetailDrawer.vue'
import { useAuthStore } from '@/stores/auth'
import { transitionIssueStatus } from '@/api/issue'
import { listComments } from '@/api/comment'
import { listAttachments } from '@/api/attachment'

vi.mock('@/api/issue', () => ({
  transitionIssueStatus: vi.fn(),
}))
vi.mock('@/api/comment', () => ({
  listComments: vi.fn(),
  createComment: vi.fn(),
  updateComment: vi.fn(),
  deleteComment: vi.fn(),
}))
vi.mock('@/api/attachment', () => ({
  listAttachments: vi.fn(),
  uploadAttachment: vi.fn(),
  downloadAttachment: vi.fn(),
  deleteAttachment: vi.fn(),
}))
// P8 面板由各自 spec 覆盖，这里打桩验证"被复用"
vi.mock('@/components/issue/IssueCommentsPanel.vue', () => ({
  default: { name: 'IssueCommentsPanel', template: '<div class="comments-stub" />' },
}))
vi.mock('@/components/issue/IssueAttachmentsPanel.vue', () => ({
  default: { name: 'IssueAttachmentsPanel', template: '<div class="attachments-stub" />' },
}))

const mocked = {
  transitionIssueStatus: vi.mocked(transitionIssueStatus),
  listComments: vi.mocked(listComments),
  listAttachments: vi.mocked(listAttachments),
}

const ISSUE: import('@/types/api').IssueVO = {
  id: 1, projectId: 7, issueNo: 3, title: '学生选课并发提交时接口返回 500',
  description: '复现步骤：并发选课', type: 'BUG', priority: 'HIGH', severity: 'S2',
  status: 'OPEN', reporterId: 2, assigneeId: 4, dueDate: null, milestoneId: null, labels: [], createdAt: '2026-09-01T10:00:00', updatedAt: '',
}

const mountIt = () =>
  mount(IssueDetailDrawer, {
    props: {
      projectId: 7,
      projectKey: 'CAMPUS',
      issue: ISSUE,
      visible: true,
    },
    global: { plugins: [getActivePinia() ?? createPinia(), ElementPlus] },
  })

describe('IssueDetailDrawer（Phase A-③）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    mocked.listComments.mockResolvedValue({ list: [], total: 0, page: 1, size: 50 })
    mocked.listAttachments.mockResolvedValue({ list: [], total: 0, page: 1, size: 50 })
  })

  it('聚合展示：编号/中文信息/描述 + 复用评论与附件面板', async () => {
    useAuthStore().permissionCodes = ['issue:list', 'issue:transition', 'comment:list', 'issue:update']
    const wrapper = mountIt()
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('CAMPUS-3')
    expect(text).toContain('学生选课并发提交时接口返回 500')
    expect(text).toContain('缺陷')
    expect(text).toContain('S2 严重')
    expect(text).toContain('复现步骤：并发选课')
    expect(text).toContain('描述')
    // P8 面板被复用
    expect(wrapper.find('.comments-stub').exists()).toBe(true)
    expect(wrapper.find('.attachments-stub').exists()).toBe(true)
  })

  it('OPEN 状态只渲染「处理中」一个流转按钮（ADR-017 矩阵）', async () => {
    useAuthStore().permissionCodes = ['issue:list', 'issue:transition']
    const wrapper = mountIt()
    await flushPromises()
    const flowBtns = wrapper.findAll('.drawer__flow button')
    expect(flowBtns.length).toBe(1)
    expect(flowBtns[0].text()).toBe('处理中')
  })

  it('点击流转按钮调用 API 并 emit updated（携带最新 Issue）', async () => {
    useAuthStore().permissionCodes = ['issue:list', 'issue:transition']
    const updated = { ...ISSUE, status: 'IN_PROGRESS' as const }
    mocked.transitionIssueStatus.mockResolvedValue(updated)
    const wrapper = mountIt()
    await flushPromises()
    await wrapper.findAll('.drawer__flow button')[0].trigger('click')
    expect(mocked.transitionIssueStatus).toHaveBeenCalledWith(7, 1, 'OPEN', 'IN_PROGRESS')
    expect(wrapper.emitted('updated')![0][0]).toMatchObject({ status: 'IN_PROGRESS' })
  })

  it('无 issue:transition 权限时不渲染流转区；有编辑权限时渲染编辑按钮', async () => {
    useAuthStore().permissionCodes = ['issue:list', 'issue:update', 'comment:list']
    const wrapper = mountIt()
    await flushPromises()
    expect(wrapper.find('.drawer__flow').exists()).toBe(false)
    expect(wrapper.text()).toContain('编辑')
  })
})
