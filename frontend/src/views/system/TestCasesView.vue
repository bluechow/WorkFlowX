<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createDirectory,
  createTestCase,
  deleteDirectory,
  deleteTestCase,
  listDirectories,
  listTestCases,
  updateDirectory,
  updateTestCase,
  type DirectoryVO,
  type TestCasePriority,
  type TestCaseStatus,
  type TestCaseType,
  type TestCaseVO,
} from '@/api/testcase'
import { useAuthStore } from '@/stores/auth'
import { exportTestCases, importTestCases, type TestCaseImportResult } from '@/api/testcase'
import {
  CASE_PRIORITY_LABELS,
  CASE_STATUS_LABELS,
  CASE_TYPE_LABELS,
  labelOf,
} from '@/utils/labels'

/**
 * 测试用例库（Phase 20）：左侧目录树 + 右侧用例表格。
 * 权限仅 UX；后端 authority + 数据级才是边界。成功后一律重新拉取。
 */
const auth = useAuthStore()
const route = useRoute()
const projectId = computed(() => Number(route.params.projectId))

const dirs = ref<DirectoryVO[]>([])
const cases = ref<TestCaseVO[]>([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const selectedDirId = ref<number | null>(null) // null=全部；0=未分类
const pager = reactive({ page: 1, size: 10, keyword: '', status: undefined as undefined | TestCaseStatus, type: undefined as undefined | TestCaseType, priority: undefined as undefined | TestCasePriority })

const CASE_TYPES: TestCaseType[] = ['FUNCTIONAL', 'REGRESSION', 'SMOKE', 'SECURITY', 'PERFORMANCE']
const PRIORITIES: TestCasePriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
const STATUSES: TestCaseStatus[] = ['DRAFT', 'ACTIVE', 'DEPRECATED']

const dirDialogVisible = ref(false)
const dirEditingId = ref<number | null>(null)
const dirParentId = ref<number | null>(null)
const dirName = ref('')
const dirSaving = ref(false)

const caseDialogVisible = ref(false)
const caseEditingId = ref<number | null>(null)
const caseSaving = ref(false)
const caseForm = reactive({
  title: '',
  preconditions: '',
  steps: '',
  expected: '',
  caseType: 'FUNCTIONAL' as TestCaseType,
  priority: 'MEDIUM' as TestCasePriority,
  status: 'DRAFT' as TestCaseStatus,
  directoryId: null as number | null,
})

interface TreeNode {
  id: number
  label: string
  children: TreeNode[]
}

/** 由平铺目录组装树；孤立父目录（父被删）挂到根 */
const treeData = computed<TreeNode[]>(() => {
  const byId = new Map<number, TreeNode>()
  const roots: TreeNode[] = []
  for (const d of dirs.value) {
    byId.set(d.id, { id: d.id, label: d.name, children: [] })
  }
  for (const d of dirs.value) {
    const node = byId.get(d.id)!
    if (d.parentId && byId.has(d.parentId)) {
      byId.get(d.parentId)!.children.push(node)
    } else {
      roots.push(node)
    }
  }
  return roots
})

async function refreshDirs() {
  dirs.value = await listDirectories(projectId.value)
}

async function refreshCases() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await listTestCases(projectId.value, {
      keyword: pager.keyword || undefined,
      directoryId: selectedDirId.value === null ? undefined : selectedDirId.value,
      status: pager.status,
      caseType: pager.type,
      priority: pager.priority,
      page: pager.page,
      size: pager.size,
    })
    cases.value = result.list
    total.value = result.total
  } catch (e) {
    loadError.value = (e as { message?: string }).message ?? '用例加载失败'
    cases.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function selectDir(id: number | null) {
  selectedDirId.value = id
  pager.page = 1
  refreshCases()
}

function search() {
  pager.page = 1
  refreshCases()
}

// ===== 目录 =====

function openCreateDir(parentId: number | null) {
  dirEditingId.value = null
  dirParentId.value = parentId
  dirName.value = ''
  dirDialogVisible.value = true
}

function openEditDir(node: TreeNode) {
  const dir = dirs.value.find((d) => d.id === node.id)
  dirEditingId.value = node.id
  dirParentId.value = dir?.parentId ?? null
  dirName.value = node.label
  dirDialogVisible.value = true
}

async function submitDir() {
  if (dirSaving.value) return
  if (!dirName.value.trim()) {
    ElMessage.warning('目录名不能为空')
    return
  }
  dirSaving.value = true
  try {
    if (dirEditingId.value === null) {
      await createDirectory(projectId.value, { name: dirName.value.trim(), parentId: dirParentId.value })
      ElMessage.success('目录已创建')
    } else {
      await updateDirectory(projectId.value, dirEditingId.value, {
        name: dirName.value.trim(),
        parentId: dirParentId.value,
      })
      ElMessage.success('目录已更新')
    }
    dirDialogVisible.value = false
    await refreshDirs()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '保存失败')
  } finally {
    dirSaving.value = false
  }
}

async function removeDir(node: TreeNode) {
  try {
    await ElMessageBox.confirm(`确认删除目录「${node.label}」？子目录将一并删除，目录内用例退回未分类。`, '确认操作', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteDirectory(projectId.value, node.id)
    ElMessage.success('目录已删除')
    if (selectedDirId.value === node.id) selectDir(null)
    await refreshDirs()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '删除失败')
  }
}

// ===== 用例 =====

function openCreateCase() {
  caseEditingId.value = null
  caseForm.title = ''
  caseForm.preconditions = ''
  caseForm.steps = ''
  caseForm.expected = ''
  caseForm.caseType = 'FUNCTIONAL'
  caseForm.priority = 'MEDIUM'
  caseForm.status = 'DRAFT'
  caseForm.directoryId = selectedDirId.value && selectedDirId.value > 0 ? selectedDirId.value : null
  caseDialogVisible.value = true
}

function openEditCase(row: TestCaseVO) {
  caseEditingId.value = row.id
  caseForm.title = row.title
  caseForm.preconditions = row.preconditions ?? ''
  caseForm.steps = row.steps ?? ''
  caseForm.expected = row.expected ?? ''
  caseForm.caseType = row.type
  caseForm.priority = row.priority
  caseForm.status = row.status
  caseForm.directoryId = row.directoryId
  caseDialogVisible.value = true
}

async function submitCase() {
  if (caseSaving.value) return
  if (!caseForm.title.trim()) {
    ElMessage.warning('标题不能为空')
    return
  }
  caseSaving.value = true
  try {
    if (caseEditingId.value === null) {
      await createTestCase(projectId.value, {
        title: caseForm.title.trim(),
        preconditions: caseForm.preconditions.trim() || undefined,
        steps: caseForm.steps.trim() || undefined,
        expected: caseForm.expected.trim() || undefined,
        caseType: caseForm.caseType,
        priority: caseForm.priority,
        status: caseForm.status,
        directoryId: caseForm.directoryId,
      })
      ElMessage.success('用例已创建')
    } else {
      await updateTestCase(projectId.value, caseEditingId.value, {
        title: caseForm.title.trim(),
        preconditions: caseForm.preconditions.trim() || undefined,
        steps: caseForm.steps.trim() || undefined,
        expected: caseForm.expected.trim() || undefined,
        caseType: caseForm.caseType,
        priority: caseForm.priority,
        status: caseForm.status,
        directoryId: caseForm.directoryId,
      })
      ElMessage.success('用例已更新')
    }
    caseDialogVisible.value = false
    await refreshCases()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '保存失败')
  } finally {
    caseSaving.value = false
  }
}

async function removeCase(row: TestCaseVO) {
  try {
    await ElMessageBox.confirm(`确认删除用例「${row.title}」？`, '确认操作', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteTestCase(projectId.value, row.id)
    ElMessage.success('用例已删除')
    await refreshCases()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '删除失败')
  }
}

// ===== Excel 导入导出（Phase A-⑦）=====
const importing = ref(false)
const importInput = ref<HTMLInputElement | null>(null)
const importResult = ref<TestCaseImportResult | null>(null)
const importDialogVisible = ref(false)

async function doExport() {
  try {
    await exportTestCases(projectId.value, '用例库')
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '导出失败')
  }
}

function pickImportFile() {
  importInput.value?.click()
}

async function onImportFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  importing.value = true
  try {
    importResult.value = await importTestCases(projectId.value, file)
    importDialogVisible.value = true
    await refreshDirs()
    await refreshCases()
  } catch (e) {
    ElMessage.error((e as { message?: string }).message ?? '导入失败（请使用导出的模板格式）')
  } finally {
    importing.value = false
  }
}

onMounted(async () => {
  try {
    await refreshDirs()
  } catch (e) {
    loadError.value = (e as { message?: string }).message ?? '目录加载失败'
  }
  await refreshCases()
})
</script>

<template>
  <section class="testcases-view">
    <div class="testcases-view__toolbar">
      <h2>测试用例库</h2>
      <div class="testcases-view__actions">
        <el-button @click="doExport">导出 Excel</el-button>
        <el-button
          v-if="auth.hasPermission('testcase:create')"
          :loading="importing"
          @click="pickImportFile"
        >
          导入 Excel
        </el-button>
        <el-button
          v-if="auth.hasPermission('testcase:create')"
          type="primary"
          @click="openCreateCase"
        >
          新建用例
        </el-button>
        <input
          ref="importInput"
          type="file"
          accept=".xlsx"
          style="display: none"
          @change="onImportFile"
        />
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" />

    <div v-else class="testcases-view__body">
      <aside class="testcases-view__dirs">
        <div class="testcases-view__dirs-head">
          <span>目录</span>
          <el-button
            v-if="auth.hasPermission('testcase:create')"
            link
            type="primary"
            size="small"
            @click="openCreateDir(null)"
          >
            新建根目录
          </el-button>
        </div>
        <el-button
          link
          :type="selectedDirId === null ? 'primary' : 'default'"
          class="testcases-view__all"
          @click="selectDir(null)"
        >
          全部用例
        </el-button>
        <el-button
          link
          :type="selectedDirId === 0 ? 'primary' : 'default'"
          class="testcases-view__all"
          @click="selectDir(0)"
        >
          未分类
        </el-button>
        <el-tree
          v-if="treeData.length"
          :data="treeData"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
        >
          <template #default="{ data }">
            <span class="testcases-view__node">
              <span
                :class="{ 'testcases-view__node--active': selectedDirId === data.id }"
                @click="selectDir(data.id)"
              >
                {{ data.label }}
              </span>
              <span v-if="auth.hasPermission('testcase:create')" class="testcases-view__node-ops">
                <el-button link size="small" @click.stop="openCreateDir(data.id)">+</el-button>
                <el-button
                  v-if="auth.hasPermission('testcase:update')"
                  link
                  size="small"
                  @click.stop="openEditDir(data)"
                >
                  编辑
                </el-button>
                <el-button
                  v-if="auth.hasPermission('testcase:delete')"
                  link
                  type="danger"
                  size="small"
                  @click.stop="removeDir(data)"
                >
                  删除
                </el-button>
              </span>
            </span>
          </template>
        </el-tree>
        <el-empty v-else description="暂无目录" :image-size="50" />
      </aside>

      <div class="testcases-view__main">
        <div class="testcases-view__filters">
          <el-input
            v-model="pager.keyword"
            placeholder="搜索标题"
            clearable
            style="width: 200px"
            @keyup.enter="search"
          />
          <el-select v-model="pager.status" clearable placeholder="状态" style="width: 120px" @change="search">
            <el-option v-for="s in STATUSES" :key="s" :label="s" :value="s" />
          </el-select>
          <el-select v-model="pager.type" clearable placeholder="类型" style="width: 130px" @change="search">
            <el-option v-for="t in CASE_TYPES" :key="t" :label="labelOf(CASE_TYPE_LABELS, t)" :value="t" />
          </el-select>
          <el-select v-model="pager.priority" clearable placeholder="优先级" style="width: 120px" @change="search">
            <el-option v-for="p in PRIORITIES" :key="p" :label="labelOf(CASE_PRIORITY_LABELS, p)" :value="p" />
          </el-select>
          <el-button @click="search">搜索</el-button>
        </div>

        <el-table v-loading="loading" :data="cases" border empty-text="暂无用例">
          <el-table-column label="编号" width="110">
            <template #default="{ row }">TC-{{ row.testcaseNo }}</template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ labelOf(CASE_TYPE_LABELS, row.type) }}</template>
          </el-table-column>
          <el-table-column label="优先级" width="90">
            <template #default="{ row }">{{ labelOf(CASE_PRIORITY_LABELS, row.priority) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : row.status === 'DRAFT' ? 'info' : 'warning'" size="small">
                {{ labelOf(CASE_STATUS_LABELS, row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="auth.hasPermission('testcase:update')"
                link
                type="primary"
                size="small"
                @click="openEditCase(row)"
              >
                编辑
              </el-button>
              <el-button
                v-if="auth.hasPermission('testcase:delete')"
                link
                type="danger"
                size="small"
                @click="removeCase(row)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-if="total > pager.size"
          class="testcases-view__pager"
          layout="prev, pager, next, total"
          :total="total"
          :page-size="pager.size"
          :current-page="pager.page"
          @current-change="(p: number) => { pager.page = p; refreshCases() }"
        />
      </div>
    </div>

    <el-dialog v-model="dirDialogVisible" :title="dirEditingId === null ? '新建目录' : '编辑目录'" width="440px">
      <el-form label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="dirName" placeholder="目录名" />
        </el-form-item>
        <el-form-item label="父目录">
          <el-select v-model="dirParentId" clearable placeholder="根目录" style="width: 100%">
            <el-option
              v-for="d in dirs.filter((x) => x.id !== dirEditingId)"
              :key="d.id"
              :label="d.name"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dirDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dirSaving" :disabled="dirSaving" @click="submitDir">
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="caseDialogVisible" :title="caseEditingId === null ? '新建用例' : '编辑用例'" width="620px">
      <el-form label-width="90px">
        <el-form-item label="标题">
          <el-input v-model="caseForm.title" placeholder="用例标题" />
        </el-form-item>
        <el-form-item label="前置条件">
          <el-input v-model="caseForm.preconditions" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="测试步骤">
          <el-input v-model="caseForm.steps" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="预期结果">
          <el-input v-model="caseForm.expected" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="caseForm.caseType" style="width: 100%">
            <el-option v-for="t in CASE_TYPES" :key="t" :label="labelOf(CASE_TYPE_LABELS, t)" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="caseForm.priority" style="width: 100%">
            <el-option v-for="p in PRIORITIES" :key="p" :label="labelOf(CASE_PRIORITY_LABELS, p)" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="caseForm.status" style="width: 100%">
            <el-option v-for="st in STATUSES" :key="st" :label="st" :value="st" />
          </el-select>
        </el-form-item>
        <el-form-item label="目录">
          <el-select v-model="caseForm.directoryId" clearable placeholder="未分类" style="width: 100%">
            <el-option v-for="d in dirs" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="caseDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="caseSaving" :disabled="caseSaving" @click="submitCase">
          保存
        </el-button>
      </template>
    </el-dialog>
    <!-- 导入结果：行级失败逐行展示（Phase A-⑦） -->
    <el-dialog v-model="importDialogVisible" title="导入结果" width="520px">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="总行数">{{ importResult?.totalRows ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="成功">{{ importResult?.successCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="失败">{{ importResult?.failureCount ?? 0 }}</el-descriptions-item>
      </el-descriptions>
      <template v-if="importResult && importResult.failures.length">
        <h4 class="testcases-view__fail-title">失败明细</h4>
        <div v-for="f in importResult.failures" :key="f.rowNumber" class="testcases-view__fail-row">
          第 {{ f.rowNumber }} 行：{{ f.message }}
        </div>
      </template>
      <template #footer>
        <el-button type="primary" @click="importDialogVisible = false">知道了</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.testcases-view {
  max-width: 1200px;
  margin: 16px auto;
}
.testcases-view__actions {
  display: flex;
  gap: 8px;
}
.testcases-view__fail-title {
  margin: 12px 0 6px;
  font-size: 13px;
}
.testcases-view__fail-row {
  color: #f56c6c;
  font-size: 12px;
  padding: 3px 0;
}
.testcases-view__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.testcases-view__body {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.testcases-view__dirs {
  width: 280px;
  flex-shrink: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 8px;
}
.testcases-view__dirs-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
  font-weight: 600;
}
.testcases-view__all {
  display: block;
  margin: 2px 0;
  text-align: left;
  width: 100%;
}
.testcases-view__node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 4px;
}
.testcases-view__node--active {
  color: var(--el-color-primary);
  font-weight: 600;
}
.testcases-view__node-ops {
  visibility: hidden;
}
.testcases-view__node:hover .testcases-view__node-ops {
  visibility: visible;
}
.testcases-view__main {
  flex: 1;
  min-width: 0;
}
.testcases-view__filters {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.testcases-view__pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
