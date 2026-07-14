<script setup lang="ts">
import { computed, onMounted, reactive, watch } from 'vue'
import type { AssetQuery, RuntimeMetadataResponse } from '../../types'
import LocationTreeSelect from './LocationTreeSelect.vue'

const props = defineProps<{
  meta: RuntimeMetadataResponse | null
  modelValue: AssetQuery
}>()
const emit = defineEmits<{ (e: 'update:modelValue', v: AssetQuery): void; (e: 'search'): void }>()

const lifecycleOptions = [
  { label: '规划中', value: 'planned' },
  { label: '在用', value: 'in_use' },
  { label: '闲置', value: 'idle' },
  { label: '维修中', value: 'maintenance' },
  { label: '退役', value: 'retired' }
]

// 由查询配置驱动：兼容 V4 的 searchSchema.filters（{field,control,label}）
// 与未来的 searchSchema.enabledFilters（string[]）。
const filters = computed<Array<Record<string, string>>>(() => {
  const cfg = props.meta?.searchSchema as { filters?: Array<Record<string, string>>; enabledFilters?: string[] } | null
  if (cfg?.filters && Array.isArray(cfg.filters)) {
    return cfg.filters
  }
  if (cfg?.enabledFilters && Array.isArray(cfg.enabledFilters)) {
    return cfg.enabledFilters.map((f) => ({ field: f, control: 'text', label: f }))
  }
  return []
})

const form = reactive<Record<string, any>>({ ...props.modelValue })

onMounted(() => Object.assign(form, props.modelValue))
watch(() => props.meta, () => Object.assign(form, props.modelValue))
watch(
  form,
  (v) => emit('update:modelValue', { ...(v as AssetQuery) }),
  { deep: true }
)

function onSearch() {
  emit('search')
}
function onReset() {
  Object.keys(form).forEach((k) => delete form[k])
  Object.assign(form, {
    assetTypeId: props.modelValue.assetTypeId,
    keyword: '',
    lifecycleStatus: '',
    locationId: '',
    ownerUserId: '',
    ownerOrgId: '',
    responsibleUserId: '',
    warrantyEndFrom: '',
    warrantyEndTo: '',
    licenseEndFrom: '',
    licenseEndTo: ''
  } as AssetQuery)
  emit('update:modelValue', { ...(form as AssetQuery) })
  emit('search')
}

// date_range 类型的字段名 → 对应的起止查询键。
function rangeKeys(field: string): { from: string; to: string } {
  if (field.includes('warranty')) return { from: 'warrantyEndFrom', to: 'warrantyEndTo' }
  if (field.includes('license')) return { from: 'licenseEndFrom', to: 'licenseEndTo' }
  return { from: 'warrantyEndFrom', to: 'warrantyEndTo' }
}
</script>

<template>
  <el-form :inline="true" class="dyn-filter">
    <template v-for="f in filters" :key="f.field">
      <!-- 关键字 -->
      <el-form-item v-if="f.control === 'keyword'" :label="f.label || '关键字'">
        <el-input v-model="form.keyword" placeholder="编号/名称" clearable style="width: 160px" />
      </el-form-item>

      <!-- 生命周期/状态 -->
      <el-form-item v-else-if="f.control === 'status'" :label="f.label || '状态'">
        <el-select v-model="form.lifecycleStatus" placeholder="全部" clearable style="width: 130px">
          <el-option v-for="o in lifecycleOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>

      <!-- 位置 -->
      <el-form-item v-else-if="f.control === 'location'" :label="f.label || '位置'">
        <LocationTreeSelect v-model="form.locationId" />
      </el-form-item>

      <!-- 日期范围 -->
      <el-form-item v-else-if="f.control === 'date_range'" :label="f.label || '日期'">
        <el-date-picker v-model="form[rangeKeys(f.field).from]" type="date" value-format="YYYY-MM-DD" placeholder="起" style="width: 140px" />
        <span class="sep">~</span>
        <el-date-picker v-model="form[rangeKeys(f.field).to]" type="date" value-format="YYYY-MM-DD" placeholder="止" style="width: 140px" />
      </el-form-item>

      <!-- 文本 / 组织 / 枚举：渲染为文本输入（组织/枚举当前以文本录入，后端按需匹配） -->
      <el-form-item v-else :label="f.label || f.field">
        <el-input v-model="form[f.field]" :placeholder="f.label || f.field" clearable style="width: 160px" />
      </el-form-item>
    </template>

    <el-form-item>
      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button @click="onReset">重置</el-button>
    </el-form-item>
  </el-form>
</template>

<style scoped>
.dyn-filter {
  background: #fff;
  padding: 14px 14px 0;
  border-radius: 6px;
  margin-bottom: 14px;
}
.sep {
  margin: 0 6px;
  color: var(--el-text-color-secondary);
}
</style>
