<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useUserStore } from '../store/user'

const router = useRouter()
const userStore = useUserStore()

const userTypeLabel = userStore.userType === 'PLATFORM' ? '平台管理员' : '租户用户'
</script>

<template>
  <div class="profile-page">
    <el-card class="profile-card" shadow="never">
      <template #header>
        <div class="profile-head">
          <span>个人中心</span>
          <el-button type="primary" text @click="router.push('/change-password')">
            修改密码
          </el-button>
        </div>
      </template>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="用户ID">
          {{ userStore.userId || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="用户名">
          {{ userStore.username }}
        </el-descriptions-item>
        <el-descriptions-item label="显示名称">
          {{ userStore.displayName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="用户类型">
          {{ userTypeLabel }}
        </el-descriptions-item>
        <el-descriptions-item label="当前租户">
          {{ userStore.tenantId || '（平台，无租户上下文）' }}
        </el-descriptions-item>
        <el-descriptions-item label="需改密">
          {{ userStore.mustChangePassword ? '是' : '否' }}
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">权限列表</el-divider>
      <div v-if="userStore.permissions.length" class="perm-list">
        <el-tag
          v-for="p in userStore.permissions"
          :key="p"
          class="perm-tag"
          type="info"
          effect="plain"
        >
          {{ p }}
        </el-tag>
      </div>
      <el-empty v-else description="暂无权限数据" :image-size="60" />
    </el-card>
  </div>
</template>

<style scoped>
.profile-page {
  max-width: 880px;
}
.profile-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}
.perm-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.perm-tag {
  margin: 0;
}
</style>
