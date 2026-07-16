<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElButton,
  ElEmpty,
  ElMessage,
  ElSelect,
  ElOption,
  ElTable,
  ElTableColumn,
  ElTag
} from 'element-plus'
import { getNotifications, markNotificationRead, markAllNotificationsRead } from '../../api/notification'
import type { Notification, NotificationType } from '../../types'
import { useUserStore } from '../../store/user'
import { useNotificationStore } from '../../store/notification'
import { useViewState } from '../../composables/useViewState'
import StateView from '../../components/StateView.vue'

const router = useRouter()
const userStore = useUserStore()
const notificationStore = useNotificationStore()
const view = useViewState()

const list = ref<Notification[]>([])
const typeFilter = ref<NotificationType | ''>('')

const canRead = computed(() => userStore.hasPermission('notification:read'))

type TagType = 'success' | 'info' | 'warning' | 'danger' | 'primary'
function typeMeta(t: NotificationType): { label: string; type: TagType } {
  switch (t) {
    case 'APPROVAL_TASK':
      return { label: '待审批', type: 'warning' }
    case 'APPROVAL_APPROVED':
      return { label: '已通过', type: 'success' }
    case 'APPROVAL_REJECTED':
      return { label: '已驳回', type: 'danger' }
    case 'APPROVAL_FORWARDED':
      return { label: '转审', type: 'info' }
    default:
      return { label: t, type: 'info' }
  }
}

const displayed = computed(() =>
  typeFilter.value ? list.value.filter((n) => n.type === typeFilter.value) : list.value
)

async function load() {
  if (!userStore.hasPermission('notification:view')) {
    view.setNoPermission()
    return
  }
  view.setLoading()
  try {
    list.value = await getNotifications()
    view.settle(list.value.length > 0)
    await notificationStore.refresh()
  } catch (e: unknown) {
    view.setError((e as { message?: string }).message || '加载通知失败')
  }
}

function isUnread(n: Notification): boolean {
  return !n.readAt
}

async function readOne(n: Notification) {
  if (!isUnread(n) || !canRead.value) return
  try {
    await markNotificationRead(n.id)
    n.readAt = new Date().toISOString()
    await notificationStore.refresh()
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string }).message || '标记已读失败')
  }
}

async function readAll() {
  if (!canRead.value) return
  try {
    await markAllNotificationsRead()
    list.value.forEach((n) => {
      if (isUnread(n)) n.readAt = new Date().toISOString()
    })
    await notificationStore.refresh()
    ElMessage.success('已全部标记为已读')
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string }).message || '操作失败')
  }
}

function onClickRow(n: Notification) {
  // 点击审批类通知跳转审批详情（businessId 为审批实例 ID）
  if (n.businessType === 'APPROVAL' && n.businessId) {
    router.push(`/approval/instances/${n.businessId}`)
    return
  }
  void readOne(n)
}

onMounted(load)
</script>

<template>
  <div class="notification-list">
    <StateView
      :status="view.status.value"
      :error-detail="view.errorDetail.value"
      @retry="load"
    >
      <div class="toolbar">
        <span class="toolbar__title">通知中心</span>
        <div class="toolbar__right">
          <el-select v-model="typeFilter" size="small" clearable placeholder="全部类型" style="width: 130px">
            <el-option label="待审批" value="APPROVAL_TASK" />
            <el-option label="已通过" value="APPROVAL_APPROVED" />
            <el-option label="已驳回" value="APPROVAL_REJECTED" />
            <el-option label="转审" value="APPROVAL_FORWARDED" />
          </el-select>
          <el-button v-if="canRead" type="primary" size="small" @click="readAll">全部已读</el-button>
        </div>
      </div>

      <el-table :data="displayed" border stripe empty-text="暂无通知" @row-click="onClickRow">
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="typeMeta(row.type).type" size="small">{{ typeMeta(row.type).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="content" label="内容" min-width="220" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="时间" width="180" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="isUnread(row as Notification)" type="danger" size="small" effect="dark">未读</el-tag>
            <el-tag v-else type="info" size="small">已读</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="isUnread(row as Notification) && canRead"
              link
              type="primary"
              size="small"
              @click.stop="readOne(row as Notification)"
            >
              标记已读
            </el-button>
            <span v-else>—</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="view.status.value === 'ready' && displayed.length === 0" description="暂无通知" />
    </StateView>
  </div>
</template>

<style scoped>
.notification-list {
  width: 100%;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.toolbar__title {
  font-size: 16px;
  font-weight: 600;
}
.toolbar__right {
  display: flex;
  align-items: center;
  gap: 10px;
}
</style>
