<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  ElButton,
  ElDialog,
  ElDescriptions,
  ElDescriptionsItem,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElTag,
  ElTimeline,
  ElTimelineItem
} from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getApprovalInstance, approveInstance, rejectInstance } from '../../api/approval'
import type { ApprovalInstance, InstanceStatus, TaskStatus } from '../../types'
import { useUserStore } from '../../store/user'
import { useNotificationStore } from '../../store/notification'
import { useViewState } from '../../composables/useViewState'
import StateView from '../../components/StateView.vue'

const route = useRoute()
const userStore = useUserStore()
const notificationStore = useNotificationStore()
const view = useViewState()

const instance = ref<ApprovalInstance | null>(null)
const id = computed(() => route.params.id as string)

const canApprove = computed(() => userStore.hasPermission('approval:approve'))
const canReject = computed(() => userStore.hasPermission('approval:reject'))
const canDecide = computed(() => (instance.value?.tasks || []).some((t) => t.canDecide))

function fmt(v: string | null | undefined): string {
  if (!v) return '—'
  return v
}

type TagType = 'success' | 'info' | 'warning' | 'danger' | 'primary'
function instanceStatusTag(s: InstanceStatus): { label: string; type: TagType } {
  switch (s) {
    case 'PENDING':
      return { label: '审批中', type: 'warning' }
    case 'APPROVED':
      return { label: '已通过', type: 'success' }
    case 'REJECTED':
      return { label: '已驳回', type: 'danger' }
    case 'CANCELLED':
      return { label: '已取消', type: 'info' }
    default:
      return { label: s, type: 'info' }
  }
}
function taskStatusTag(s: TaskStatus): { label: string; type: TagType } {
  switch (s) {
    case 'PENDING':
      return { label: '待审', type: 'warning' }
    case 'APPROVED':
      return { label: '已通过', type: 'success' }
    case 'REJECTED':
      return { label: '已驳回', type: 'danger' }
    case 'CANCELLED':
      return { label: '已取消', type: 'info' }
    default:
      return { label: s, type: 'info' }
  }
}

async function load() {
  if (!userStore.hasPermission('approval:view')) {
    view.setNoPermission()
    return
  }
  view.setLoading()
  try {
    instance.value = await getApprovalInstance(id.value)
    view.setReady()
  } catch (e: unknown) {
    view.setError((e as { message?: string }).message || '加载审批详情失败')
  }
}

// ===== 通过 / 驳回 对话框 =====
const dialogVisible = ref(false)
const dialogMode = ref<'approve' | 'reject'>('approve')
const formRef = ref<FormInstance>()
const comment = ref('')
const submitting = ref(false)

const rules: FormRules = {
  comment: [
    {
      validator: (_r, v: string, cb) => {
        if (dialogMode.value === 'reject' && (!v || !v.trim())) {
          cb(new Error('驳回必须填写意见'))
        } else {
          cb()
        }
      },
      trigger: 'blur'
    }
  ]
}

function openApprove() {
  dialogMode.value = 'approve'
  comment.value = ''
  dialogVisible.value = true
}
function openReject() {
  dialogMode.value = 'reject'
  comment.value = ''
  dialogVisible.value = true
}

async function confirm() {
  if (!instance.value || !formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    if (dialogMode.value === 'approve') {
      await approveInstance(id.value, comment.value.trim() || undefined)
      ElMessage.success('已通过审批')
    } else {
      await rejectInstance(id.value, comment.value.trim())
      ElMessage.success('已驳回')
    }
    dialogVisible.value = false
    await load()
    await notificationStore.refresh()
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string }).message || '操作失败')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="approval-detail">
    <StateView
      :status="view.status.value"
      :error-detail="view.errorDetail.value"
      @retry="load"
    >
      <template v-if="instance">
        <el-descriptions class="block" title="审批信息" :column="2" border>
          <el-descriptions-item label="标题">{{ fmt(instance.title) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="instanceStatusTag(instance.status).type" size="small">
              {{ instanceStatusTag(instance.status).label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="申请人">{{ fmt(instance.applicantName) }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ fmt(instance.createdAt) }}</el-descriptions-item>
        </el-descriptions>

        <el-descriptions class="block" title="业务上下文" :column="1" border>
          <el-descriptions-item label="资产 ID">{{ fmt(instance.assetId) }}</el-descriptions-item>
          <el-descriptions-item label="动作">
            {{ instance.actionName || instance.actionCode }}
          </el-descriptions-item>
          <el-descriptions-item label="状态流转">
            <el-tag size="small">{{ instance.fromState }}</el-tag>
            →
            <el-tag size="small" type="success">{{ instance.toState }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="原因">{{ fmt(instance.reason) }}</el-descriptions-item>
        </el-descriptions>

        <el-card class="block" shadow="never">
          <template #header>审批任务历史</template>
          <el-timeline>
            <el-timeline-item
              v-for="t in instance.tasks"
              :key="t.id"
              :timestamp="t.decidedAt || t.createdAt"
              placement="top"
              :type="t.status === 'APPROVED' ? 'success' : t.status === 'REJECTED' ? 'danger' : 'primary'"
            >
              <div class="task-row">
                <span>节点 {{ t.nodeOrder }}</span>
                <span>审批人：{{ t.approverName || t.approverId }}</span>
                <el-tag :type="taskStatusTag(t.status).type" size="small">
                  {{ taskStatusTag(t.status).label }}
                </el-tag>
              </div>
              <div v-if="t.comment" class="task-comment">意见：{{ t.comment }}</div>
            </el-timeline-item>
          </el-timeline>
        </el-card>

        <div v-if="canDecide" class="actions">
          <el-button v-if="canApprove" type="success" @click="openApprove">通过</el-button>
          <el-button v-if="canReject" type="danger" @click="openReject">驳回</el-button>
        </div>
      </template>
    </StateView>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'approve' ? '通过审批' : '驳回审批'"
      width="460px"
      :close-on-click-modal="false"
    >
      <p v-if="instance" class="dlg-sub">
        审批单：<strong>{{ instance.title }}</strong>
        <br />
        动作：{{ instance.actionName || instance.actionCode }}
        （{{ instance.fromState }} → {{ instance.toState }}）
      </p>
      <el-form ref="formRef" :model="{ comment }" :rules="rules" label-width="80px">
        <el-form-item :label="dialogMode === 'approve' ? '审批意见' : '驳回意见'" prop="comment">
          <el-input
            v-model="comment"
            type="textarea"
            :rows="3"
            :placeholder="dialogMode === 'approve' ? '可选，填写审批意见' : '必填，请说明驳回原因'"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="submitting" @click="dialogVisible = false">取消</el-button>
        <el-button
          :type="dialogMode === 'approve' ? 'success' : 'danger'"
          :loading="submitting"
          @click="confirm"
        >
          {{ dialogMode === 'approve' ? '确认通过' : '确认驳回' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.approval-detail {
  width: 100%;
}
.block {
  margin-bottom: 16px;
}
.actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}
.task-row {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}
.task-comment {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.dlg-sub {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.6;
}
</style>
