<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useNotificationStore } from '@/stores/notification'
import type { NotificationVO } from '@/api/notification'

/**
 * 通知列表面板（P9-09）。
 * 点击通知: 标记已读 + 跳转关联 Issue（projectId 缺失=Issue 已删，仅标记已读不跳转防异常）。
 */
const store = useNotificationStore()
const router = useRouter()

const TYPE_LABEL: Record<string, string> = {
  ISSUE_ASSIGNED: '分派',
  ISSUE_STATUS_CHANGED: '状态',
  ISSUE_COMMENTED: '评论',
}

async function onNotificationClick(notification: NotificationVO) {
  if (!notification.isRead) {
    try {
      await store.markRead(notification.id)
    } catch (e) {
      ElMessage.error((e as { message?: string }).message ?? '标记已读失败')
    }
  }
  if (notification.projectId) {
    router.push(`/projects/${notification.projectId}/issues`)
  }
}

async function onMarkAllRead() {
  try {
    await store.markAllRead()
    ElMessage.success('已全部标记为已读')
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '操作失败')
  }
}

onMounted(() => {
  store.fetchList(true)
})
</script>

<template>
  <div class="notification-list">
    <div class="notification-list__head">
      <span class="notification-list__title">通知中心</span>
      <el-button link type="primary" size="small" data-test="mark-all" @click="onMarkAllRead">
        全部已读
      </el-button>
    </div>

    <div v-if="store.listStatus === 'loading' && store.notifications.length === 0"
         v-loading="true" class="notification-list__state" />
    <el-alert v-else-if="store.listStatus === 'failed'" type="error"
              :title="store.listError" :closable="false" />
    <el-empty v-else-if="store.notifications.length === 0" description="暂无通知" :image-size="60" />

    <ul v-else class="notification-list__items">
      <li
        v-for="n in store.notifications"
        :key="n.id"
        class="notification-list__item"
        :class="{ 'notification-list__item--unread': !n.isRead }"
        data-test="notification-item"
        @click="onNotificationClick(n)"
      >
        <div class="notification-list__meta">
          <el-tag size="small" :type="n.isRead ? 'info' : 'primary'">
            {{ TYPE_LABEL[n.type] ?? n.type }}
          </el-tag>
          <span class="notification-list__time">{{ n.createdAt }}</span>
        </div>
        <p class="notification-list__text">{{ n.title }}</p>
        <p v-if="n.content" class="notification-list__content">{{ n.content }}</p>
      </li>
    </ul>

    <div v-if="store.notifications.length < store.total" class="notification-list__more">
      <el-button
        link
        type="primary"
        size="small"
        :loading="store.listStatus === 'loading'"
        @click="store.loadMore()"
      >
        加载更多
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.notification-list__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.notification-list__title {
  font-weight: 600;
}
.notification-list__state {
  min-height: 80px;
}
.notification-list__items {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 360px;
  overflow-y: auto;
}
.notification-list__item {
  padding: 8px 6px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
}
.notification-list__item:hover {
  background: var(--el-fill-color-light);
}
.notification-list__item--unread .notification-list__text {
  font-weight: 600;
}
.notification-list__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.notification-list__time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.notification-list__text {
  margin: 0;
  font-size: 13px;
}
.notification-list__content {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  word-break: break-all;
}
.notification-list__more {
  text-align: center;
  padding-top: 6px;
}
</style>
