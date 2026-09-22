import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, getActivePinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import IssueCommentsPanel from '../IssueCommentsPanel.vue'
import { createComment, deleteComment, listComments, updateComment } from '@/api/comment'
import type { CommentVO } from '@/api/comment'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/comment', () => ({
  listComments: vi.fn(),
  createComment: vi.fn(),
  updateComment: vi.fn(),
  deleteComment: vi.fn(),
}))

const mocked = {
  listComments: vi.mocked(listComments),
  createComment: vi.mocked(createComment),
  updateComment: vi.mocked(updateComment),
  deleteComment: vi.mocked(deleteComment),
}

const MY_COMMENT: CommentVO = {
  id: 11, issueId: 3, authorId: 1, content: 'P8 我的评论',
  createdAt: '2026-09-22T10:00:00', updatedAt: '2026-09-22T10:00:00',
}
const OTHER_COMMENT: CommentVO = {
  id: 12, issueId: 3, authorId: 2, content: 'P8 他人评论',
  createdAt: '2026-09-22T10:01:00', updatedAt: '2026-09-22T10:01:00',
}

const mountPanel = () =>
  mount(IssueCommentsPanel, {
    props: { projectId: 7, issueId: 3 },
    global: { plugins: [getActivePinia() ?? createPinia(), ElementPlus] },
  })

describe('IssueCommentsPanel（P8-14）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    const store = useAuthStore()
    store.permissionCodes = ['comment:list', 'comment:create', 'comment:update', 'comment:delete']
    store.currentUser = { id: 1, username: 'admin', email: 'a@a', nickname: null, status: 'ACTIVE', lastLoginAt: null, createdAt: '', updatedAt: '' }
    mocked.listComments.mockResolvedValue({ list: [MY_COMMENT, OTHER_COMMENT], total: 2, page: 1, size: 20 })
  })

  it('加载并渲染评论列表', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    expect(mocked.listComments).toHaveBeenCalledWith(7, 3)
    expect(wrapper.text()).toContain('P8 我的评论')
    expect(wrapper.text()).toContain('P8 他人评论')
  })

  it('空列表展示 empty 状态', async () => {
    mocked.listComments.mockResolvedValue({ list: [], total: 0, page: 1, size: 20 })
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.text()).toContain('暂无评论')
  })

  it('加载失败展示错误提示', async () => {
    mocked.listComments.mockRejectedValue({ code: 403, message: '仅项目成员可访问' })
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.text()).toContain('仅项目成员可访问')
  })

  it('创建评论成功后从 API 重新拉取（不本地 push）', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    const textarea = wrapper.find('textarea')
    await textarea.setValue('P8 新评论')
    mocked.createComment.mockResolvedValue(MY_COMMENT)
    const buttons = wrapper.findAll('button')
    const submit = buttons.find((b) => b.text().includes('发布评论'))
    await submit!.trigger('click')
    await flushPromises()
    expect(mocked.createComment).toHaveBeenCalledWith(7, 3, 'P8 新评论')
    expect(mocked.listComments).toHaveBeenCalledTimes(2)
  })

  it('空内容提交被前端拦截', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    const buttons = wrapper.findAll('button')
    const submit = buttons.find((b) => b.text().includes('发布评论'))
    expect(submit!.attributes('disabled')).toBeDefined()
    expect(mocked.createComment).not.toHaveBeenCalled()
  })

  it('编辑自己的评论并保存', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    mocked.updateComment.mockResolvedValue({ ...MY_COMMENT, content: 'P8 编辑后' })
    const editButtons = wrapper.findAll('button').filter((b) => b.text() === '编辑')
    // 自己的评论在先（authorId=1），第一处编辑按钮属于它
    await editButtons[0].trigger('click')
    await flushPromises()
    // 面板顶部发布框是第一个 textarea；编辑框在其后
    const textareas = wrapper.findAll('textarea')
    await textareas[textareas.length - 1].setValue('P8 编辑后')
    const save = wrapper.findAll('button').find((b) => b.text().includes('保存'))
    mocked.updateComment.mockResolvedValue(MY_COMMENT)
    await save!.trigger('click')
    await flushPromises()
    expect(mocked.updateComment).toHaveBeenCalledWith(7, 3, 11, 'P8 编辑后')
    expect(mocked.listComments).toHaveBeenCalledTimes(2)
  })

  it('删除评论后重新拉取', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    mocked.deleteComment.mockResolvedValue(undefined)
    const del = wrapper.findAll('button').find((b) => b.text() === '删除')
    await del!.trigger('click')
    await flushPromises()
    expect(mocked.deleteComment).toHaveBeenCalledWith(7, 3, 11)
    expect(mocked.listComments).toHaveBeenCalledTimes(2)
  })

  it('无 comment:create 权限时隐藏发布框（纯 UX）', async () => {
    const store = useAuthStore()
    store.permissionCodes = ['comment:list']
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.find('textarea').exists()).toBe(false)
  })
})
