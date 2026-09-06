<script setup lang="ts">
import { onMounted } from 'vue'
import { useHealthStore } from '@/stores/health'

const health = useHealthStore()
onMounted(() => health.refresh())
</script>

<template>
  <section class="health">
    <h2>系统健康状态</h2>

    <div v-if="health.state === 'loading'" class="health__body">
      <el-skeleton :rows="2" animated />
    </div>

    <el-alert
      v-else-if="health.state === 'error'"
      class="health__body"
      type="error"
      :closable="false"
      show-icon
      :title="`后端不可达（${health.error?.code ?? '-'}）`"
      :description="health.error?.message ?? '未知错误'"
    />

    <template v-else-if="health.info">
      <el-alert class="health__body" type="success" :closable="false" show-icon title="后端服务正常" />
      <el-descriptions :column="1" border class="health__detail">
        <el-descriptions-item label="service">{{ health.info.service }}</el-descriptions-item>
        <el-descriptions-item label="status">{{ health.info.status }}</el-descriptions-item>
        <el-descriptions-item label="checkedAt">{{ health.info.checkedAt }}</el-descriptions-item>
      </el-descriptions>
    </template>

    <el-button class="health__retry" type="primary" plain @click="health.refresh()">重新检测</el-button>
  </section>
</template>

<style scoped>
.health {
  max-width: 560px;
  margin: 24px auto;
}
.health__body {
  margin-bottom: 16px;
}
.health__detail {
  margin-bottom: 16px;
}
</style>
