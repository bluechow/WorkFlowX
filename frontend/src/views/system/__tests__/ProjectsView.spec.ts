import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, getActivePinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import ProjectsView from '../ProjectsView.vue'
import { useAuthStore } from '@/stores/auth'
import { createProject, listProjects, updateProject, updateProjectStatus } from '@/api/project'
import type { ProjectVO } from '@/types/api'

vi.mock('@/api/project', () => ({
  listProjects: vi.fn(),
  createProject: vi.fn(),
  updateProject: vi.fn(),
  updateProjectStatus: vi.fn(),
  listOrgOptions: vi.fn(),
}))

const mocked = {
  listProjects: vi.mocked(listProjects),
  createProject: vi.mocked(createProject),
  updateProject: vi.mocked(updateProject),
  updateProjectStatus: vi.mocked(updateProjectStatus),
}

const PROJECTS: ProjectVO[] = [
  { id: 1, orgId: 5, key: 'WFX', name: '主项目', description: 'd', status: 'ACTIVE', ownerId: 1, createdAt: '', updatedAt: '' },
  { id: 2, orgId: 5, key: 'OLD', name: '归档项目', description: null, status: 'ARCHIVED', ownerId: 1, createdAt: '', updatedAt: '' },
]

const mountView = () =>
  mount(ProjectsView, { global: { plugins: [getActivePinia() ?? createPinia(), ElementPlus] } })

describe('ProjectsView（P5-03）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    useAuthStore().permissionCodes = ['project:list', 'project:create', 'project:update']
    mocked.listProjects.mockResolvedValue({ list: PROJECTS, total: 2, page: 1, size: 10 })
  })

  it('加载并渲染项目列表（含状态标签）', async () => {
    const wrapper = mountView()
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('WFX')
    expect(text).toContain('主项目')
    expect(text).toContain('已归档')
  })

  it('空列表展示空状态', async () => {
    mocked.listProjects.mockResolvedValue({ list: [], total: 0, page: 1, size: 10 })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('暂无项目')
  })

  it('无 project:create 权限时新建按钮不渲染', async () => {
    useAuthStore().permissionCodes = ['project:list']
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.findAll('button').find((b) => b.text() === '新建项目')).toBeUndefined()
  })

  it('新建项目经真实 API（key 大写化，须选择组织）', async () => {
    const { listOrgOptions } = await import('@/api/project')
    vi.mocked(listOrgOptions).mockResolvedValue([{ id: 5, name: 'Org', code: 'ORG' }])
    mocked.createProject.mockResolvedValue(PROJECTS[0])
    const wrapper = mountView()
    await flushPromises()
    await wrapper.findAll('button').find((b) => b.text() === '新建项目')!.trigger('click')
    await flushPromises()
    // 未选组织时保存被拦截
    const inputs = wrapper.findAll('.el-dialog input')
    await inputs[0].setValue('新项目')
    await inputs[1].setValue('newproj')
    const saveBtn = wrapper.findAll('.el-dialog button').find((b) => b.text() === '保存')
    await saveBtn?.trigger('click')
    await flushPromises()
    expect(mocked.createProject).not.toHaveBeenCalled()
    // 选择组织（Element Plus select 下拉挂载于 body）
    await wrapper.find('.el-select').trigger('click')
    await flushPromises()
    const option = [...document.querySelectorAll('.el-select-dropdown__item')]
      .find((el) => el.textContent?.includes('ORG'))
    option?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()
    await saveBtn?.trigger('click')
    await flushPromises()
    expect(mocked.createProject).toHaveBeenCalledWith(
      expect.objectContaining({ key: 'NEWPROJ', name: '新项目', orgId: 5 }),
    )
  })

  it('归档需确认后才调用 status API', async () => {
    mocked.updateProjectStatus.mockResolvedValue(PROJECTS[1])
    const wrapper = mountView()
    await flushPromises()
    const archiveBtn = wrapper.findAll('button').find((b) => b.text() === '归档')
    await archiveBtn?.trigger('click')
    await flushPromises()
    expect(mocked.updateProjectStatus).not.toHaveBeenCalled()
  })

  it('编辑仅允许改名称与描述', async () => {
    mocked.updateProject.mockResolvedValue(PROJECTS[0])
    const wrapper = mountView()
    await flushPromises()
    const edit = wrapper.findAll('button').find((b) => b.text() === '编辑')
    await edit?.trigger('click')
    await flushPromises()
    const keyInput = wrapper.findAll('.el-dialog input')[1]
    expect((keyInput?.element as HTMLInputElement).disabled).toBe(true)
    const saveBtn = wrapper.findAll('.el-dialog button').find((b) => b.text() === '保存')
    await saveBtn?.trigger('click')
    await flushPromises()
    expect(mocked.updateProject).toHaveBeenCalledWith(1, expect.objectContaining({ name: '主项目' }))
  })

  it('加载失败不崩溃', async () => {
    mocked.listProjects.mockRejectedValue({ message: 'permission denied' })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('项目管理')
  })
})
