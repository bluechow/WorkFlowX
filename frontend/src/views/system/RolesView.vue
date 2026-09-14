<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  assignRolePermissions,
  createRole,
  deleteRole,
  getRolePermissions,
  listPermissions,
  listRoles,
  updateRole,
} from '@/api/rbac'
import type { PermissionVO, RoleVO } from '@/types/api'

/**
 * 角色管理（P3-04）：真实 API 列表/新建/编辑/删除/权限分配。
 * UX 约束: 系统角色禁止删除（按钮禁用）——真正保护在后端（400）。
 */
const auth = useAuthStore()
const roles = ref<RoleVO[]>([])
const permissions = ref<PermissionVO[]>([])
const loading = ref(false)
const submitting = ref(false)

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const permDialogVisible = ref(false)
const permDialogRole = ref<RoleVO | null>(null)
const checkedPermissions = ref<string[]>([])

const form = reactive({ code: '', name: '', description: '' })

async function refresh() {
  loading.value = true
  try {
    roles.value = await listRoles()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '加载角色失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.code = ''
  form.name = ''
  form.description = ''
  dialogVisible.value = true
}

function openEdit(role: RoleVO) {
  editingId.value = role.id
  form.code = role.code
  form.name = role.name
  form.description = role.description ?? ''
  dialogVisible.value = true
}

async function submit() {
  if (submitting.value) return
  if (!form.code.trim() && editingId.value === null) {
    ElMessage.warning('请输入角色编码')
    return
  }
  if (!form.name.trim()) {
    ElMessage.warning('请输入角色名称')
    return
  }
  submitting.value = true
  try {
    if (editingId.value === null) {
      await createRole({ code: form.code.trim().toUpperCase(), name: form.name.trim(), description: form.description.trim() || undefined })
      ElMessage.success('角色已创建')
    } else {
      await updateRole(editingId.value, { name: form.name.trim(), description: form.description.trim() || undefined })
      ElMessage.success('角色已更新')
    }
    dialogVisible.value = false
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '保存失败')
  } finally {
    submitting.value = false
  }
}

async function handleDelete(role: RoleVO) {
  try {
    await ElMessageBox.confirm(`确认删除角色 ${role.name}（${role.code}）？其用户与权限绑定将一并移除。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteRole(role.id)
    ElMessage.success('角色已删除')
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '删除失败')
  }
}

async function openPermissions(role: RoleVO) {
  permDialogRole.value = role
  try {
    const [all, checked] = await Promise.all([listPermissions(), getRolePermissions(role.id)])
    permissions.value = all
    checkedPermissions.value = checked
    permDialogVisible.value = true
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '加载权限失败')
  }
}

async function submitPermissions() {
  if (!permDialogRole.value || submitting.value) return
  submitting.value = true
  try {
    await assignRolePermissions(permDialogRole.value.id, checkedPermissions.value)
    ElMessage.success('权限已更新')
    permDialogVisible.value = false
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '权限保存失败')
  } finally {
    submitting.value = false
  }
}

onMounted(refresh)
</script>

<template>
  <section class="roles-view">
    <div class="roles-view__toolbar">
      <h2>角色管理</h2>
      <el-button v-if="auth.hasPermission('role:create')" type="primary" @click="openCreate">新建角色</el-button>
    </div>

    <el-table v-loading="loading" :data="roles" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="code" label="编码" min-width="140" />
      <el-table-column prop="name" label="名称" min-width="120" />
      <el-table-column label="系统角色" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.system" type="warning" size="small">系统</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button v-if="auth.hasPermission('role:assign_permission')" link type="primary" @click="openPermissions(row)">权限</el-button>
          <el-button v-if="auth.hasPermission('role:update')" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="auth.hasPermission('role:delete')" link type="danger" :disabled="row.system" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingId === null ? '新建角色' : '编辑角色'" width="440px">
      <el-form label-width="80px">
        <el-form-item label="编码">
          <el-input v-model="form.code" :disabled="editingId !== null" placeholder="如 PROJECT_MANAGER" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="角色名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="permDialogVisible" :title="`权限分配 - ${permDialogRole?.name ?? ''}`" width="520px">
      <el-checkbox-group v-model="checkedPermissions">
        <el-checkbox v-for="p in permissions" :key="p.id" :value="p.code" class="perm-checkbox">
          {{ p.code }}（{{ p.name }}）
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="submitting" @click="submitPermissions">
          保存
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.roles-view {
  max-width: 960px;
  margin: 16px auto;
}
.roles-view__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.perm-checkbox {
  display: block;
  margin: 0 0 8px;
}
</style>
