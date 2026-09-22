import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, getActivePinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import IssueAttachmentsPanel from '../IssueAttachmentsPanel.vue'
import {
  deleteAttachment,
  downloadAttachment,
  listAttachments,
  uploadAttachment,
  type AttachmentVO,
} from '@/api/attachment'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/attachment', () => ({
  listAttachments: vi.fn(),
  uploadAttachment: vi.fn(),
  downloadAttachment: vi.fn(),
  deleteAttachment: vi.fn(),
  ALLOWED_EXTENSIONS: ['jpg', 'jpeg', 'png', 'gif', 'webp', 'pdf', 'txt', 'doc', 'docx', 'xls', 'xlsx', 'zip'],
  MAX_SIZE_BYTES: 10 * 1024 * 1024,
}))

const mocked = {
  listAttachments: vi.mocked(listAttachments),
  uploadAttachment: vi.mocked(uploadAttachment),
  downloadAttachment: vi.mocked(downloadAttachment),
  deleteAttachment: vi.mocked(deleteAttachment),
}

const ITEM: AttachmentVO = {
  id: 21, issueId: 3, fileName: 'p8-note.txt', fileSize: 2048,
  contentType: 'text/plain', uploaderId: 1, createdAt: '2026-09-22T10:00:00',
}

const mountPanel = () =>
  mount(IssueAttachmentsPanel, {
    props: { projectId: 7, issueId: 3 },
    global: { plugins: [getActivePinia() ?? createPinia(), ElementPlus] },
  })

describe('IssueAttachmentsPanel（P8-15）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    const store = useAuthStore()
    store.permissionCodes = ['attachment:list', 'attachment:upload', 'attachment:get', 'attachment:delete']
    store.currentUser = { id: 1, username: 'admin', email: 'a@a', nickname: null, status: 'ACTIVE', lastLoginAt: null, createdAt: '', updatedAt: '' }
    mocked.listAttachments.mockResolvedValue({ list: [ITEM], total: 1, page: 1, size: 20 })
  })

  it('加载并渲染附件列表（名称/大小）', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    expect(mocked.listAttachments).toHaveBeenCalledWith(7, 3)
    expect(wrapper.text()).toContain('p8-note.txt')
    expect(wrapper.text()).toContain('2KB')
  })

  it('空列表展示 empty 状态', async () => {
    mocked.listAttachments.mockResolvedValue({ list: [], total: 0, page: 1, size: 20 })
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.text()).toContain('暂无附件')
  })

  it('上传成功后从 API 重新拉取', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    const file = new File(['P8 内容'], 'p8-new.txt', { type: 'text/plain' })
    mocked.uploadAttachment.mockResolvedValue(ITEM)
    const input = wrapper.find('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')
    await flushPromises()
    expect(mocked.uploadAttachment).toHaveBeenCalledTimes(1)
    expect(mocked.listAttachments).toHaveBeenCalledTimes(2)
  })

  it('前端预检拒绝不允许的扩展名（不发起请求）', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    const file = new File(['MZ'], 'p8-evil.exe', { type: 'application/octet-stream' })
    const input = wrapper.find('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')
    await flushPromises()
    expect(mocked.uploadAttachment).not.toHaveBeenCalled()
  })

  it('前端预检拒绝超过 10MB 的文件（不发起请求）', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    const big = new File([new ArrayBuffer(10 * 1024 * 1024 + 1)], 'p8-big.txt', { type: 'text/plain' })
    const input = wrapper.find('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [big] })
    await input.trigger('change')
    await flushPromises()
    expect(mocked.uploadAttachment).not.toHaveBeenCalled()
  })

  it('下载调用鉴权下载接口', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    mocked.downloadAttachment.mockResolvedValue(undefined)
    const dl = wrapper.findAll('button').find((b) => b.text() === '下载')
    await dl!.trigger('click')
    await flushPromises()
    expect(mocked.downloadAttachment).toHaveBeenCalledWith(7, 3, ITEM)
  })

  it('删除后重新拉取', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    mocked.deleteAttachment.mockResolvedValue(undefined)
    const del = wrapper.findAll('button').find((b) => b.text() === '删除')
    await del!.trigger('click')
    await flushPromises()
    expect(mocked.deleteAttachment).toHaveBeenCalledWith(7, 3, 21)
    expect(mocked.listAttachments).toHaveBeenCalledTimes(2)
  })

  it('无上传权限时隐藏上传按钮（纯 UX）', async () => {
    const store = useAuthStore()
    store.permissionCodes = ['attachment:list']
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.findAll('button').some((b) => b.text().includes('上传附件'))).toBe(false)
  })
})
