<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import type { ApiError } from '@/types/api'

/**
 * 登录页（P2-16）: 真实调用后端登录 API，禁止任何 mock。
 * 表单校验 / loading / 防重复提交 / 统一错误提示 / 登录成功跳转 redirect 或 dashboard。
 * 实现说明: 校验为同步手动校验并展示在错误区——不依赖 el-form validate() 的异步 Promise
 * （真实 Chrome 环境曾观测到其永久 pending 导致登录不可用，P2-16 修复记录）。
 */
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const submitting = ref(false)
const errorMessage = ref('')

// 会话失效被守卫/拦截器送回登录页时的提示（如 401）
const expiredNotice = route.query.reason === '401' ? '登录状态已失效，请重新登录' : ''

const form = reactive({ username: '', password: '' })

async function handleLogin() {
  if (submitting.value) return
  errorMessage.value = ''
  // 同步手动校验（空白凭证绝不发请求）
  if (!form.username.trim()) {
    errorMessage.value = '请输入用户名'
    return
  }
  if (!form.password.trim()) {
    errorMessage.value = '请输入密码'
    return
  }
  submitting.value = true
  try {
    await auth.login({ ...form })
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/workspace'
    await router.push(redirect)
  } catch (e) {
    const err = e as ApiError
    errorMessage.value = err.message || '登录失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <h1 class="login-title">WorkFlowX</h1>
      <p class="login-subtitle">项目协作与工单管理平台</p>

      <el-alert
        v-if="expiredNotice"
        class="login-notice"
        type="warning"
        :title="expiredNotice"
        show-icon
        :closable="false"
      />
      <el-alert
        v-if="errorMessage"
        class="login-notice"
        type="error"
        :title="errorMessage"
        show-icon
        :closable="false"
      />

      <el-form class="login-form" label-position="top" size="large" @submit.prevent="handleLogin">
        <el-form-item label="用户名">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            :disabled="submitting"
            clearable
          />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            :disabled="submitting"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-button
          class="login-submit"
          type="primary"
          native-type="submit"
          :loading="submitting"
          :disabled="submitting"
        >
          登 录
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  min-height: calc(100vh - 60px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}

.login-card {
  width: 100%;
  max-width: 380px;
}

.login-title {
  margin: 0;
  font-size: 24px;
  text-align: center;
}

.login-subtitle {
  margin: 4px 0 16px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}

.login-notice {
  margin-bottom: 16px;
}

.login-submit {
  width: 100%;
}
</style>
