import { defineStore } from 'pinia'
import { getTenants } from '../api/auth'
import type { TenantBrief } from '../types'

export const useTenantStore = defineStore('tenant', {
  state: () => ({
    tenants: [] as TenantBrief[],
    currentTenantId: null as string | null
  }),
  getters: {
    // 平台管理员无租户上下文，返回空数组（由后端保证）
    switchableTenants: (s) => s.tenants
  },
  actions: {
    async fetchTenants() {
      this.tenants = await getTenants()
    },
    setCurrent(id: string | null) {
      this.currentTenantId = id
    }
  }
})
