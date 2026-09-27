<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createOrg, deleteOrg, listOrgs, updateOrg } from '@/api/org'
import { useAuthStore } from '@/stores/auth'
import RowActions from '@/components/RowActions.vue'
import type { OrganizationVO } from '@/types/api'

/** 组织管理列表（P4-03）：真实 API；删除需二次确认（后端叠加 OWNER 数据级校验）。 */
const auth = useAuthStore()
const router = useRouter()

const orgs = ref<OrganizationVO[]>([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)
const pager = reactive({ page: 1, size: 10, keyword: '' })

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({ name: '', code: '', description: '' })

async function refresh() {
  loading.value = true
  try {
    const page = await listOrgs({
      keyword: pager.keyword || undefined,
      page: pager.page,
      size: pager.size,
    })
    orgs.value = page.list
    total.value = page.total
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '加载组织失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.name = ''
  form.code = ''
  form.description = ''
  dialogVisible.value = true
}

function openEdit(org: OrganizationVO) {
  editingId.value = org.id
  form.name = org.name
  form.code = org.code
  form.description = org.description ?? ''
  dialogVisible.value = true
}

async function submit() {
  if (submitting.value) return
  if (!form.name.trim()) {
    ElMessage.warning('请输入组织名称')
    return
  }
  if (!form.code.trim() && editingId.value === null) {
    ElMessage.warning('请输入组织编码')
    return
  }
  submitting.value = true
  try {
    if (editingId.value === null) {
      await createOrg({
        name: form.name.trim(),
        code: form.code.trim().toUpperCase(),
        description: form.description.trim() || undefined,
      })
      ElMessage.success('组织已创建')
    } else {
      await updateOrg(editingId.value, {
        name: form.name.trim(),
        description: form.description.trim() || undefined,
      })
      ElMessage.success('组织已更新')
    }
    dialogVisible.value = false
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '保存失败')
  } finally {
    submitting.value = false
  }
}

async function handleDelete(org: OrganizationVO) {
  try {
    await ElMessageBox.confirm(
      `确认删除组织 ${org.name}（${org.code}）？其部门与成员关系将一并移除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteOrg(org.id)
    ElMessage.success('组织已删除')
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '删除失败')
  }
}

function search() {
  pager.page = 1
  refresh()
}

onMounted(refresh)
</script>

<template>
  <section class="orgs-view">
    <div class="orgs-view__toolbar">
      <h2>组织管理</h2>
      <el-input
        v-model="pager.keyword"
        placeholder="搜索名称/编码"
        clearable
        style="width: 220px"
        @keyup.enter="search"
      />
      <el-button @click="search">搜索</el-button>
      <el-button v-if="auth.hasPermission('org:create')" type="primary" @click="openCreate">
        新建组织
      </el-button>
    </div>

    <el-table v-loading="loading" :data="orgs" border :empty-text="'暂无组织'">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="code" label="编码" min-width="140" />
      <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <RowActions
            :groups="[
              [{ label: '管理', onClick: () => router.push(`/system/organizations/${row.id}`) }],
              [{ label: '编辑', permission: 'org:update', onClick: () => openEdit(row) }],
              [{ label: '删除', permission: 'org:delete', type: 'danger', onClick: () => handleDelete(row) }],
            ]"
          />
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > pager.size"
      class="orgs-view__pager"
      layout="prev, pager, next, total"
      :total="total"
      :page-size="pager.size"
      :current-page="pager.page"
      @current-change="(p: number) => { pager.page = p; refresh() }"
    />

    <el-dialog v-model="dialogVisible" :title="editingId === null ? '新建组织' : '编辑组织'" width="440px">
      <el-form label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="组织名称" />
        </el-form-item>
        <el-form-item label="编码">
          <el-input v-model="form.code" :disabled="editingId !== null" placeholder="如 ACME_HQ" />
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
.orgs-view {
  max-width: 960px;
  margin: 16px auto;
}
.orgs-view__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.orgs-view__toolbar h2 {
  margin-right: auto;
}
.orgs-view__pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
