import { defineStore } from 'pinia'
import { getUnreadCount } from '../api/notification'
import { useUserStore } from './user'

interface NotificationState {
  unreadCount: number
  loaded: boolean
}

/**
 * 通知未读数共享状态：铃铛（NotificationBell）与通知列表（NotificationListView）
 * 共用，标记已读后统一刷新，保证 Badge 与实际未读一致。
 */
export const useNotificationStore = defineStore('notification', {
  state: (): NotificationState => ({
    unreadCount: 0,
    loaded: false
  }),

  getters: {
    hasUnread: (s) => s.unreadCount > 0
  },

  actions: {
    /** 拉取最新未读数（无 notification:view 权限时静默归零）。 */
    async refresh() {
      const user = useUserStore()
      if (!user.hasPermission('notification:view')) {
        this.unreadCount = 0
        this.loaded = true
        return
      }
      try {
        this.unreadCount = await getUnreadCount()
      } catch {
        this.unreadCount = 0
      }
      this.loaded = true
    },

    /** 直接设置未读数（列表标记已读后本地兜底）。 */
    setUnread(n: number) {
      this.unreadCount = n < 0 ? 0 : n
    }
  }
})
