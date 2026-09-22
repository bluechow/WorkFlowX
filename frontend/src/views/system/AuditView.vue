<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listAuditLogs, type AuditLogVO } from '@/api/audit'
import { useAuthStore } from '@/stores/auth'

/**
 * 审计日志页面（P10-13）。audit:list authority 后端强制；无权限时展示明确提示（非空白页）。
 * 筛选/时间范围/分页全部下推后端；数据为全系统高价值操作事实。
 */
const auth = useAuthStore()

const rows = ref<AuditLogVO[]>([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const filters = reactive({
  module: undefined as undefined | string,
  action: undefined as undefined | string,
  success: undefined as undefined | boolean,
  operator: undefined as undefined | number,
  beginTime: undefined as undefined | string,
  endTime: undefined as undefined | string,
})
const pager = reactive({ page: 1, size: 20 })

const MODULES = ['AUTH', 'USER', 'ORG', 'PROJECT', 'ISSUE', 'COMMENT', 'ATTACHMENT']
const ACTIONS = [
  'LOGIN', 'LOGIN_FAIL', 'LOGOUT', 'CREATE', 'UPDATE', 'DELETE',
  'STATUS', 'TRANSITION', 'ASSIGN_MEMBER', 'UPLOAD',
]

async function refresh() {
  loading.value = true
  loadError.value = ''
  try {
    const page = await listAuditLogs({
      module: filters.module,
      action: filters.action,
      success: filters.success,
      operator: filters.operator,
      beginTime: filters.beginTime,
      endTime: filters.endTime,
      page: pager.page,
      size: pager.size,
    })
    rows.value = page.list
    total.value = page.total
  } catch (e) {
    loadError.value = (e as { message?: string }).message ?? '审计日志加载失败'
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  pager.page = 1
  refresh()
}

function showDetail(row: AuditLogVO) {
  ElMessage.info({
    message: `#${row.id} ${row.module}/${row.action} ${row.target ?? ''} traceId=${row.traceId ?? '-'}`
      + `\n${row.uri} ${row.ip}\n${row.summary ?? ''}`,
    duration: 6000,
    showClose: true,
  })
}

onMounted(() => {
  if (auth.hasPermission('audit:list')) {
    refresh()
  }
})
</script>

<template>
  <section class="audit-view">
    <div class="audit-view__toolbar">
      <h2>审计日志</h2>
    </div>

    <el-alert
      v-if="!auth.hasPermission('audit:list')"
      type="warning"
      :title="'您没有查看审计日志的权限（audit:list）——请联系管理员'"
      :closable="false"
      show-icon
      data-test="no-permission"
    />

    <template v-else>
      <div class="audit-view__filters">
        <el-select v-model="filters.module" clearable placeholder="模块" style="width: 130px" @change="search">
          <el-option v-for="m in MODULES" :key="m" :label="m" :value="m" />
        </el-select>
        <el-select v-model="filters.action" clearable placeholder="操作" style="width: 150px" @change="search">
          <el-option v-for="a in ACTIONS" :key="a" :label="a" :value="a" />
        </el-select>
        <el-select v-model="filters.success" clearable placeholder="结果" style="width: 110px" @change="search">
          <el-option label="成功" :value="true" />
          <el-option label="失败" :value="false" />
        </el-select>
        <el-date-picker
          v-model="filters.beginTime"
          type="datetime"
          placeholder="开始时间"
          style="width: 180px"
        />
        <el-date-picker
          v-model="filters.endTime"
          type="datetime"
          placeholder="结束时间"
          style="width: 180px"
        />
        <el-button @click="search">查询</el-button>
      </div>

      <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" />
      <el-table
        v-else
        v-loading="loading"
        :data="rows"
        border
        empty-text="暂无审计记录"
        data-test="audit-table"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="操作者" width="90">
          <template #default="{ row }">{{ row.userId ? `#${row.userId}` : '匿名' }}</template>
        </el-table-column>
        <el-table-column prop="module" label="模块" width="110" />
        <el-table-column prop="action" label="操作" width="120" />
        <el-table-column prop="target" label="目标" width="140" show-overflow-tooltip />
        <el-table-column prop="summary" label="摘要" min-width="200" show-overflow-tooltip />
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" size="small">
              {{ row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="traceId" label="traceId" width="140" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column label="详情" width="80">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="showDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > pager.size"
        class="audit-view__pager"
        layout="prev, pager, next, total"
        :total="total"
        :page-size="pager.size"
        :current-page="pager.page"
        @current-change="(p: number) => { pager.page = p; refresh() }"
      />
    </template>
  </section>
</template>

<style scoped>
.audit-view {
  max-width: 1200px;
  margin: 16px auto;
}
.audit-view__toolbar {
  margin-bottom: 12px;
}
.audit-view__filters {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.audit-view__pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
