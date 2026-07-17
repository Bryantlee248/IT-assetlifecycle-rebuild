import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '../store/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/change-password',
    name: 'change-password',
    component: () => import('../views/ChangePasswordView.vue'),
    meta: { title: '修改密码' }
  },
  {
    path: '/',
    component: () => import('../views/MainLayout.vue'),
    redirect: '/profile',
    children: [
      {
        path: 'platform/tenants',
        name: 'platform-tenants',
        component: () => import('../views/platform/TenantView.vue'),
        meta: { title: '平台租户管理', permission: 'tenant:list' }
      },
      {
        path: 'tenant/organizations',
        name: 'tenant-organizations',
        component: () => import('../views/tenant/OrganizationView.vue'),
        meta: { title: '组织管理', permission: 'org:list' }
      },
      {
        path: 'tenant/users',
        name: 'tenant-users',
        component: () => import('../views/tenant/UserView.vue'),
        meta: { title: '用户管理', permission: 'user:list' }
      },
      {
        path: 'tenant/roles',
        name: 'tenant-roles',
        component: () => import('../views/tenant/RoleView.vue'),
        meta: { title: '角色权限', permission: 'role:list' }
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('../views/ProfileView.vue'),
        meta: { title: '个人中心' }
      },
      // ===== MVP-1 元数据与资产核心 =====
      {
        path: 'metadata/asset-types',
        name: 'metadata-asset-types',
        component: () => import('../views/metadata/AssetTypeView.vue'),
        meta: { title: '元数据配置', permission: 'metadata:manage' }
      },
      {
        path: 'assets',
        name: 'asset-list',
        component: () => import('../views/asset/AssetListView.vue'),
        meta: { title: '资产列表', permission: 'asset:view' }
      },
      {
        path: 'assets/new',
        name: 'asset-new',
        component: () => import('../views/asset/AssetEditView.vue'),
        meta: { title: '新增资产', permission: 'asset:create' }
      },
      {
        path: 'assets/:assetId',
        name: 'asset-detail',
        component: () => import('../views/asset/AssetDetailView.vue'),
        meta: { title: '资产详情', permission: 'asset:view' }
      },
      {
        path: 'assets/:assetId/edit',
        name: 'asset-edit',
        component: () => import('../views/asset/AssetEditView.vue'),
        meta: { title: '编辑资产', permission: 'asset:update' }
      },
      // ===== MVP-3 审批 =====
      {
        path: 'approval/tasks',
        name: 'approval-tasks',
        component: () => import('../views/approval/ApprovalTodoView.vue'),
        meta: { title: '审批待办', permission: 'approval:view' }
      },
      {
        path: 'approval/instances/:id',
        name: 'approval-detail',
        component: () => import('../views/approval/ApprovalDetailView.vue'),
        meta: { title: '审批详情', permission: 'approval:view' }
      },
      // ===== MVP-3 通知 =====
      {
        path: 'notification/list',
        name: 'notification-list',
        component: () => import('../views/notification/NotificationListView.vue'),
        meta: { title: '通知中心', permission: 'notification:view' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：
// - 未登录 → /login
// - 已登录但 mustChangePassword → /change-password（当前已在改密页则放行）
// - 已登录但会话未加载 → 先 loadSession（菜单/权限），失败则登出回登录
// - 已登录访问 /login → 回主页
router.beforeEach(async (to) => {
  const user = useUserStore()

  if (to.meta.public) return true

  if (!user.isLoggedIn) {
    return { path: '/login' }
  }

  if (!user.sessionLoaded) {
    try {
      await user.loadSession()
    } catch {
      user.logout()
      return { path: '/login' }
    }
  }

  if (user.mustChangePassword && to.name !== 'change-password') {
    return { path: '/change-password' }
  }

  if (to.path === '/login') {
    return { path: '/' }
  }

  return true
})

export default router
