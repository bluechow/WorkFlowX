<script setup lang="ts"></script>

<template>
  <el-container class="app">
    <el-header class="app__header">
      <span class="app__brand">WorkFlowX</span>
      <span class="app__subtitle">项目协作与工单管理平台</span>
    </el-header>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.app__header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  border-bottom: 1px solid #ebeef5;
}
.app__brand {
  font-size: 20px;
  font-weight: 600;
}
.app__subtitle {
  color: #909399;
  font-size: 13px;
}
</style>
