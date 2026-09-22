<script setup lang="ts">
import { useRouter } from 'vue-router'

/** 404 页面（P11-03）：未知路由统一兜底，不出现空白页。 */
const router = useRouter()
</script>

<template>
  <section class="not-found">
    <el-result icon="warning" title="404" sub-title="页面不存在或已被移动">
      <template #extra>
        <el-button type="primary" @click="router.push('/dashboard')">返回首页</el-button>
        <el-button @click="router.back()">返回上一页</el-button>
      </template>
    </el-result>
  </section>
</template>

<style scoped>
.not-found {
  margin-top: 60px;
}
</style>
