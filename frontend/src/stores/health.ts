import { defineStore } from 'pinia'
import { fetchHealth } from '@/api/health'
import type { ApiError, HealthInfo } from '@/types/api'

export type HealthState = 'loading' | 'success' | 'error'

interface HealthStoreState {
  state: HealthState
  info: HealthInfo | null
  error: ApiError | null
}

/** 健康状态 store（任务 1-6.3 Pinia 示例 + 1-6.6 三态承载） */
export const useHealthStore = defineStore('health', {
  state: (): HealthStoreState => ({
    state: 'loading',
    info: null,
    error: null,
  }),
  actions: {
    async refresh() {
      this.state = 'loading'
      this.error = null
      try {
        this.info = await fetchHealth()
        this.state = 'success'
      } catch (e) {
        this.error = e as ApiError
        this.state = 'error'
      }
    },
  },
})
