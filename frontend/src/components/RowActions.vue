<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'

/**
 * 表格操作列统一组件（ui-conventions.md §3）。
 *
 * - groups：按类别分组（导航 → 编辑 → 状态管理 → 危险），组间自动渲染竖线分隔；
 * - 权限：action.permission 缺失或无此权限的按钮不渲染；整组不可见时分隔线自动消失；
 * - 语义色：primary=导航/编辑（默认）、warning=可逆管理、danger=危险；字号统一 small。
 */
export interface RowActionItem {
  label: string
  onClick: () => void
  type?: 'primary' | 'warning' | 'danger'
  /** 需要的权限码；不传=始终显示 */
  permission?: string
  disabled?: boolean
  /** 行级可见性（如"仅 ACTIVE 行显示禁用"）；true=隐藏 */
  hidden?: boolean
}

const props = defineProps<{ groups: RowActionItem[][] }>()

const auth = useAuthStore()

const visibleGroups = computed(() =>
  props.groups
    .map((group) =>
      group.filter((a) => !a.hidden && (!a.permission || auth.hasPermission(a.permission))),
    )
    .filter((group) => group.length > 0),
)
</script>

<template>
  <div class="row-actions">
    <template v-for="(group, gi) in visibleGroups" :key="gi">
      <span v-if="gi > 0" class="row-actions__divider" />
      <el-button
        v-for="action in group"
        :key="action.label"
        link
        size="small"
        :type="action.type ?? 'primary'"
        :disabled="action.disabled"
        class="row-actions__btn"
        @click="action.onClick"
      >
        {{ action.label }}
      </el-button>
    </template>
  </div>
</template>

<style scoped>
.row-actions {
  display: inline-flex;
  align-items: center;
  white-space: nowrap;
}
.row-actions__divider {
  flex: none;
  width: 1px;
  height: 12px;
  background: #dcdfe6;
  margin: 0 4px;
}
.row-actions :deep(.el-button + .el-button) {
  margin-left: 10px;
}
</style>
