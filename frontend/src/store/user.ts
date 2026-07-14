import { defineStore } from 'pinia'
import * as authApi from '../api/auth'
import { clearTokens, setTokens } from '../utils/request'
import type { LoginResponse, TokenResponse, UserType } from '../types'
import { useMenuStore } from './menu'
import { useTenantStore } from './tenant'

interface UserState {
  accessToken: string
  refreshToken: string
  userId: string
  username: string
  displayName: string
  userType: UserType | ''
  mustChangePassword: boolean
  tenantId: string | null
  permissions: string[]
  sessionLoaded: boolean
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    accessToken: localStorage.getItem('itam_access_token') || '',
    refreshToken: localStorage.getItem('itam_refresh_token') || '',
    userId: '',
    username: '',
    displayName: '',
    userType: '',
    mustChangePassword: false,
    tenantId: null,
    permissions: [],
    sessionLoaded: false
  }),

  getters: {
    isLoggedIn: (s) => !!s.accessToken,
    isPlatformAdmin: (s) => s.userType === 'PLATFORM',
    hasPermission: (s) => (code: string) => s.permissions.includes(code)
  },

  actions: {
    applyLogin(resp: LoginResponse) {
      this.accessToken = resp.accessToken
      this.refreshToken = resp.refreshToken
      this.userType = resp.userType
      this.mustChangePassword = resp.mustChangePassword
      this.tenantId = resp.tenantId
      this.username = resp.username
      this.displayName = resp.displayName
      setTokens(resp.accessToken, resp.refreshToken)
    },

    applyTokens(tokens: TokenResponse) {
      this.accessToken = tokens.accessToken
      this.refreshToken = tokens.refreshToken
      setTokens(tokens.accessToken, tokens.refreshToken)
    },

    // 登录后 / 续期后拉取会话信息（用户信息、权限、菜单、可切换租户）
    async loadSession() {
      const [me, perms] = await Promise.all([
        authApi.getMe(),
        authApi.getPermissions()
      ])
      this.userId = me.id
      this.username = me.username
      this.displayName = me.displayName
      this.userType = me.userType
      this.mustChangePassword = me.mustChangePassword
      this.tenantId = me.tenantId
      this.permissions = perms

      const menuStore = useMenuStore()
      const tenantStore = useTenantStore()
      await Promise.all([menuStore.fetchMenu(), tenantStore.fetchTenants()])
      this.sessionLoaded = true
    },

    async switchTenant(tenantId: string) {
      const tokens = await authApi.switchTenant(tenantId)
      this.applyTokens(tokens)
      // 切换后权限/菜单/租户列表都会变化，重新加载整个会话
      await this.loadSession()
    },

    async changePassword(oldPassword: string, newPassword: string) {
      await authApi.changePassword({ oldPassword, newPassword })
      this.mustChangePassword = false
    },

    logout() {
      try {
        void authApi.logout()
      } catch {
        // 忽略登出接口错误，本地清理即可
      }
      clearTokens()
      this.$reset()
    }
  }
})
