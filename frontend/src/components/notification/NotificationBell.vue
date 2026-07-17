<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElBadge, ElIcon } from 'element-plus'
import { Bell } from '@element-plus/icons-vue'
import { useUserStore } from '../../store/user'
import { useNotificationStore } from '../../store/notification'

const router = useRouter()
const userStore = useUserStore()
const notificationStore = useNotificationStore()

let timer: number | undefined

onMounted(async () => {
  if (!userStore.hasPermission('notification:view')) return
  await notificationStore.refresh()
  // 轻量轮询：每 30s 同步未读数
  timer = window.setInterval(() => {
    void notificationStore.refresh()
  }, 30000)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})

function onClick() {
  router.push('/notification/list')
}
</script>

<template>
  <div v-if="userStore.hasPermission('notification:view')" class="bell" @click="onClick">
    <el-badge :value="notificationStore.unreadCount" :max="99" :hidden="!notificationStore.hasUnread">
      <el-icon :size="20"><Bell /></el-icon>
    </el-badge>
  </div>
</template>

<style scoped>
.bell {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
  color: #1f2329;
  outline: none;
  padding: 0 4px;
}
.bell:hover {
  color: var(--el-color-primary);
}
</style>
