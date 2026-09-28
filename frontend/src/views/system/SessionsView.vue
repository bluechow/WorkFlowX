<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { kickSession, listSessions } from '@/api/session'
import type { SessionVO } from '@/api/session'

/**
 * 在线会话管理（Phase A-⑤，ADMIN）：查看全部在线用户与剩余有效期，远程踢下线。
 * 踢下线为高影响操作（对方立即 401）→ 确认对话框；不能踢自己（后端 400 兜底）。
 */
const sessions = ref<SessionVO[]>([])
const loading = ref(false)
const kicking = ref(false)

function ttlText(seconds: number | null): string {
  if (seconds == null || seconds < 0) return '已过期'
  if (seconds >= 3600) return `${Math.floor(seconds / 3600)} 小时 ${Math.round((seconds % 3600) / 60)} 分`
  if (seconds >= 60) return `${Math.floor(seconds / 60)} 分钟`
  return `${seconds} 秒`
}

async function refresh() {
  loading.value = true
  try {
    sessions.value = await listSessions()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '加载会话列表失败')
  } finally {
    loading.value = false
  }
}

async function kick(row: SessionVO) {
  try {
    await ElMessageBox.confirm(
      `确认将用户「${row.nickname || row.username}」踢下线？其当前登录将立即失效。`,
      '确认操作',
      { type: 'warning', confirmButtonText: '踢下线', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  kicking.value = true
  try {
    await kickSession(row.userId)
    ElMessage.success('已踢下线')
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '操作失败')
  } finally {
    kicking.value = false
  }
}

onMounted(refresh)
</script>

<template>
  <section class="sessions-view">
    <div class="sessions-view__toolbar">
      <h2>在线会话</h2>
      <el-button :loading="loading" @click="refresh">刷新</el-button>
    </div>
    <p class="sessions-view__hint">
      当前登录的用户及其会话剩余有效期。踢下线后，该用户的下一个请求将立即失效，需重新登录。
    </p>

    <el-table v-loading="loading" :data="sessions" border empty-text="当前没有在线会话">
      <el-table-column prop="userId" label="ID" width="80" />
      <el-table-column prop="username" label="用户名" min-width="140" />
      <el-table-column label="昵称" min-width="120">
        <template #default="{ row }">{{ row.nickname || '—' }}</template>
      </el-table-column>
      <el-table-column label="角色" width="140">
        <template #default="{ row }">
          <el-tag v-for="r in row.roles" :key="r" size="small" class="sessions-view__role">{{ r }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="会话剩余有效期" width="160">
        <template #default="{ row }">{{ ttlText(row.expiresInSeconds) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.self"
            size="small"
            disabled
            title="不能踢出自己的会话，请使用右上角退出登录"
          >
            当前登录
          </el-button>
          <el-button
            v-else
            size="small"
            type="danger"
            :loading="kicking"
            @click="kick(row)"
          >
            踢下线
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<style scoped>
.sessions-view {
  max-width: 960px;
  margin: 16px auto;
}
.sessions-view__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.sessions-view__toolbar h2 {
  margin: 0;
  font-size: 20px;
}
.sessions-view__hint {
  color: #909399;
  font-size: 12px;
  margin: 0 0 12px;
}
.sessions-view__role {
  margin-right: 4px;
}
</style>
