<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElIcon, ElMessage, ElTag } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import { getAsset } from '../../api/asset'
import { getRuntimeMetadata } from '../../api/metadata'
import {
  getLifecycle,
  getLifecycleActions,
  getLifecycleEvents,
  executeLifecycleAction
} from '../../api/lifecycle'
import type {
  AssetResponse,
  FieldPermissionView,
  RuntimeMetadataResponse,
  LifecycleStatus,
  LifecycleAction,
  LifecycleEvent
} from '../../types'
import { useUserStore } from '../../store/user'
import { useViewState } from '../../composables/useViewState'
import StateView from '../../components/StateView.vue'
import RelationTab from '../../components/asset/RelationTab.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const view = useViewState()

const asset = ref<AssetResponse | null>(null)
const meta = ref<RuntimeMetadataResponse | null>(null)

const canUpdate = computed(() => userStore.hasPermission('asset:update'))

// ===== 生命周期三区：各自独立的四态 =====
const statusView = useViewState()
const actionView = useViewState()
const eventView = useViewState()

const lifecycle = ref<LifecycleStatus | null>(null)
const actions = ref<LifecycleAction[]>([])
const events = ref<LifecycleEvent[]>([])

const canTransition = computed(() => userStore.hasPermission('lifecycle:transition'))
const canViewLifecycle = computed(() => userStore.hasPermission('lifecycle:view'))
const assetId = computed(() => route.params.assetId as string)

// 动态字段行：仅渲染后端返回（已按可见性过滤、已脱敏）的字段。
const dynamicRows = computed(() => {
  if (!asset.value || !meta.value) return []
  return meta.value.fields
    .filter((f) => asset.value!.fields[f.fieldCode] !== undefined)
    .map((f) => {
      const perm: FieldPermissionView | undefined = meta.value!.fieldPermissions[f.fieldCode]
      return {
        code: f.fieldCode,
        name: f.fieldName,
        value: asset.value!.fields[f.fieldCode],
        locked: !!(perm && (perm.masked || f.sensitive || f.encrypted))
      }
    })
})

function fmt(v: unknown): string {
  if (v === null || v === undefined) return '—'
  if (typeof v === 'boolean') return v ? '是' : '否'
  return String(v)
}

// 依据状态编码给出合理的标签色彩（无法枚举全部状态时按关键字推断，缺省 info）。
type TagType = 'success' | 'info' | 'warning' | 'danger' | 'primary'
function stateTagType(state: string): TagType {
  const s = (state || '').toUpperCase()
  if (/(SCRAP|DISPOS|LOST|DEAD|BROKEN)/.test(s)) return 'danger'
  if (/(REPAIR|MAINT|BORROW|RETURN|TRANSFER|WARRANTY|FAULT)/.test(s)) return 'warning'
  if (/(IDLE|STOCK|STANDBY|INACTIVE|OFFLINE)/.test(s)) return 'info'
  if (/(USE|ACTIVE|DEPLOY|RUNNING|ONLINE|NORMAL)/.test(s)) return 'success'
  return 'info'
}

// ===== 生命周期三区加载 =====
async function loadStatus() {
  statusView.setLoading()
  try {
    lifecycle.value = await getLifecycle(assetId.value)
    statusView.settle(!!lifecycle.value)
  } catch (e: unknown) {
    statusView.setError((e as { message?: string }).message || '加载生命周期状态失败')
  }
}

async function loadActions() {
  if (!canTransition.value) return
  actionView.setLoading()
  try {
    actions.value = await getLifecycleActions(assetId.value)
    actionView.settle(actions.value.length > 0)
  } catch (e: unknown) {
    actionView.setError((e as { message?: string }).message || '加载可执行动作失败')
  }
}

async function loadEvents() {
  eventView.setLoading()
  try {
    events.value = await getLifecycleEvents(assetId.value)
    eventView.settle(events.value.length > 0)
  } catch (e: unknown) {
    eventView.setError((e as { message?: string }).message || '加载生命周期事件失败')
  }
}

// 动作执行后刷新三区（状态 + 动作 + 事件）。
async function refreshLifecycle() {
  await Promise.all([loadStatus(), loadActions(), loadEvents()])
}

// ===== 执行动作对话框 =====
const actionDialogVisible = ref(false)
const pendingAction = ref<LifecycleAction | null>(null)
const actionFormRef = ref<FormInstance>()
const actionForm = ref<{ reason: string }>({ reason: '' })
const submitting = ref(false)

const actionRules: FormRules = {
  reason: [{ required: true, message: '请填写操作原因', trigger: 'blur' }]
}

function openAction(a: LifecycleAction) {
  pendingAction.value = a
  actionForm.value = { reason: '' }
  actionDialogVisible.value = true
}

function onDialogClose(done: () => void) {
  // 提交中禁止关闭，避免重复提交
  if (submitting.value) return
  done()
}

async function confirmAction() {
  if (!pendingAction.value || !actionFormRef.value) return
  // 校验必填原因；校验失败则拦截
  try {
    await actionFormRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const res = await executeLifecycleAction(assetId.value, pendingAction.value.actionCode, {
      reason: actionForm.value.reason.trim()
    })
    actionDialogVisible.value = false
    if (res.result === 'transitioned') {
      ElMessage.success('状态已更新')
      await refreshLifecycle()
      // 同步基础信息卡中的"生命周期"字段，避免展示过期值。
      // 仅做响应式属性赋值，不重载整条资产，以免重新触发 meta 加载。
      if (res.toState) {
        asset.value!.lifecycleStatus = res.toState
      }
    } else if (res.result === 'approval_required') {
      // 不伪造审批实例，仅提示：审批模块将在 MVP-3 实现
      ElMessage.info('该动作需要审批，审批模块将在 MVP-3 实现')
    }
  } catch (e: unknown) {
    // 拦截器已 ElMessage.error 过；此处再展示 422 守卫 / 409 冲突等细节
    ElMessage.error((e as { message?: string }).message || '操作失败')
  } finally {
    submitting.value = false
  }
}

async function load() {
  if (!userStore.hasPermission('asset:view')) {
    view.setNoPermission()
    return
  }
  view.setLoading()
  const id = route.params.assetId as string
  try {
    asset.value = await getAsset(id)
    meta.value = await getRuntimeMetadata(asset.value.assetTypeId)
    view.setReady()
  } catch (e: unknown) {
    view.setError((e as { message?: string }).message || '加载失败')
    return
  }
  // 资产主数据已就绪，并行加载生命周期三区（各自独立四态，失败互不牵连）。
  // 无 lifecycle:view 权限时，不调用任何生命周期接口，三个区统一进入"无权限"态，
  // 由 <StateView> 渲染锁图标占位（满足"显示清晰无权限态"）。
  if (canViewLifecycle.value) {
    void refreshLifecycle()
  } else {
    statusView.setNoPermission()
    eventView.setNoPermission()
    actionView.setNoPermission()
  }
}

function onEdit() {
  const id = route.params.assetId as string
  router.push(`/assets/${id}/edit`)
}

onMounted(load)
</script>

<template>
  <div class="asset-detail">
    <StateView
      :status="view.status.value"
      :error-detail="view.errorDetail.value"
      @retry="load"
    >
      <template v-if="asset">
        <el-card shadow="never" class="block">
          <template #header>
            <div class="card-head">
              <span>基础信息</span>
              <el-button v-if="canUpdate" type="primary" size="small" @click="onEdit">编辑</el-button>
            </div>
          </template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="资产编号">{{ fmt(asset.assetNo) }}</el-descriptions-item>
            <el-descriptions-item label="资产名称">{{ fmt(asset.assetName) }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ fmt(asset.assetTypeName) }}</el-descriptions-item>
            <el-descriptions-item label="大类">{{ fmt(asset.assetKind) }}</el-descriptions-item>
            <el-descriptions-item label="生命周期">{{ fmt(asset.lifecycleStatus) }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ fmt(asset.status) }}</el-descriptions-item>
            <el-descriptions-item label="来源">{{ fmt(asset.sourceType) }}</el-descriptions-item>
            <el-descriptions-item label="责任人">{{ fmt(asset.responsibleUserId) }}</el-descriptions-item>
            <el-descriptions-item label="使用人">{{ fmt(asset.ownerUserId) }}</el-descriptions-item>
            <el-descriptions-item label="使用部门">{{ fmt(asset.ownerOrgId) }}</el-descriptions-item>
            <el-descriptions-item label="位置">{{ fmt(asset.locationId) }}</el-descriptions-item>
            <el-descriptions-item label="成本中心">{{ fmt(asset.costCenterId) }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ fmt(asset.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ fmt(asset.updatedAt) }}</el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 生命周期状态区 -->
        <el-card shadow="never" class="block">
          <template #header>生命周期状态</template>
          <StateView
            :status="statusView.status.value"
            :error-detail="statusView.errorDetail.value"
            @retry="loadStatus"
          >
            <el-descriptions v-if="lifecycle" :column="1" border>
              <el-descriptions-item label="当前状态">
                <el-tag :type="stateTagType(lifecycle.currentState)">
                  {{ lifecycle.currentStateName }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="状态编码">{{ lifecycle.currentState }}</el-descriptions-item>
              <el-descriptions-item label="生命周期模板">{{ lifecycle.templateName }}</el-descriptions-item>
            </el-descriptions>
          </StateView>
        </el-card>

        <!-- 生命周期操作区（无 lifecycle:transition 权限时整体隐藏） -->
        <el-card v-if="canTransition" shadow="never" class="block">
          <template #header>生命周期操作</template>
          <StateView
            :status="actionView.status.value"
            :error-detail="actionView.errorDetail.value"
            @retry="loadActions"
          >
            <div class="action-row">
              <el-button
                v-for="a in actions"
                :key="a.actionCode"
                type="primary"
                plain
                @click="openAction(a)"
              >
                {{ a.actionName }}
              </el-button>
            </div>
          </StateView>
        </el-card>

        <!-- 生命周期事件时间线区 -->
        <el-card shadow="never" class="block">
          <template #header>生命周期事件</template>
          <StateView
            :status="eventView.status.value"
            :error-detail="eventView.errorDetail.value"
            @retry="loadEvents"
          >
            <el-timeline>
              <el-timeline-item
                v-for="ev in events"
                :key="ev.id"
                :timestamp="ev.createdAt"
                placement="top"
                type="primary"
              >
                <div class="ev-title">
                  <el-tag size="small" effect="plain">{{ ev.actionName }}</el-tag>
                  <span class="ev-code">{{ ev.actionCode }}</span>
                </div>
                <div class="ev-flow">{{ ev.fromState || '—' }} → {{ ev.toState || '—' }}</div>
                <div class="ev-meta">操作人：{{ ev.operatorName || '系统' }}</div>
                <el-collapse v-if="ev.reason" class="ev-collapse">
                  <el-collapse-item title="查看原因">
                    <div class="ev-reason">{{ ev.reason }}</div>
                  </el-collapse-item>
                </el-collapse>
              </el-timeline-item>
            </el-timeline>
          </StateView>
        </el-card>

        <el-card shadow="never" class="block">
          <template #header>扩展属性</template>
          <el-descriptions v-if="dynamicRows.length" :column="2" border>
            <el-descriptions-item v-for="r in dynamicRows" :key="r.code" :label="r.name">
              <el-icon v-if="r.locked" color="#e6a23c"><Lock /></el-icon>
              {{ fmt(r.value) }}
            </el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="该资产暂无扩展属性" />
        </el-card>

        <el-card shadow="never" class="block">
          <template #header>资产关系</template>
          <RelationTab :asset-id="asset.id" />
        </el-card>
      </template>
    </StateView>

    <!-- 执行生命周期动作对话框（MVP-2 仅收集必填原因，formData/附件为 MVP-3） -->
    <el-dialog
      v-model="actionDialogVisible"
      title="执行生命周期动作"
      width="480px"
      :close-on-click-modal="false"
      :before-close="onDialogClose"
    >
      <template v-if="pendingAction">
        <p class="dlg-sub">
          动作：<strong>{{ pendingAction.actionName }}</strong>
          <span class="ev-code">({{ pendingAction.actionCode }})</span>
          ，目标状态：{{ pendingAction.toStateName }}
        </p>
        <el-form ref="actionFormRef" :model="actionForm" :rules="actionRules" label-width="80px">
          <el-form-item label="原因" prop="reason">
            <el-input
              v-model="actionForm.reason"
              type="textarea"
              :rows="3"
              placeholder="请填写本次操作的原因（必填）"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button :disabled="submitting" @click="actionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmAction">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.asset-detail {
  width: 100%;
}
.block {
  margin-bottom: 16px;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.ev-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ev-code {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.ev-flow {
  margin-top: 2px;
  font-size: 13px;
  color: var(--el-text-color-primary);
}
.ev-meta {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.ev-collapse {
  margin-top: 6px;
}
.ev-reason {
  white-space: pre-wrap;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.dlg-sub {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
</style>
