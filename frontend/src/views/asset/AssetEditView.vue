<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createAsset, getAsset, updateAsset } from '../../api/asset'
import { getRuntimeMetadata } from '../../api/metadata'
import type { AssetResponse, CreateAssetRequest, UpdateAssetRequest, RuntimeMetadataResponse } from '../../types'
import { useViewState } from '../../composables/useViewState'
import StateView from '../../components/StateView.vue'
import AssetTypeTree from '../../components/metadata/AssetTypeTree.vue'
import DynamicForm from '../../components/metadata/DynamicForm.vue'

const route = useRoute()
const router = useRouter()
const view = useViewState()

const assetId = computed(() => (route.params.assetId as string) || null)
const isEdit = computed(() => !!assetId.value)

const selectedTypeId = ref<string | null>(null)
const formValue = ref<Record<string, unknown>>({})
const loaded = ref(false)
const saving = ref(false)
const meta = ref<RuntimeMetadataResponse | null>(null)

// 固定物理/身份字段（camelCase 表单键 → snake_case 权限编码）。
const FIXED_KEYS: string[] = [
  'assetName', 'assetNo', 'serialNo', 'brand', 'model', 'vendor',
  'warrantyEndDate', 'licenseEndDate', 'ownerUserId', 'ownerOrgId',
  'locationId', 'costCenterId', 'responsibleUserId'
]
const FIXED_TO_CODE: Record<string, string> = {
  assetName: 'asset_name', assetNo: 'asset_no', serialNo: 'serial_no',
  brand: 'brand', model: 'model', vendor: 'vendor',
  warrantyEndDate: 'warranty_end_date', licenseEndDate: 'license_end_date',
  ownerUserId: 'owner_user_id', ownerOrgId: 'owner_org_id',
  locationId: 'location_id', costCenterId: 'cost_center_id',
  responsibleUserId: 'responsible_user_id'
}
// 身份字段始终纳入（创建者角色必定可编辑）。
const ALWAYS_INCLUDE = new Set(['assetName', 'assetNo'])

function permEditable(code: string): boolean {
  const p = meta.value?.fieldPermissions?.[code]
  return !p || (p.visible !== false && p.editable !== false)
}

function assetToForm(a: AssetResponse): Record<string, unknown> {
  const physToForm: Record<string, string> = {
    serial_no: 'serialNo', brand: 'brand', model: 'model', vendor: 'vendor',
    warranty_end_date: 'warrantyEndDate', license_end_date: 'licenseEndDate'
  }
  const form: Record<string, unknown> = {
    assetName: a.assetName, assetNo: a.assetNo, serialNo: '', brand: '', model: '',
    vendor: '', warrantyEndDate: '', licenseEndDate: '', ownerUserId: a.ownerUserId,
    ownerOrgId: a.ownerOrgId, locationId: a.locationId, costCenterId: a.costCenterId,
    responsibleUserId: a.responsibleUserId, ...a.fields
  }
  for (const [code, key] of Object.entries(physToForm)) {
    if (a.fields?.[code] != null) form[key] = a.fields[code]
  }
  return form
}

// P0-3：按字段权限构建 payload，不提交不可见/不可编辑字段（后端仍权威）。
function buildPayload(): CreateAssetRequest | UpdateAssetRequest {
  const attrs: Record<string, unknown> = {}
  const req: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(formValue.value)) {
    if (FIXED_KEYS.includes(k)) {
      if (ALWAYS_INCLUDE.has(k) || permEditable(FIXED_TO_CODE[k])) {
        req[k] = v === '' ? null : v
      }
    } else if (v !== undefined && v !== null && v !== '') {
      const code = k // 动态字段编码即 snake_case
      if (permEditable(code)) attrs[k] = v
    }
  }
  req.attributes = attrs
  if (!isEdit.value) {
    req.assetTypeId = selectedTypeId.value as string
    req.lifecycleStatus = 'planned' // 后端固定为 planned，忽略前端值
  }
  return req as CreateAssetRequest | UpdateAssetRequest
}

async function load() {
  view.setLoading()
  try {
    if (isEdit.value) {
      const a = await getAsset(assetId.value as string)
      selectedTypeId.value = a.assetTypeId
      formValue.value = assetToForm(a)
      meta.value = await getRuntimeMetadata(a.assetTypeId)
    }
    loaded.value = true
    view.setReady()
  } catch (e: unknown) {
    view.setError((e as { message?: string }).message || '加载失败')
  }
}

async function submit() {
  if (!isEdit.value && !selectedTypeId.value) {
    ElMessage.warning('请先选择资产类型')
    return
  }
  // 创建前确保已加载目标类型的字段权限，以便按权限裁剪 payload。
  if (!isEdit.value && selectedTypeId.value && !meta.value) {
    try {
      meta.value = await getRuntimeMetadata(selectedTypeId.value)
    } catch {
      // 忽略：回退为包含全部字段
    }
  }
  const payload = buildPayload()
  saving.value = true
  try {
    if (isEdit.value) {
      await updateAsset(assetId.value as string, payload as UpdateAssetRequest)
      ElMessage.success('已保存')
      router.push(`/assets/${assetId.value}`)
    } else {
      const created = await createAsset(payload as CreateAssetRequest)
      ElMessage.success('已创建')
      router.push(`/assets/${created.id}`)
    }
  } catch {
    // 错误提示由拦截器处理
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="asset-edit">
    <StateView
      :status="view.status.value"
      :error-detail="view.errorDetail.value"
      @retry="load"
    >
      <template v-if="loaded">
        <el-card shadow="never">
          <template #header>
            <span>{{ isEdit ? '编辑资产' : '新增资产' }}</span>
          </template>

          <el-form label-width="100px" class="type-row">
            <el-form-item label="资产类型" required>
              <AssetTypeTree v-model="selectedTypeId" :disabled="isEdit" />
            </el-form-item>
          </el-form>

          <DynamicForm
            v-if="selectedTypeId"
            :key="(selectedTypeId || '') + (assetId || 'new')"
            :asset-type-id="selectedTypeId"
            :model-value="formValue"
            @update:model-value="(v: Record<string, unknown>) => (formValue = v)"
          />

          <div class="actions">
            <el-button @click="router.back()">取消</el-button>
            <el-button type="primary" :loading="saving" @click="submit">
              {{ isEdit ? '保存' : '创建' }}
            </el-button>
          </div>
        </el-card>
      </template>
    </StateView>
  </div>
</template>

<style scoped>
.asset-edit {
  width: 100%;
}
.type-row {
  max-width: 420px;
  margin-bottom: 8px;
}
.actions {
  margin-top: 16px;
  text-align: right;
}
</style>
