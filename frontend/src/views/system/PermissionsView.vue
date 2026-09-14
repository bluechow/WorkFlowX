<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listPermissions } from '@/api/rbac'
import type { PermissionVO } from '@/types/api'

/** 权限管理（P3-04）：只读列表——权限由 V3 迁移与业务模块登记产生，不提供随意创建入口。 */
const permissions = ref<PermissionVO[]>([])
const loading = ref(false)

async function refresh() {
  loading.value = true
  try {
    permissions.value = await listPermissions()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '加载权限失败')
  } finally {
    loading.value = false
  }
}

onMounted(refresh)
</script>

<template>
  <section class="permissions-view">
    <h2>权限管理</h2>
    <el-table v-loading="loading" :data="permissions" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="code" label="编码" min-width="180" />
      <el-table-column prop="name" label="名称" min-width="120" />
      <el-table-column prop="type" label="类型" width="90" />
      <el-table-column label="系统权限" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.system" type="warning" size="small">系统</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
    </el-table>
  </section>
</template>

<style scoped>
.permissions-view {
  max-width: 960px;
  margin: 16px auto;
}
h2 {
  margin-bottom: 12px;
}
</style>
