<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import RowActions from '@/components/RowActions.vue'
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
 * P18+: 权限分配对话框按模块分组 + 页面入口标记，管理员一目了然。
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
const expandedGroups = ref<string[]>([])

const form = reactive({ code: '', name: '', description: '' })

// ===== 权限模块分组（P18：让管理员一眼知道勾哪几个能开哪个页面） =====

interface PermGroup {
  key: string
  label: string
  entryCode: string | null
  entryLabel: string
  desc: string
  /** 除 key 外还归入本组的前缀（如 department:* 归入组织管理） */
  also?: string[]
}

const PERM_GROUPS: PermGroup[] = [
  { key: 'user', label: '用户管理', entryCode: 'user:list', entryLabel: '用户管理页面', desc: '用户增删改查、禁用/锁定' },
  { key: 'role', label: '角色管理', entryCode: 'role:list', entryLabel: '角色管理页面', desc: '角色增删改查' },
  { key: 'permission', label: '权限管理', entryCode: 'permission:list', entryLabel: '权限管理页面', desc: '查看系统权限列表' },
  { key: 'org', label: '组织管理', entryCode: 'org:list', entryLabel: '组织管理页面（含部门/成员）', desc: '组织/部门/组织成员', also: ['department'] },
  { key: 'project', label: '项目管理', entryCode: 'project:list', entryLabel: '项目管理页面（含 Issue/用例库/测试计划入口）', desc: '项目增删改查、归档/恢复、项目成员' },
  { key: 'issue', label: 'Issue 管理', entryCode: 'issue:list', entryLabel: '项目内 Issue 列表', desc: 'Issue 增删改查、分派、状态流转' },
  { key: 'testcase', label: '用例库', entryCode: 'testcase:list', entryLabel: '项目内测试用例库', desc: '用例目录/用例增删改查' },
  { key: 'testplan', label: '测试计划', entryCode: 'testplan:list', entryLabel: '项目内测试计划与执行', desc: '计划创建/用例挑选/执行打结果/Bug 关联' },
  { key: 'comment', label: 'Issue 评论', entryCode: null, entryLabel: '', desc: 'Issue 内发表/编辑/删除评论' },
  { key: 'attachment', label: 'Issue 附件', entryCode: null, entryLabel: '', desc: 'Issue 内上传/下载/删除附件' },
  { key: 'audit', label: '审计日志', entryCode: 'audit:list', entryLabel: '审计日志页面', desc: '查看系统审计日志' },
  { key: 'dashboard', label: '数据仪表盘', entryCode: 'dashboard:view', entryLabel: 'Dashboard 统计图表', desc: '查看项目/Issue 统计与趋势' },
]

/** 按分组归类权限（Service 层权限已在 groups 外兜底显示为「其他」） */
const groupedPermissions = computed(() => {
  const groups: { group: PermGroup; perms: PermissionVO[] }[] = []
  const used = new Set<string>()
  for (const g of PERM_GROUPS) {
    const prefixes = [g.key, ...(g.also ?? [])]
    const perms = permissions.value.filter((p) => prefixes.some((k) => p.code.startsWith(k + ':')))
    if (perms.length > 0) {
      groups.push({ group: g, perms })
      perms.forEach((p) => used.add(p.code))
    }
  }
  const others = permissions.value.filter((p) => !used.has(p.code))
  if (others.length > 0) {
    groups.push({
      group: { key: '_other', label: '其他', entryCode: null, entryLabel: '', desc: '' },
      perms: others,
    })
  }
  return groups
})

/** 分组勾选状态以 Service 实际返回的权限为准（codes 由 perms 推导，避免静态清单漂移） */
function groupCheckedCount(perms: PermissionVO[]): number {
  return perms.filter((p) => checkedPermissions.value.includes(p.code)).length
}

function isGroupAllChecked(perms: PermissionVO[]): boolean {
  return perms.every((p) => checkedPermissions.value.includes(p.code))
}

function toggleGroup(perms: PermissionVO[], checked: boolean) {
  const codes = perms.map((p) => p.code)
  if (checked) {
    checkedPermissions.value = [...new Set([...checkedPermissions.value, ...codes])]
  } else {
    checkedPermissions.value = checkedPermissions.value.filter((c) => !codes.includes(c))
  }
}

// ===== 原有逻辑 =====

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
    // 默认展开所有有勾选的分组
    expandedGroups.value = groupedPermissions.value
      .filter((g) => g.perms.some((p) => checked.includes(p.code)))
      .map((g) => g.group.key)
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
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <RowActions
            :groups="[
              [{ label: '权限', permission: 'role:assign_permission', onClick: () => openPermissions(row) }],
              [{ label: '编辑', permission: 'role:update', onClick: () => openEdit(row) }],
              [{ label: '删除', permission: 'role:delete', type: 'danger', disabled: row.system, onClick: () => handleDelete(row) }],
            ]"
          />
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

    <!-- P18: 权限分配对话框——按模块分组 + 页面入口标记 -->
    <el-dialog v-model="permDialogVisible" :title="`权限分配 - ${permDialogRole?.name ?? ''}`" width="640px">
      <el-alert type="info" :closable="false" class="perm-dialog__tip">
        <template #title>
          勾选带
          <el-tag size="small" type="success" class="perm-dialog__entry-badge">页面</el-tag>
          标记的权限后，该角色登录即可看到并使用对应功能模块
        </template>
      </el-alert>

      <el-collapse v-model="expandedGroups" class="perm-dialog__collapse">
        <el-collapse-item
          v-for="{ group, perms } in groupedPermissions"
          :key="group.key"
          :name="group.key"
        >
          <template #title>
            <span class="perm-dialog__group-title">
              {{ group.label }}
              <el-tag size="small" type="info" class="perm-dialog__count">
                {{ groupCheckedCount(perms) }}/{{ perms.length }}
              </el-tag>
              <el-tag
                v-if="isGroupAllChecked(perms)"
                size="small"
                type="success"
                class="perm-dialog__count"
              >
                全部
              </el-tag>
            </span>
          </template>
          <div class="perm-dialog__group-desc">{{ group.desc }}</div>
          <div class="perm-dialog__group-actions">
            <el-button link size="small" @click="toggleGroup(perms, true)">全选</el-button>
            <el-button link size="small" @click="toggleGroup(perms, false)">全不选</el-button>
          </div>
          <el-checkbox-group v-model="checkedPermissions">
            <div v-for="p in perms" :key="p.id" class="perm-dialog__perm-row">
              <el-checkbox :value="p.code" class="perm-dialog__perm-check">
                <span v-if="p.code === group.entryCode" class="perm-dialog__entry-tag">
                  <el-tag size="small" type="success">页面</el-tag>
                </span>
                {{ p.code }}
                <span class="perm-dialog__perm-name">（{{ p.name }}）</span>
              </el-checkbox>
            </div>
          </el-checkbox-group>
        </el-collapse-item>
      </el-collapse>

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
.perm-dialog__tip {
  margin-bottom: 12px;
}
.perm-dialog__entry-badge {
  margin: 0 2px;
}
.perm-dialog__collapse {
  border: none;
}
.perm-dialog__group-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}
.perm-dialog__count {
  margin-left: 4px;
}
.perm-dialog__group-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  padding: 0 0 6px;
}
.perm-dialog__group-actions {
  padding: 0 0 6px;
}
.perm-dialog__perm-row {
  padding: 2px 0;
}
.perm-dialog__perm-check {
  width: 100%;
}
.perm-dialog__entry-tag {
  margin-right: 4px;
}
.perm-dialog__perm-name {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
