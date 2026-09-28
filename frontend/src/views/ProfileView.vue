<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import { changePassword, updateProfile } from '@/api/auth'
import { listMyTodoIssues } from '@/api/me'
import type { TodoIssueVO } from '@/api/me'
import { useAuthStore } from '@/stores/auth'
import {
  ISSUE_PRIORITY_LABELS,
  ISSUE_PRIORITY_TAG_TYPES,
  ISSUE_STATUS_LABELS,
  labelOf,
} from '@/utils/labels'

/**
 * 个人中心（Phase A-④）：我的资料 / 修改密码 / 我的待办。
 * self 资源：修改密码成功后服务端已作废会话，前端清理本地态并跳转登录页。
 */
const auth = useAuthStore()
const router = useRouter()

// ===== 资料 =====
const profileForm = reactive({ email: '', nickname: '' })
const savingProfile = ref(false)

async function submitProfile() {
  if (savingProfile.value) return
  if (!profileForm.email.trim()) {
    ElMessage.warning('请输入邮箱')
    return
  }
  savingProfile.value = true
  try {
    const updated = await updateProfile({
      email: profileForm.email.trim(),
      nickname: profileForm.nickname.trim() || null,
    })
    auth.currentUser = updated
    ElMessage.success('资料已保存')
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '保存失败')
  } finally {
    savingProfile.value = false
  }
}

// ===== 密码 =====
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const changingPwd = ref(false)

async function submitPassword() {
  if (changingPwd.value) return
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    ElMessage.warning('请填写原密码与新密码')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirm) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  changingPwd.value = true
  try {
    await changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
    })
    ElNotification.success({ title: '密码已修改', message: '请使用新密码重新登录' })
    await auth.logout()
    router.push('/login')
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '修改密码失败')
  } finally {
    changingPwd.value = false
  }
}

// ===== 我的待办 =====
const todos = ref<TodoIssueVO[]>([])
const loadingTodos = ref(false)

async function loadTodos() {
  loadingTodos.value = true
  try {
    todos.value = await listMyTodoIssues()
  } catch {
    todos.value = []
  } finally {
    loadingTodos.value = false
  }
}

function goProject(projectId: number) {
  router.push(`/system/projects/${projectId}/board`)
}

onMounted(() => {
  if (auth.currentUser) {
    profileForm.email = auth.currentUser.email
    profileForm.nickname = auth.currentUser.nickname ?? ''
  }
  void loadTodos()
})
</script>

<template>
  <section class="profile-view">
    <h2>个人中心</h2>

    <div class="profile-view__grid">
      <!-- 左列：资料 + 密码 -->
      <div class="profile-view__col">
        <div class="profile-card">
          <h3>我的资料</h3>
          <el-form label-width="90px">
            <el-form-item label="用户名">
              <el-input :model-value="auth.currentUser?.username" disabled />
            </el-form-item>
            <el-form-item label="昵称">
              <el-input v-model="profileForm.nickname" maxlength="50" placeholder="显示昵称" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="profileForm.email" maxlength="100" placeholder="name@example.com" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingProfile" @click="submitProfile">
                保存资料
              </el-button>
            </el-form-item>
          </el-form>
        </div>

        <div class="profile-card">
          <h3>修改密码</h3>
          <el-form label-width="90px">
            <el-form-item label="原密码">
              <el-input v-model="pwdForm.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少 8 位，含字母和数字" />
            </el-form-item>
            <el-form-item label="确认新密码">
              <el-input v-model="pwdForm.confirm" type="password" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="changingPwd" @click="submitPassword">
                修改密码
              </el-button>
            </el-form-item>
          </el-form>
        </div>
      </div>

      <!-- 右列：我的待办 -->
      <div class="profile-card">
        <div class="profile-card__head">
          <h3>我的待办（{{ todos.length }}）</h3>
          <el-button link size="small" :loading="loadingTodos" @click="loadTodos">刷新</el-button>
        </div>
        <p class="profile-card__hint">指派给我且尚未完结的 Issue（最多显示 20 条，按优先级排序）</p>
        <div v-for="t in todos" :key="t.issueId" class="todo-item" @click="goProject(t.projectId)">
          <div class="todo-item__meta">
            <span class="todo-item__no">{{ t.projectKey }}-{{ t.issueNo }}</span>
            <el-tag size="small" :type="(ISSUE_PRIORITY_TAG_TYPES[t.priority] as never) || 'info'">
              {{ labelOf(ISSUE_PRIORITY_LABELS, t.priority) }}
            </el-tag>
            <el-tag v-if="t.severity" size="small" type="danger">{{ t.severity }}</el-tag>
            <el-tag size="small" type="info">{{ labelOf(ISSUE_STATUS_LABELS, t.status) }}</el-tag>
          </div>
          <div class="todo-item__title">{{ t.title }}</div>
          <div class="todo-item__project">{{ t.projectName }}</div>
        </div>
        <div v-if="todos.length === 0 && !loadingTodos" class="profile-card__empty">
          太棒了，没有待办 🎉
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.profile-view {
  max-width: 1000px;
  margin: 16px auto;
}
.profile-view h2 {
  margin: 0 0 12px;
  font-size: 20px;
}
.profile-view__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  align-items: start;
}
.profile-view__col {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.profile-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px 20px;
}
.profile-card h3 {
  margin: 0 0 12px;
  font-size: 15px;
}
.profile-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.profile-card__hint {
  color: #909399;
  font-size: 12px;
  margin: 0 0 10px;
}
.profile-card__empty {
  color: #c0c4cc;
  font-size: 13px;
  text-align: center;
  padding: 24px 0;
}
.todo-item {
  border-bottom: 1px solid #f0f2f5;
  padding: 10px 4px;
  cursor: pointer;
}
.todo-item:hover {
  background: #f5f7fa;
}
.todo-item__meta {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 4px;
}
.todo-item__no {
  color: #409eff;
  font-size: 12px;
  font-weight: 600;
}
.todo-item__title {
  font-size: 13px;
  line-height: 1.4;
}
.todo-item__project {
  color: #909399;
  font-size: 12px;
  margin-top: 2px;
}
</style>
