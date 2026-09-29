<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listActivities, type ActivityVO } from '@/api/activity'
import { ACTIVITY_ACTION_LABELS, ACTIVITY_ACTION_TAG_TYPES } from '@/api/activity'

/**
 * 项目动态（Final Edition FP-2）：真实业务事件流（创建/分派/流转/评论/附件/关联）。
 * 数据全部来自后端 activities 表（服务层事务内写入），无前端伪造。
 */
const route = useRoute()
const projectId = computed(() => Number(route.params.projectId))

const activities = ref<ActivityVO[]>([])
const loading = ref(false)

async function refresh() {
  loading.value = true
  try {
    activities.value = await listActivities(projectId.value, 100)
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '加载动态失败')
  } finally {
    loading.value = false
  }
}

function fmt(t: string): string {
  return t ? t.replace('T', ' ').slice(0, 16) : ''
}

onMounted(refresh)
</script>

<template>
  <div v-loading="loading" class="pa">
    <el-empty v-if="!loading && activities.length === 0" description="暂无动态" :image-size="60" />
    <el-timeline v-else>
      <el-timeline-item
        v-for="a in activities"
        :key="a.id"
        :timestamp="fmt(a.createdAt)"
        placement="top"
        :type="a.action === 'CREATE' ? 'primary' : a.action === 'TRANSITION' ? 'warning' : undefined"
      >
        <div class="pa__item">
          <el-tag size="small" :type="(ACTIVITY_ACTION_TAG_TYPES[a.action] as never) || 'info'">
            {{ ACTIVITY_ACTION_LABELS[a.action] ?? a.action }}
          </el-tag>
          <span class="pa__actor">#{{ a.actorId }}</span>
          <span class="pa__summary">{{ a.summary }}</span>
        </div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<style scoped>
.pa {
  background: #fff;
  border-radius: 8px;
  padding: 20px 24px;
  max-height: calc(100vh - 220px);
  overflow-y: auto;
}
.pa__item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pa__actor {
  color: #409eff;
  font-size: 13px;
  font-weight: 600;
}
.pa__summary {
  font-size: 13px;
  color: #303133;
}
</style>
