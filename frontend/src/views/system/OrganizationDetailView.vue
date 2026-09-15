<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addOrgMember,
  createDepartment,
  deleteDepartment,
  getOrg,
  listDepartments,
  listOrgMembers,
  removeOrgMember,
  updateDepartment,
} from '@/api/org'
import { useAuthStore } from '@/stores/auth'
import type { DepartmentVO, OrganizationMemberVO, OrganizationVO } from '@/types/api'

/**
 * 组织详情（P4-03）：部门树管理 + 成员管理。
 * UX 约束: OWNER 行禁止移除（后端 400 兜底）；部门调整父级在对话框中选择同组织其他部门。
 */
const auth = useAuthStore()
const route = useRoute()
const orgId = Number(route.params.id)

const org = ref<OrganizationVO | null>(null)
const departments = ref<DepartmentVO[]>([])
const members = ref<OrganizationMemberVO[]>([])
const loading = ref(false)
const submitting = ref(false)

const deptDialogVisible = ref(false)
const deptEditingId = ref<number | null>(null)
const deptForm = reactive({ name: '', code: '', parentId: null as number | null })

const memberDialogVisible = ref(false)
const memberForm = reactive({ userId: null as number | null, role: 'MEMBER' as 'ADMIN' | 'MEMBER' })

interface DeptTreeNode {
  id: number
  label: string
  code: string
  children: DeptTreeNode[]
}

const deptTree = computed<DeptTreeNode[]>(() => {
  const nodes = new Map<number, DeptTreeNode>()
  for (const d of departments.value) {
    nodes.set(d.id, { id: d.id, label: `${d.name}（${d.code}）`, code: d.code, children: [] })
  }
  const roots: DeptTreeNode[] = []
  for (const d of departments.value) {
    const node = nodes.get(d.id)!
    if (d.parentId && nodes.has(d.parentId)) {
      nodes.get(d.parentId)!.children.push(node)
    } else {
      roots.push(node)
    }
  }
  return roots
})

const parentOptions = computed(() =>
  departments.value.filter((d) => d.id !== deptEditingId.value),
)

async function refresh() {
  loading.value = true
  try {
    const [orgData, deptData, memberData] = await Promise.all([
      getOrg(orgId),
      listDepartments(orgId),
      listOrgMembers(orgId),
    ])
    org.value = orgData
    departments.value = deptData
    members.value = memberData
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '加载组织详情失败')
  } finally {
    loading.value = false
  }
}

function openCreateDept(parentId: number | null) {
  deptEditingId.value = null
  deptForm.name = ''
  deptForm.code = ''
  deptForm.parentId = parentId
  deptDialogVisible.value = true
}

function openEditDept(dept: DepartmentVO) {
  deptEditingId.value = dept.id
  deptForm.name = dept.name
  deptForm.code = dept.code
  deptForm.parentId = dept.parentId
  deptDialogVisible.value = true
}

async function submitDept() {
  if (submitting.value) return
  if (!deptForm.name.trim()) {
    ElMessage.warning('请输入部门名称')
    return
  }
  submitting.value = true
  try {
    if (deptEditingId.value === null) {
      await createDepartment(orgId, {
        name: deptForm.name.trim(),
        code: deptForm.code.trim(),
        parentId: deptForm.parentId,
      })
      ElMessage.success('部门已创建')
    } else {
      await updateDepartment(deptEditingId.value, {
        name: deptForm.name.trim(),
        parentId: deptForm.parentId,
      })
      ElMessage.success('部门已更新')
    }
    deptDialogVisible.value = false
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '保存失败')
  } finally {
    submitting.value = false
  }
}

async function handleDeleteDept(dept: DepartmentVO) {
  try {
    await ElMessageBox.confirm(`确认删除部门 ${dept.name}？其子部门将提升为根部门。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteDepartment(dept.id)
    ElMessage.success('部门已删除')
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '删除失败')
  }
}

async function openAddMember() {
  memberForm.userId = null
  memberForm.role = 'MEMBER'
  memberDialogVisible.value = true
}

async function submitMember() {
  if (submitting.value) return
  if (!memberForm.userId) {
    ElMessage.warning('请输入用户 ID')
    return
  }
  submitting.value = true
  try {
    await addOrgMember(orgId, {
      userId: memberForm.userId,
      role: memberForm.role,
    })
    ElMessage.success('成员已添加')
    memberDialogVisible.value = false
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '添加失败')
  } finally {
    submitting.value = false
  }
}

async function handleRemoveMember(member: OrganizationMemberVO) {
  try {
    await ElMessageBox.confirm(
      `确认将用户 #${member.userId} 移出组织？`,
      '移除确认',
      { type: 'warning', confirmButtonText: '移除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await removeOrgMember(orgId, member.userId)
    ElMessage.success('成员已移除')
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '移除失败')
  }
}

onMounted(refresh)
</script>

<template>
  <section v-loading="loading" class="org-detail">
    <el-page-header content="组织详情" class="org-detail__header" @back="$router.back()" />

    <el-descriptions v-if="org" :column="2" border class="org-detail__info">
      <el-descriptions-item label="名称">{{ org.name }}</el-descriptions-item>
      <el-descriptions-item label="编码">{{ org.code }}</el-descriptions-item>
      <el-descriptions-item label="OWNER 用户 ID">{{ org.ownerId }}</el-descriptions-item>
      <el-descriptions-item label="描述">{{ org.description ?? '—' }}</el-descriptions-item>
    </el-descriptions>

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="org-detail__section-head">
          <h3>部门</h3>
          <el-button
            v-if="auth.hasPermission('department:create')"
            size="small"
            type="primary"
            @click="openCreateDept(null)"
          >
            新建根部门
          </el-button>
        </div>
        <el-tree
          v-if="deptTree.length"
          :data="deptTree"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
        >
          <template #default="{ data }">
            <span class="org-detail__dept-node">
              <span>{{ data.label }}</span>
              <span v-if="auth.hasPermission('department:update') || auth.hasPermission('department:create') || auth.hasPermission('department:delete')">
                <el-button
                  v-if="auth.hasPermission('department:create')"
                  link
                  type="primary"
                  size="small"
                  @click="openCreateDept(data.id)"
                >
                  添加子部门
                </el-button>
                <el-button
                  v-if="auth.hasPermission('department:update')"
                  link
                  type="primary"
                  size="small"
                  @click="openEditDept(departments.find((d) => d.id === data.id)!)"
                >
                  编辑
                </el-button>
                <el-button
                  v-if="auth.hasPermission('department:delete')"
                  link
                  type="danger"
                  size="small"
                  @click="handleDeleteDept(departments.find((d) => d.id === data.id)!)"
                >
                  删除
                </el-button>
              </span>
            </span>
          </template>
        </el-tree>
        <el-empty v-else description="暂无部门" :image-size="60" />
      </el-col>

      <el-col :span="12">
        <div class="org-detail__section-head">
          <h3>成员</h3>
          <el-button
            v-if="auth.hasPermission('org:assign_member')"
            size="small"
            type="primary"
            @click="openAddMember"
          >
            添加成员
          </el-button>
        </div>
        <el-table :data="members" border size="small" empty-text="暂无成员">
          <el-table-column prop="userId" label="用户 ID" width="80" />
          <el-table-column prop="role" label="组织角色" width="100">
            <template #default="{ row }">
              <el-tag :type="row.role === 'OWNER' ? 'warning' : 'info'" size="small">
                {{ row.role }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="部门" min-width="120">
            <template #default="{ row }">
              {{ departments.find((d) => d.id === row.departmentId)?.name ?? '未分配' }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button
                v-if="auth.hasPermission('org:assign_member')"
                link
                type="danger"
                size="small"
                :disabled="row.role === 'OWNER'"
                @click="handleRemoveMember(row)"
              >
                移除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-col>
    </el-row>

    <el-dialog v-model="deptDialogVisible" :title="deptEditingId === null ? '新建部门' : '编辑部门'" width="440px">
      <el-form label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="deptForm.name" placeholder="部门名称" />
        </el-form-item>
        <el-form-item v-if="deptEditingId === null" label="编码">
          <el-input v-model="deptForm.code" placeholder="组织内唯一，如 RD" />
        </el-form-item>
        <el-form-item label="父部门">
          <el-select v-model="deptForm.parentId" clearable placeholder="不选=根部门" style="width: 100%">
            <el-option
              v-for="d in parentOptions"
              :key="d.id"
              :label="`${d.name}（${d.code}）`"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deptDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="submitting" @click="submitDept">
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="memberDialogVisible" title="添加成员" width="400px">
      <el-form label-width="80px">
        <el-form-item label="用户 ID">
          <el-input-number v-model="memberForm.userId" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="memberForm.role" style="width: 100%">
            <el-option label="MEMBER" value="MEMBER" />
            <el-option label="ADMIN" value="ADMIN" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="memberDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="submitting" @click="submitMember">
          添加
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.org-detail {
  max-width: 1080px;
  margin: 16px auto;
}
.org-detail__header {
  margin-bottom: 16px;
}
.org-detail__info {
  margin-bottom: 16px;
}
.org-detail__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.org-detail__dept-node {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}
</style>
