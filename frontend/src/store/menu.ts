import { defineStore } from 'pinia'
import { getMenu } from '../api/auth'
import type { MenuNode } from '../types'

export const useMenuStore = defineStore('menu', {
  state: () => ({
    menu: [] as MenuNode[]
  }),
  getters: {
    // 平铺用于路径匹配（当前为两级，直接取顶层即可）
    menuList: (s) => s.menu
  },
  actions: {
    async fetchMenu() {
      this.menu = await getMenu()
    }
  }
})
