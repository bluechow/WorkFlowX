<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getUserRoles, listRoles, replaceUserRoles } from '@/api/rbac'
import { listUsers } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import type { PageVO, RoleVO, UserVO } from '@/types/api'

/**
 * 用户角色管理（P3-04）：用户列表 + 角色分配对话框（replace 语义，一次提交）。
 * 前端仅 UX；授权边界在后端（user:assign_role authority）。
 */
const auth = useAuthStore()
const page = ref<PageVO<UserVO> | null>(null)
const roles = ref<RoleVO[]>([])
const loading = ref(false)
const submitting = ref(false)
const pagener = reactive({ page: 1, size: 10, keyword: '' })

const dialogVisible = ref(false)
const dialogUser = ref<UserVO | null>(null)
const checkedRoles = ref<string[]>([])

async function refresh() {
  loading.value = true
  try {
    // P18 修复: 改用 api/user.ts 的 listUsers（内部正确解包 Result——
    // 原内联 http.get 把 Result 包装当 PageVO 返回，导致用户列表恒为空）
    page.value = await listUsers(
      pagener.keyword || undefined,
      undefined,
      pagener.page,
      pagener.size,
    )
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '加载用户失败')
  } finally {
    loading.value = false
  }
}

async function openAssign(user: UserVO) {
  dialogUser.value = user
  try {
    const [all, checked] = await Promise.all([listRoles(), getUserRoles(user.id)])
    roles.value = all
    checkedRoles.value = checked
    dialogVisible.value = true
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '加载角色失败')
  }
}

async function submitAssign() {
  if (!dialogUser.value || submitting.value) return
  submitting.value = true
  try {
    await replaceUserRoles(dialogUser.value.id, checkedRoles.value)
    ElMessage.success('用户角色已更新')
    dialogVisible.value = false
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '保存失败')
  } finally {
    submitting.value = false
  }
}

async function search() {
  pagener.page = 1
  await refresh()
}

onMounted(refresh)
</script>

<template>
  <section class="user-roles-view">
    <div class="user-roles-view__toolbar">
      <h2>用户角色管理</h2>
      <el-input
        v-model="pagener.keyword"
        placeholder="搜索用户名/邮箱/昵称"
        clearable
        style="width: 240px"
        @keyup.enter="search"
      />
      <el-button @click="search">搜索</el-button>
    </div>

    <el-table v-loading="loading" :data="page?.list ?? []" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="nickname" label="昵称" min-width="120" />
      <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="90" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button v-if="auth.hasPermission('user:assign_role')" link type="primary" @click="openAssign(row)">分配角色</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="page"
      class="user-roles-view__pager"
      layout="prev, pager, next, total"
      :total="page.total"
      :page-size="page.size"
      :current-page="page.page"
      @current-change="(p: number) => { pagener.page = p; refresh() }"
    />

    <el-dialog v-model="dialogVisible" :title="`分配角色 - ${dialogUser?.username ?? ''}`" width="440px">
      <el-checkbox-group v-model="checkedRoles">
        <el-checkbox v-for="r in roles" :key="r.id" :value="r.code" class="role-checkbox">
          {{ r.code }}（{{ r.name }}）
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="submitting" @click="submitAssign">
          保存
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.user-roles-view {
  max-width: 960px;
  margin: 16px auto;
}
.user-roles-view__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.user-roles-view__toolbar h2 {
  margin-right: auto;
}
.user-roles-view__pager {
  margin-top: 12px;
  justify-content: flex-end;
}
.role-checkbox {
  display: block;
  margin: 0 0 8px;
}
</style>
