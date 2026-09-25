<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addPlanItems,
  executeItem,
  getTestPlan,
  listPlanItems,
  removePlanItem,
  type ItemResult,
  type TestPlanItemVO,
  type TestPlanVO,
} from '@/api/testplan'
import { listTestCases, type TestCaseVO } from '@/api/testcase'
import { useAuthStore } from '@/stores/auth'

/**
 * 测试计划执行页（Phase 21 / V1.1）：条目逐条打结果 + 进度统计 + FAIL 关联 Bug。
 * 权限仅 UX；后端 authority + 数据级才是边界。COMPLETED 计划禁止操作。
 */
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const projectId = computed(() => Number(route.params.projectId))
const planId = computed(() => Number(route.params.planId))

const plan = ref<TestPlanVO | null>(null)
const items = ref<TestPlanItemVO[]>([])
const loading = ref(false)
const loadError = ref('')
const executing = ref(0)
const addDialogVisible = ref(false)
const adding = ref(false)

const isLocked = computed(() => plan.value?.status === 'COMPLETED')
const progressPct = computed(() => {
  const p = plan.value
  if (!p || p.total === 0) return 0
  return Math.round(((p.passed + p.failed + p.blocked) / p.total) * 100)
})

async function refresh() {
  loading.value = true
  loadError.value = ''
  try {
    plan.value = await getTestPlan(projectId.value, planId.value)
    items.value = await listPlanItems(projectId.value, planId.value)
  } catch (e) {
    loadError.value = (e as { message?: string }).message ?? '计划加载失败'
  } finally {
    loading.value = false
  }
}

async function onExecute(item: TestPlanItemVO, result: ItemResult) {
  if (executing.value) return
  let note: string | undefined
  let issueId: number | null | undefined
  if (result === 'FAIL') {
    // 简易输入: 用 ElMessageBox prompt 填备注与 Bug ID
    try {
      const { value } = await ElMessageBox.prompt(
        '请输入失败备注（可留空），并可填写要关联的 Issue ID',
        `标记 FAIL - ${item.caseTitle ?? ''}`,
        {
          confirmButtonText: '确认',
          cancelButtonText: '取消',
          inputPlaceholder: '失败备注',
        },
      )
      note = value || undefined
      const match = value?.match(/\d+/)
      issueId = match ? Number(match[0]) : undefined
    } catch {
      return
    }
  } else if (result === 'BLOCKED') {
    try {
      const { value } = await ElMessageBox.prompt('请输入阻塞原因', `标记 BLOCKED`, {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        inputPlaceholder: '阻塞原因',
      })
      note = value || undefined
    } catch {
      return
    }
  }
  executing.value = item.id
  try {
    await executeItem(projectId.value, planId.value, item.id, { result, note, issueId })
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '执行失败')
  } finally {
    executing.value = 0
  }
}

async function onRemoveItem(item: TestPlanItemVO) {
  try {
    await ElMessageBox.confirm('确认从计划中移除该条目？', '确认操作', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await removePlanItem(projectId.value, planId.value, item.id)
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '移除失败')
  }
}

// 添加用例
const selectedCaseIds = ref<number[]>([])
const libraryCases = ref<TestCaseVO[]>([])
const libraryLoading = ref(false)

async function openAdd() {
  addDialogVisible.value = true
  libraryLoading.value = true
  try {
    const result = await listTestCases(projectId.value, { page: 1, size: 100 })
    const inPlan = new Set(items.value.map((i) => i.caseId))
    libraryCases.value = result.list.filter((c) => !inPlan.has(c.id))
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '加载用例库失败')
  } finally {
    libraryLoading.value = false
  }
}

async function submitAdd() {
  if (adding.value || selectedCaseIds.value.length === 0) return
  adding.value = true
  try {
    const added = await addPlanItems(projectId.value, planId.value, selectedCaseIds.value)
    ElMessage.success(`已添加 ${added} 条用例`)
    addDialogVisible.value = false
    await refresh()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '添加失败')
  } finally {
    adding.value = false
  }
}

onMounted(refresh)
</script>

<template>
  <section class="plan-detail-view">
    <div class="plan-detail-view__toolbar">
      <h2>{{ plan?.name ?? '测试计划' }}</h2>
      <el-button @click="router.push(`/system/projects/${projectId}/testplans`)">返回列表</el-button>
    </div>

    <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" />

    <template v-else>
      <el-descriptions v-if="plan" :column="4" border class="plan-detail-view__stats">
        <el-descriptions-item label="状态">{{ plan.status }}</el-descriptions-item>
        <el-descriptions-item label="总用例">{{ plan.total }}</el-descriptions-item>
        <el-descriptions-item label="通过">{{ plan.passed }}</el-descriptions-item>
        <el-descriptions-item label="失败">{{ plan.failed }}</el-descriptions-item>
        <el-descriptions-item label="阻塞">{{ plan.blocked }}</el-descriptions-item>
        <el-descriptions-item label="待执行">{{ plan.pending }}</el-descriptions-item>
        <el-descriptions-item label="进度" :span="2">
          <el-progress :percentage="progressPct" :stroke-width="14" />
        </el-descriptions-item>
      </el-descriptions>

      <div class="plan-detail-view__ops">
        <el-button
          v-if="auth.hasPermission('testplan:update') && !isLocked"
          type="primary"
          size="small"
          @click="openAdd"
        >
          添加用例
        </el-button>
        <el-tag v-if="isLocked" type="info" size="small">计划已完成，只读</el-tag>
      </div>

      <el-table v-loading="loading" :data="items" border empty-text="暂无条目，请先添加用例">
        <el-table-column label="编号" width="90">
          <template #default="{ row }">TC-{{ row.testcaseNo ?? '?' }}</template>
        </el-table-column>
        <el-table-column prop="caseTitle" label="用例标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="casePriority" label="优先级" width="100" />
        <el-table-column label="执行结果" width="260">
          <template #default="{ row }">
            <span class="plan-detail-view__result">
              <el-tag
                :type="row.result === 'PASS' ? 'success' : row.result === 'FAIL' ? 'danger' : row.result === 'BLOCKED' ? 'warning' : 'info'"
                size="small"
              >
                {{ row.result }}
              </el-tag>
              <template v-if="!isLocked && auth.hasPermission('testplan:update')">
                <el-button
                  link
                  type="success"
                  size="small"
                  :disabled="executing === row.id"
                  @click="onExecute(row, 'PASS')"
                >
                  PASS
                </el-button>
                <el-button
                  link
                  type="danger"
                  size="small"
                  :disabled="executing === row.id"
                  @click="onExecute(row, 'FAIL')"
                >
                  FAIL
                </el-button>
                <el-button
                  link
                  type="warning"
                  size="small"
                  :disabled="executing === row.id"
                  @click="onExecute(row, 'BLOCKED')"
                >
                  BLOCKED
                </el-button>
              </template>
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="note" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="关联 Bug" width="100">
          <template #default="{ row }">{{ row.issueId ? `#${row.issueId}` : '—' }}</template>
        </el-table-column>
        <el-table-column prop="executedBy" label="执行人" width="90">
          <template #default="{ row }">{{ row.executedBy ? `#${row.executedBy}` : '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="auth.hasPermission('testplan:update') && !isLocked"
              link
              type="danger"
              size="small"
              @click="onRemoveItem(row)"
            >
              移除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-dialog v-model="addDialogVisible" title="从用例库添加" width="520px">
        <div v-loading="libraryLoading" class="plan-detail-view__library">
          <el-checkbox-group v-model="selectedCaseIds">
            <el-checkbox
              v-for="c in libraryCases"
              :key="c.id"
              :value="c.id"
              class="plan-detail-view__lib-item"
            >
              TC-{{ c.testcaseNo }} {{ c.title }}
            </el-checkbox>
            <el-empty v-if="libraryCases.length === 0" description="用例库中所有用例已在本计划内" :image-size="50" />
          </el-checkbox-group>
        </div>
        <template #footer>
          <el-button @click="addDialogVisible = false">取消</el-button>
          <el-button
            type="primary"
            :loading="adding"
            :disabled="adding || selectedCaseIds.length === 0"
            @click="submitAdd"
          >
            添加（{{ selectedCaseIds.length }}）
          </el-button>
        </template>
      </el-dialog>
    </template>
  </section>
</template>

<style scoped>
.plan-detail-view {
  max-width: 1200px;
  margin: 16px auto;
}
.plan-detail-view__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.plan-detail-view__stats {
  margin-bottom: 12px;
}
.plan-detail-view__ops {
  margin-bottom: 8px;
  display: flex;
  gap: 8px;
  align-items: center;
}
.plan-detail-view__result {
  display: flex;
  align-items: center;
  gap: 4px;
}
.plan-detail-view__library {
  max-height: 320px;
  overflow-y: auto;
}
.plan-detail-view__lib-item {
  display: block;
  margin: 0 0 4px;
}
</style>
