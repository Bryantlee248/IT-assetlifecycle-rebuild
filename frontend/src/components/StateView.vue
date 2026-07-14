<script setup lang="ts">
import { ElButton, ElIcon } from 'element-plus'
import { Loading, Warning, CircleClose, Lock } from '@element-plus/icons-vue'

// 数据型区域统一四态组件：loading / empty / error / no-permission
// 当 status === 'ready' 时渲染默认插槽（真实内容），否则渲染对应占位。
export type ViewStatus = 'loading' | 'empty' | 'error' | 'no-permission' | 'ready'

defineProps<{
  status: ViewStatus
  emptyText?: string
  errorText?: string
  noPermissionText?: string
  errorDetail?: string
}>()

const emit = defineEmits<{ (e: 'retry'): void }>()
</script>

<template>
  <div v-if="status !== 'ready'" class="state-view">
    <div v-if="status === 'loading'" class="state-view__box">
      <el-icon class="state-view__icon is-loading"><Loading /></el-icon>
      <p class="state-view__text">加载中…</p>
    </div>

    <div v-else-if="status === 'empty'" class="state-view__box">
      <el-icon class="state-view__icon state-view__icon--muted"><CircleClose /></el-icon>
      <p class="state-view__text">{{ emptyText || '暂无数据' }}</p>
    </div>

    <div v-else-if="status === 'error'" class="state-view__box">
      <el-icon class="state-view__icon state-view__icon--error"><Warning /></el-icon>
      <p class="state-view__text">{{ errorText || '加载失败，请稍后重试' }}</p>
      <p v-if="errorDetail" class="state-view__detail">{{ errorDetail }}</p>
      <el-button type="primary" @click="emit('retry')">重试</el-button>
    </div>

    <div v-else-if="status === 'no-permission'" class="state-view__box">
      <el-icon class="state-view__icon state-view__icon--muted"><Lock /></el-icon>
      <p class="state-view__text">{{ noPermissionText || '无访问权限' }}</p>
    </div>
  </div>
  <slot v-else />
</template>

<style scoped>
.state-view {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 240px;
  width: 100%;
}
.state-view__box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  color: var(--el-text-color-secondary);
}
.state-view__icon {
  font-size: 42px;
}
.state-view__icon--muted {
  color: var(--el-color-info-light-3);
}
.state-view__icon--error {
  color: var(--el-color-danger);
}
.state-view__text {
  margin: 0;
  font-size: 14px;
}
.state-view__detail {
  margin: 0;
  font-size: 12px;
  color: var(--el-color-danger-light-3);
  max-width: 420px;
  text-align: center;
  word-break: break-all;
}
</style>
