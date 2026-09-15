<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createProject, listOrgOptions, listProjects, updateProject, updateProjectStatus } from '@/api/project'
import { useAuthStore } from '@/stores/auth'
import type { ProjectStatus, ProjectVO } from '@/types/api'

/**
 * 项目管理（P5-03）：真实 API 列表/搜索/筛选/创建/编辑/归档恢复。
 * 无物理删除——归档代替（与后端一致）；按钮按 project:* 权限码渲染（纯 UX）。
 */
const auth = useAuthStore()
const router = useRouter()

const projects = ref<ProjectVO[]>([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)
const pager = reactive({ page: 1, size: 10, keyword: '' })
const statusFilter = ref<'ACTIVE' | 'ARCHIVED' | ''>('')
const orgFilter = ref<number | null>(null)
const orgOptions = ref<{ id: number; name: string; code: string }[]>([])

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({ name: '', key: '', orgId: null as number | null, description: '' })

async function refresh() {
  loading.value = true
  try {
    const page = await listProjects({
      keyword: pager.keyword || undefined,
      status: statusFilter.value || undefined,
      orgId: orgFilter.value ?? undefined,
      page: pager.page,
      size: pager.size,
    })
    projects.value = page.list
    total.value = page.total
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '加载项目失败')
  } finally {
    loading.value = false
  }
}

function search() {
  pager.page = 1
  refresh()
}

async function openCreate() {
  editingId.value = null
  form.name = ''
  form.key = ''
  form.orgId = null
  form.description = ''
  try {
    orgOptions.value = await listOrgOptions()
  } catch {
    orgOptions.value = []
  }
  dialogVisible.value = true
}

async function openEdit(project: ProjectVO) {
  editingId.value = project.id
  form.name = project.name
  form.key = project.key
  form.orgId = project.orgId
  form.description = project.description ?? ''
  dialogVisible.value = true
}

async function submit() {
  if (submitting.value) return
  if (!form.name.trim()) {
    ElMessage.warning('请输入项目名称')
    return
  }
  if (editingId.value === null && !form.key.trim()) {
    ElMessage.warning('请输入项目标识')
    return
  }
  if (editingId.value === null && !form.orgId) {
    ElMessage.warning('请选择所属组织')
    return
  }
  submitting.value = true
  try {
    if (editingId.value === null) {
      await createProject({
        name: form.name.trim(),
        key: form.key.trim().toUpperCase(),
        orgId: form.orgId!,
        description: form.description.trim() || undefined,
      })
      ElMessage.success('项目已创建')
    } else {
      await updateProject(editingId.value, {
        name: form.name.trim(),
        description: form.description.trim() || undefined,
      })
      ElMessage.success('项目已更新')
    }
    dialogVisible.value = false
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '保存失败')
  } finally {
    submitting.value = false
  }
}

async function toggleArchive(project: ProjectVO) {
  const target: ProjectStatus = project.status === 'ACTIVE' ? 'ARCHIVED' : 'ACTIVE'
  const action = target === 'ARCHIVED' ? '归档' : '恢复'
  try {
    await ElMessageBox.confirm(`确认${action}项目 ${project.name}？`, `${action}确认`, {
      type: 'warning',
      confirmButtonText: action,
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await updateProjectStatus(project.id, target)
    ElMessage.success(`项目已${action}`)
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? `${action}失败`)
  }
}

onMounted(refresh)
</script>

<template>
  <section class="projects-view">
    <div class="projects-view__toolbar">
      <h2>项目管理</h2>
      <el-input
        v-model="pager.keyword"
        placeholder="搜索名称/标识"
        clearable
        style="width: 200px"
        @keyup.enter="search"
      />
      <el-select v-model="statusFilter" clearable placeholder="状态" style="width: 120px" @change="search">
        <el-option label="ACTIVE" value="ACTIVE" />
        <el-option label="ARCHIVED" value="ARCHIVED" />
      </el-select>
      <el-button @click="search">搜索</el-button>
      <el-button
        v-if="auth.hasPermission('project:create')"
        type="primary"
        @click="openCreate"
      >
        新建项目
      </el-button>
    </div>

    <el-table v-loading="loading" :data="projects" border empty-text="暂无项目">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="key" label="标识" min-width="120" />
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="orgId" label="组织 ID" width="90" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="auth.hasPermission('issue:list')"
            link
            type="primary"
            @click="router.push(`/system/projects/${row.id}/issues`)"
          >
            Issues
          </el-button>
          <el-button
            v-if="auth.hasPermission('project:update')"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </el-button>
          <el-button
            v-if="auth.hasPermission('project:update')"
            link
            :type="row.status === 'ACTIVE' ? 'warning' : 'success'"
            @click="toggleArchive(row)"
          >
            {{ row.status === 'ACTIVE' ? '归档' : '恢复' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > pager.size"
      class="projects-view__pager"
      layout="prev, pager, next, total"
      :total="total"
      :page-size="pager.size"
      :current-page="pager.page"
      @current-change="(p: number) => { pager.page = p; refresh() }"
    />

    <el-dialog v-model="dialogVisible" :title="editingId === null ? '新建项目' : '编辑项目'" width="460px">
      <el-form label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="项目名称" />
        </el-form-item>
        <el-form-item label="标识">
          <el-input v-model="form.key" :disabled="editingId !== null" placeholder="如 WFX（Issue 编号前缀）" />
        </el-form-item>
        <el-form-item v-if="editingId === null" label="所属组织">
          <el-select v-model="form.orgId" placeholder="选择组织" style="width: 100%">
            <el-option v-for="o in orgOptions" :key="o.id" :label="`${o.name}（${o.code}）`" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="submitting" @click="submit">
          保存
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.projects-view {
  max-width: 1000px;
  margin: 16px auto;
}
.projects-view__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.projects-view__toolbar h2 {
  margin-right: auto;
}
.projects-view__pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
