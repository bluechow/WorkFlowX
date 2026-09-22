<script setup lang="ts">
import { onMounted } from 'vue'
import NotificationList from './NotificationList.vue'
import { useNotificationStore } from '@/stores/notification'

/**
 * Header 通知铃铛（P9-09）。badge 未读数真实来自 API；
 * 未读数策略: 页面初始化 + 操作后刷新（无轮询，ADR-019）。
 */
const store = useNotificationStore()

onMounted(() => {
  store.refreshUnreadCount()
})
</script>

<template>
  <el-popover placement="bottom-end" :width="420" trigger="click">
    <template #reference>
      <el-badge :value="store.unreadCount" :hidden="store.unreadCount === 0" :max="99">
        <el-button circle aria-label="通知中心" data-test="bell-button">
          <span class="notification-bell__icon">🔔</span>
        </el-button>
      </el-badge>
    </template>
    <NotificationList />
  </el-popover>
</template>

<style scoped>
.notification-bell__icon {
  font-size: 14px;
}
</style>
