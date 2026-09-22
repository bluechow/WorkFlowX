<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createUser,
  listUsers,
  updateUser,
  updateUserStatus,
  type UserStatus,
  type UserVO,
} from '@/api/user'
import { useAuthStore } from '@/stores/auth'

/**
 * 用户管理（P11-02 补齐 G2）：ADMIN 专属页面（后端 user:* authority 强制）。
 * 自我保护: 不允许禁用/锁定自己（对齐后端"不能修改自己的状态"守卫，前端按钮禁用为 UX）。
 * 状态变更（禁用/锁定）为高影响操作——确认对话框；禁用即踢线（Phase 2 语义）。
 */
const auth = useAuthStore()

const users = ref<UserVO[]>([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const statusChanging = ref(false)
const pager = reactive({ page: 1, size: 20, keyword: '' })
const filters = reactive({ status: undefined as undefined | UserStatus })

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const form = reactive({
  username: '',
  email: '',
  password: '',
  nickname: '',
})

async function refresh() {
  loading.value = true
  loadError.value = ''
  try {
    const page = await listUsers(pager.keyword || undefined, filters.status, pager.page, pager.size)
    users.value = page.list
    total.value = page.total
  } catch (e) {
    loadError.value = (e as { message?: string }).message ?? '用户列表加载失败'
    users.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  pager.page = 1
  refresh()
}

function openCreate() {
  editingId.value = null
  form.username = ''
  form.email = ''
  form.password = ''
  form.nickname = ''
  dialogVisible.value = true
}

function openEdit(user: UserVO) {
  editingId.value = user.id
  form.username = user.username
  form.email = user.email
  form.password = ''
  form.nickname = user.nickname ?? ''
  dialogVisible.value = true
}

async function submit() {
  if (submitting.value) return
  if (!form.username.trim() || !form.email.trim()) {
    ElMessage.warning('用户名与邮箱不能为空')
    return
  }
  if (editingId.value === null && form.password.length < 8) {
    ElMessage.warning('初始密码至少 8 位')
    return
  }
  submitting.value = true
  try {
    if (editingId.value === null) {
      await createUser({
        username: form.username.trim(),
        email: form.email.trim(),
        password: form.password,
        nickname: form.nickname.trim() || undefined,
      })
      ElMessage.success('用户已创建')
    } else {
      await updateUser(editingId.value, {
        email: form.email.trim(),
        nickname: form.nickname.trim() || undefined,
      })
      ElMessage.success('用户已更新')
    }
    dialogVisible.value = false
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '保存失败')
  } finally {
    submitting.value = false
  }
}

async function changeStatus(user: UserVO, status: UserStatus) {
  if (statusChanging.value) return
  if (user.id === auth.currentUser?.id) {
    ElMessage.warning('不能修改自己的状态')
    return
  }
  const label = status === 'ACTIVE' ? '恢复启用' : status === 'DISABLED' ? '禁用（踢线）' : '锁定'
  try {
    await ElMessageBox.confirm(`确认${label}用户 ${user.username}？`, '确认操作', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return // 用户取消
  }
  statusChanging.value = true
  try {
    await updateUserStatus(user.id, status)
    ElMessage.success(`已${label}`)
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '状态变更失败')
  } finally {
    statusChanging.value = false
  }
}

onMounted(() => {
  if (auth.hasPermission('user:list')) {
    refresh()
  }
})
</script>

<template>
  <section class="users-view">
    <div class="users-view__toolbar">
      <h2>用户管理</h2>
      <el-button
        v-if="auth.hasPermission('user:create')"
        type="primary"
        @click="openCreate"
      >
        新建用户
      </el-button>
    </div>

    <el-alert
      v-if="!auth.hasPermission('user:list')"
      type="warning"
      title="您没有查看用户列表的权限（user:list）——请联系管理员"
      :closable="false"
      show-icon
      data-test="no-permission"
    />

    <template v-else>
      <div class="users-view__filters">
        <el-input
          v-model="pager.keyword"
          placeholder="搜索用户名/邮箱/昵称"
          clearable
          style="width: 220px"
          @keyup.enter="search"
        />
        <el-select v-model="filters.status" clearable placeholder="状态" style="width: 120px" @change="search">
          <el-option label="ACTIVE" value="ACTIVE" />
          <el-option label="DISABLED" value="DISABLED" />
          <el-option label="LOCKED" value="LOCKED" />
        </el-select>
        <el-button @click="search">搜索</el-button>
      </div>

      <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" />
      <el-table
        v-else
        v-loading="loading"
        :data="users"
        border
        empty-text="暂无用户"
        data-test="users-table"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="email" label="邮箱" min-width="200" show-overflow-tooltip />
        <el-table-column label="昵称" width="120">
          <template #default="{ row }">{{ row.nickname ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最近登录" width="170">
          <template #default="{ row }">{{ row.lastLoginAt ?? '从未' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button
              v-if="auth.hasPermission('user:update')"
              link
              type="primary"
              size="small"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <template v-if="auth.hasPermission('user:status')">
              <el-button
                v-if="row.status === 'ACTIVE' && row.id !== auth.currentUser?.id"
                link
                type="danger"
                size="small"
                :disabled="statusChanging"
                @click="changeStatus(row, 'DISABLED')"
              >
                禁用
              </el-button>
              <el-button
                v-if="row.status === 'ACTIVE' && row.id !== auth.currentUser?.id"
                link
                type="warning"
                size="small"
                :disabled="statusChanging"
                @click="changeStatus(row, 'LOCKED')"
              >
                锁定
              </el-button>
              <el-button
                v-if="row.status !== 'ACTIVE'"
                link
                type="success"
                size="small"
                :disabled="statusChanging"
                @click="changeStatus(row, 'ACTIVE')"
              >
                恢复启用
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > pager.size"
        class="users-view__pager"
        layout="prev, pager, next, total"
        :total="total"
        :page-size="pager.size"
        :current-page="pager.page"
        @current-change="(p: number) => { pager.page = p; refresh() }"
      />
    </template>

    <el-dialog v-model="dialogVisible" :title="editingId === null ? '新建用户' : '编辑用户'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" :disabled="editingId !== null" placeholder="登录名（不可变）" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="name@example.com" />
        </el-form-item>
        <el-form-item v-if="editingId === null" label="初始密码">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 8 位" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="可空" />
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
.users-view {
  max-width: 1200px;
  margin: 16px auto;
}
.users-view__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.users-view__filters {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.users-view__pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
