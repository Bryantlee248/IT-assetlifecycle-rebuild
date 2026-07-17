import { defineStore } from 'pinia'
import { getMenu } from '../api/auth'
import type { MenuNode } from '../types'
import { useUserStore } from './user'
import { useNotificationStore } from './notification'

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
      const list = await getMenu()
      const user = useUserStore()
      // MVP-3：V9 未补登菜单行（无 menu 表），前端以路由为入口追加两个模块入口，按权限显隐。
      const extra: MenuNode[] = []
      if (user.hasPermission('approval:view')) {
        extra.push({
          key: 'mvp3-approval',
          title: '审批待办',
          path: '/approval/tasks',
          icon: 'Tickets',
          children: []
        })
      }
      if (user.hasPermission('notification:view')) {
        extra.push({
          key: 'mvp3-notification',
          title: '通知中心',
          path: '/notification/list',
          icon: 'Bell',
          children: []
        })
      }
      this.menu = [...list, ...extra]

      // 拉取未读通知数，驱动顶部铃铛 Badge
      await useNotificationStore().refresh()
    }
  }
})
