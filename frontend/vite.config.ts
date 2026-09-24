import { fileURLToPath, URL } from 'node:url'
import { configDefaults, defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // 开发环境代理：规避 CORS，前端统一走同源 /api（docs/api/api-conventions.md）
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    // P18: e2e/*.spec.ts 属 Playwright（由 playwright.config.ts 管理），不作为 Vitest 收集
    exclude: [...configDefaults.exclude, 'e2e/**'],
  },
})
