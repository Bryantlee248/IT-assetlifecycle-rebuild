<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { getRuntimeMetadata } from '../../api/metadata'
import type { FieldDefinitionResponse, FieldPermissionView, RuntimeMetadataResponse } from '../../types'
import FieldValue from './FieldValue.vue'
import LocationTreeSelect from './LocationTreeSelect.vue'

const props = defineProps<{
  assetTypeId: string
  modelValue: Record<string, unknown>
}>()
const emit = defineEmits<{ (e: 'update:modelValue', v: Record<string, unknown>): void }>()

const meta = ref<RuntimeMetadataResponse | null>(null)
const loading = ref(false)
const error = ref('')

// 固定物理/身份字段（snake_case 编码 + camelCase 表单键），受字段权限统一管控。
const FIXED_FIELDS: Array<{
  code: string
  formKey: string
  label: string
  required?: boolean
  control?: 'text' | 'location' | 'date'
}> = [
  { code: 'asset_name', formKey: 'assetName', label: '资产名称', required: true, control: 'text' },
  { code: 'asset_no', formKey: 'assetNo', label: '资产编号', required: true, control: 'text' },
  { code: 'responsible_user_id', formKey: 'responsibleUserId', label: '责任人', control: 'text' },
  { code: 'owner_user_id', formKey: 'ownerUserId', label: '使用人', control: 'text' },
  { code: 'owner_org_id', formKey: 'ownerOrgId', label: '使用部门', control: 'text' },
  { code: 'location_id', formKey: 'locationId', label: '位置', control: 'location' },
  { code: 'cost_center_id', formKey: 'costCenterId', label: '成本中心', control: 'text' },
  { code: 'serial_no', formKey: 'serialNo', label: '序列号', control: 'text' },
  { code: 'brand', formKey: 'brand', label: '品牌', control: 'text' },
  { code: 'model', formKey: 'model', label: '型号', control: 'text' },
  { code: 'vendor', formKey: 'vendor', label: '厂商', control: 'text' },
  { code: 'warranty_end_date', formKey: 'warrantyEndDate', label: '保修到期', control: 'date' },
  { code: 'license_end_date', formKey: 'licenseEndDate', label: '许可到期', control: 'date' }
]
const FIXED_SET = new Set(FIXED_FIELDS.map((f) => f.code))

const DENIED: FieldPermissionView = { visible: false, editable: false, masked: false, exportable: false, maskRule: null }
function perm(code: string): FieldPermissionView {
  return meta.value?.fieldPermissions?.[code] ?? DENIED
}

// 仅渲染可见的固定字段。
const visibleFixedFields = computed(() => FIXED_FIELDS.filter((f) => perm(f.code).visible !== false))
// 动态字段：排除固定列，且仅渲染可见字段。
const dynamicFields = computed<FieldDefinitionResponse[]>(
  () => (meta.value?.fields ?? []).filter((f) => !FIXED_SET.has(f.fieldCode) && perm(f.fieldCode).visible !== false)
)

const defaults: Record<string, unknown> = {
  assetName: '',
  assetNo: '',
  responsibleUserId: '',
  ownerUserId: '',
  ownerOrgId: '',
  locationId: '',
  costCenterId: '',
  serialNo: '',
  brand: '',
  model: '',
  vendor: '',
  warrantyEndDate: '',
  licenseEndDate: ''
}

const form = reactive<Record<string, any>>({ ...defaults, ...props.modelValue })

async function load() {
  if (!props.assetTypeId) return
  loading.value = true
  error.value = ''
  try {
    meta.value = await getRuntimeMetadata(props.assetTypeId)
  } catch (e: unknown) {
    error.value = (e as { message?: string }).message || '加载运行时元数据失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => props.assetTypeId, load)

watch(form, (v) => emit('update:modelValue', { ...v }), { deep: true })
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="error" type="error" :title="error" :closable="false" />
    <template v-else>
      <el-divider content-position="left">基础信息</el-divider>
      <el-form label-width="120px">
        <el-form-item label="资产名称" required>
          <el-input v-model="form.assetName" :disabled="!perm('asset_name').editable" />
        </el-form-item>
        <el-form-item label="资产编号" required>
          <el-input v-model="form.assetNo" :disabled="!perm('asset_no').editable" />
        </el-form-item>
      </el-form>

      <el-divider content-position="left">归属与规格</el-divider>
      <el-form label-width="120px">
        <template v-for="ff in visibleFixedFields" :key="ff.code">
          <el-form-item v-if="ff.control === 'location'" :label="ff.label">
            <LocationTreeSelect v-model="form[ff.formKey]" :disabled="!perm(ff.code).editable" />
          </el-form-item>
          <el-form-item v-else-if="ff.control === 'date'" :label="ff.label">
            <el-date-picker
              v-model="form[ff.formKey]"
              type="date"
              value-format="YYYY-MM-DD"
              :disabled="!perm(ff.code).editable || perm(ff.code).masked"
            />
          </el-form-item>
          <el-form-item v-else :label="ff.label">
            <el-input v-model="form[ff.formKey]" :disabled="!perm(ff.code).editable || perm(ff.code).masked" />
          </el-form-item>
        </template>
      </el-form>

      <el-divider content-position="left">扩展属性</el-divider>
      <el-form label-width="120px">
        <el-form-item
          v-for="f in dynamicFields"
          :key="f.id"
          :label="f.fieldName"
          :required="f.required"
        >
          <FieldValue
            :field="f"
            v-model="form[f.fieldCode]"
            :disabled="!perm(f.fieldCode).editable"
            :masked="perm(f.fieldCode).masked"
          />
        </el-form-item>
        <el-empty v-if="!dynamicFields.length" description="该类型暂无自定义字段" />
      </el-form>
    </template>
  </div>
</template>
