<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElMessage, ElSelect, ElOption, ElTable, ElTableColumn, ElTag } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getMyTodos, approveInstance, rejectInstance } from '../../api/approval'
import type { ApprovalTask, TaskStatus } from '../../types'
import { useUserStore } from '../../store/user'
import { useViewState } from '../../composables/useViewState'
import StateView from '../../components/StateView.vue'

const router = useRouter()
const userStore = useUserStore()
const view = useViewState()

const tasks = ref<ApprovalTask[]>([])
// 筛选：pending=待审；done=已办（前端过滤）；all=全部
const filter = ref<'pending' | 'done' | 'all'>('pending')

const canApprove = computed(() => userStore.hasPermission('approval:approve'))
const canReject = computed(() => userStore.hasPermission('approval:reject'))

const displayed = computed(() => {
  if (filter.value === 'pending') return tasks.value.filter((t) => t.status === 'PENDING')
  if (filter.value === 'done') return tasks.value.filter((t) => t.status !== 'PENDING')
  return tasks.value
})

function fmt(v: string | null | undefined): string {
  if (!v) return '—'
  return v
}

type TagType = 'success' | 'info' | 'warning' | 'danger' | 'primary'
function statusTag(status: TaskStatus): { label: string; type: TagType } {
  switch (status) {
    case 'PENDING':
      return { label: '待审', type: 'warning' }
    case 'APPROVED':
      return { label: '已通过', type: 'success' }
    case 'REJECTED':
      return { label: '已驳回', type: 'danger' }
    case 'CANCELLED':
      return { label: '已取消', type: 'info' }
    default:
      return { label: status, type: 'info' }
  }
}

async function load() {
  if (!userStore.hasPermission('approval:view')) {
    view.setNoPermission()
    return
  }
  view.setLoading()
  try {
    // 后端 null 状态返回该用户全部任务，前端按筛选二次过滤
    tasks.value = await getMyTodos()
    view.settle(tasks.value.length > 0)
  } catch (e: unknown) {
    view.setError((e as { message?: string }).message || '加载审批待办失败')
  }
}

function openDetail(task: ApprovalTask) {
  if (task.instanceId) router.push(`/approval/instances/${task.instanceId}`)
}

// ===== 通过 / 驳回 对话框 =====
const dialogVisible = ref(false)
const dialogMode = ref<'approve' | 'reject'>('approve')
const activeTask = ref<ApprovalTask | null>(null)
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

function openApprove(task: ApprovalTask) {
  activeTask.value = task
  dialogMode.value = 'approve'
  comment.value = ''
  dialogVisible.value = true
}
function openReject(task: ApprovalTask) {
  activeTask.value = task
  dialogMode.value = 'reject'
  comment.value = ''
  dialogVisible.value = true
}

async function confirm() {
  if (!activeTask.value || !formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  const id = activeTask.value.instanceId
  try {
    if (dialogMode.value === 'approve') {
      await approveInstance(id, comment.value.trim() || undefined)
      ElMessage.success('已通过审批')
    } else {
      await rejectInstance(id, comment.value.trim())
      ElMessage.success('已驳回')
    }
    dialogVisible.value = false
    await load()
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string }).message || '操作失败')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="approval-todo">
    <StateView
      :status="view.status.value"
      :error-detail="view.errorDetail.value"
      @retry="load"
    >
      <div class="toolbar">
        <span class="toolbar__title">我的审批待办</span>
        <el-select v-model="filter" size="small" style="width: 120px" @change="() => {}">
          <el-option label="待审" value="pending" />
          <el-option label="已办" value="done" />
          <el-option label="全部" value="all" />
        </el-select>
      </div>

      <el-table :data="displayed" border stripe empty-text="暂无待办">
        <el-table-column prop="instance.title" label="标题" min-width="200">
          <template #default="{ row }">
            <a class="link" @click="openDetail(row as ApprovalTask)">{{ row.instance?.title || row.instanceId }}</a>
          </template>
        </el-table-column>
        <el-table-column label="业务类型" width="140">
          <template #default="{ row }">
            {{ row.instance?.actionName || row.instance?.actionCode || '—' }}
          </template>
        </el-table-column>
        <el-table-column prop="instance.applicantName" label="申请人" width="120">
          <template #default="{ row }">{{ fmt(row.instance?.applicantName) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="提交时间" width="180">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status).type" size="small">
              {{ statusTag(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row as ApprovalTask)">查看</el-button>
            <el-button
              v-if="row.canDecide && canApprove"
              link
              type="success"
              size="small"
              @click="openApprove(row as ApprovalTask)"
            >
              通过
            </el-button>
            <el-button
              v-if="row.canDecide && canReject"
              link
              type="danger"
              size="small"
              @click="openReject(row as ApprovalTask)"
            >
              驳回
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </StateView>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'approve' ? '通过审批' : '驳回审批'"
      width="460px"
      :close-on-click-modal="false"
    >
      <p v-if="activeTask?.instance" class="dlg-sub">
        审批单：<strong>{{ activeTask.instance.title }}</strong>
        <br />
        动作：{{ activeTask.instance.actionName || activeTask.instance.actionCode }}
        （{{ activeTask.instance.fromState }} → {{ activeTask.instance.toState }}）
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
.approval-todo {
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
.link {
  color: var(--el-color-primary);
  cursor: pointer;
}
.link:hover {
  text-decoration: underline;
}
.dlg-sub {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.6;
}
</style>
