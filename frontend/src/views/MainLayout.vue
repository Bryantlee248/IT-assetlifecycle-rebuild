<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMenuItem, ElSubMenu } from 'element-plus'
import {
  OfficeBuilding,
  Share,
  User,
  Lock,
  Setting,
  ArrowDown
} from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'
import { useMenuStore } from '../store/menu'
import { useTenantStore } from '../store/tenant'
import type { MenuNode } from '../types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const menuStore = useMenuStore()
const tenantStore = useTenantStore()

const iconMap: Record<string, Component> = {
  Office: OfficeBuilding,
  Share,
  User,
  Lock,
  Setting
}
// 后端返回的是 Element Plus 图标组件名；全局已注册，同名可直接用，仅别名在此映射。
function resolveIcon(name: string): Component {
  return iconMap[name] || (name as unknown as Component)
}

// 递归按 key 查找菜单节点
function findNode(nodes: MenuNode[], key: string): MenuNode | null {
  for (const n of nodes) {
    if (n.key === key) return n
    const hit = findNode(n.children, key)
    if (hit) return hit
  }
  return null
}
// 递归按"去 query 的路径"匹配当前路由，返回对应 key 作为高亮项
function matchKey(nodes: MenuNode[], path: string): string {
  for (const n of nodes) {
    if (n.path && n.path.split('?')[0] === path) return n.key
    const hit = matchKey(n.children, path)
    if (hit) return hit
  }
  return route.path
}

const activeMenu = computed(() => matchKey(menuStore.menuList, route.path))

function onSelect(key: string) {
  const node = findNode(menuStore.menuList, key)
  if (node && node.path) {
    router.push(node.path)
  }
}

const currentTenantName = computed(
  () =>
    tenantStore.switchableTenants.find((t) => t.id === userStore.tenantId)?.name ||
    '请选择'
)

const canSwitchTenant = computed(
  () => !userStore.isPlatformAdmin && tenantStore.switchableTenants.length > 0
)

function onLogout() {
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}

async function onSwitchTenant(id: string) {
  try {
    await userStore.switchTenant(id)
    ElMessage.success('已切换租户')
    window.location.reload()
  } catch {
    // 错误提示由拦截器处理
  }
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="220px" class="layout-aside">
      <div class="brand">
        <span class="brand-logo">ITAM</span>
        <span class="brand-text">资产管理系统</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="layout-menu"
        background-color="#0b1f3a"
        text-color="#c5d2e6"
        active-text-color="#ffffff"
        @select="onSelect"
      >
        <template v-for="node in menuStore.menuList" :key="node.key">
          <!-- 含子节点的分组：渲染为子菜单 -->
          <el-sub-menu v-if="node.children && node.children.length" :index="node.key">
            <template #title>
              <el-icon><component :is="resolveIcon(node.icon)" /></el-icon>
              <span>{{ node.title }}</span>
            </template>
            <el-menu-item
              v-for="child in node.children"
              :key="child.key"
              :index="child.key"
            >
              <el-icon><component :is="resolveIcon(child.icon)" /></el-icon>
              <span>{{ child.title }}</span>
            </el-menu-item>
          </el-sub-menu>
          <!-- 叶子节点 -->
          <el-menu-item v-else :index="node.key">
            <el-icon><component :is="resolveIcon(node.icon)" /></el-icon>
            <span>{{ node.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <div class="header-title">{{ route.meta.title || 'IT 资产管理系统' }}</div>
        <div class="header-right">
          <el-dropdown
            v-if="canSwitchTenant"
            trigger="click"
            @command="onSwitchTenant"
          >
            <span class="header-tenant">
              租户：{{ currentTenantName }}<el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="t in tenantStore.switchableTenants"
                  :key="t.id"
                  :command="t.id"
                >
                  {{ t.name }}（{{ t.code }}）
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <el-dropdown trigger="click">
            <span class="header-user">
              <el-icon><User /></el-icon>
              {{ userStore.displayName || userStore.username }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/profile')">
                  个人中心
                </el-dropdown-item>
                <el-dropdown-item divided @click="onLogout">
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}
.layout-aside {
  background: #0b1f3a;
  overflow: hidden;
}
.brand {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 18px;
  color: #fff;
}
.brand-logo {
  background: var(--itam-primary);
  padding: 4px 8px;
  border-radius: 6px;
  font-weight: 700;
  font-size: 14px;
}
.brand-text {
  font-size: 14px;
  color: #c5d2e6;
}
.layout-menu {
  border-right: none;
}
.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}
.header-title {
  font-size: 16px;
  font-weight: 600;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 18px;
}
.header-user,
.header-tenant {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #1f2329;
  outline: none;
}
.layout-main {
  background: #f5f7fa;
  padding: 18px;
}
</style>
