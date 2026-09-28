<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createTestPlan, deleteTestPlan, listTestPlans, type TestPlanVO } from '@/api/testplan'
import { useAuthStore } from '@/stores/auth'
import RowActions from '@/components/RowActions.vue'
import { PLAN_STATUS_LABELS, labelOf } from '@/utils/labels'

/**
 * 测试计划列表（Phase 21 / V1.1）。权限仅 UX；后端 authority + 数据级才是边界。
 * 成功后一律重新拉取；删除/新建带确认与防重复。
 */
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const projectId = computed(() => Number(route.params.projectId))

const plans = ref<TestPlanVO[]>([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const pager = reactive({ page: 1, size: 10 })

const dialogVisible = ref(false)
const planName = ref('')
const submitting = ref(false)

const STATUS_TAG: Record<string, 'info' | 'warning' | 'success'> = {
  NOT_STARTED: 'info',
  RUNNING: 'warning',
  COMPLETED: 'success',
}

async function refresh() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await listTestPlans(projectId.value, pager.page, pager.size)
    plans.value = result.list
    total.value = result.total
  } catch (e) {
    loadError.value = (e as { message?: string }).message ?? '计划加载失败'
    plans.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function openCreate() {
  planName.value = ''
  dialogVisible.value = true
}

async function submitCreate() {
  if (submitting.value) return
  if (!planName.value.trim()) {
    ElMessage.warning('计划名称不能为空')
    return
  }
  submitting.value = true
  try {
    const created = await createTestPlan(projectId.value, planName.value.trim())
    ElMessage.success('测试计划已创建')
    dialogVisible.value = false
    router.push(`/system/projects/${projectId.value}/testplans/${created.id}`)
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '创建失败')
  } finally {
    submitting.value = false
  }
}

async function remove(plan: TestPlanVO) {
  try {
    await ElMessageBox.confirm(`确认删除测试计划「${plan.name}」？计划内执行记录将一并删除。`, '确认操作', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteTestPlan(projectId.value, plan.id)
    ElMessage.success('计划已删除')
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '删除失败')
  }
}


onMounted(refresh)
</script>

<template>
  <section class="testplans-view">
    <div class="testplans-view__toolbar">
      <h2>测试计划</h2>
      <el-button
        v-if="auth.hasPermission('testplan:create')"
        type="primary"
        @click="openCreate"
      >
        新建计划
      </el-button>
    </div>

    <el-alert
      v-if="!auth.hasPermission('testplan:list')"
      type="warning"
      title="您没有查看测试计划的权限（testplan:list）——请联系管理员"
      :closable="false"
      show-icon
      data-test="no-permission"
    />

    <template v-else>
      <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" />
      <el-table v-else v-loading="loading" :data="plans" border empty-text="暂无测试计划">
        <el-table-column prop="name" label="计划名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="STATUS_TAG[row.status] ?? 'info'" size="small">{{ labelOf(PLAN_STATUS_LABELS, row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="用例数" width="90" prop="total" />
        <el-table-column label="通过" width="80" prop="passed" />
        <el-table-column label="失败" width="80" prop="failed" />
        <el-table-column label="阻塞" width="80" prop="blocked" />
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <RowActions
              :groups="[
                [
                  { label: '执行', onClick: () => router.push(`/system/projects/${projectId}/testplans/${row.id}`) },
                  { label: '报告', onClick: () => router.push(`/system/projects/${projectId}/testplans/${row.id}/report`) },
                ],
                [{ label: '删除', permission: 'testplan:delete', type: 'danger', onClick: () => remove(row) }],
              ]"
            />
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > pager.size"
        class="testplans-view__pager"
        layout="prev, pager, next, total"
        :total="total"
        :page-size="pager.size"
        :current-page="pager.page"
        @current-change="(p: number) => { pager.page = p; refresh() }"
      />
    </template>

    <el-dialog v-model="dialogVisible" title="新建测试计划" width="440px">
      <el-form label-width="90px">
        <el-form-item label="计划名称">
          <el-input v-model="planName" placeholder="如：登录模块回归 V1" @keyup.enter="submitCreate" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="submitting" @click="submitCreate">
          创建并进入
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.testplans-view {
  max-width: 1100px;
  margin: 16px auto;
}
.testplans-view__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.testplans-view__pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
