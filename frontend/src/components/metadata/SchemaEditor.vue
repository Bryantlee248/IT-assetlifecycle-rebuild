<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getFormSchema,
  getListView,
  getSearchSchema,
  getRuntimeMetadata,
  putFormSchema,
  putListView,
  putSearchSchema
} from '../../api/metadata'
import type {
  FieldDefinitionResponse,
  RuntimeMetadataResponse,
  SchemaResponse
} from '../../types'

type Mode = 'form' | 'list' | 'search'
const props = defineProps<{ mode: Mode; assetTypeId: string }>()

const meta = ref<RuntimeMetadataResponse | null>(null)
const current = ref<SchemaResponse | null>(null)
const loading = ref(false)
const saving = ref(false)

// form/list：每个字段的"是否显示"与"排序"
const includeMap = reactive<Record<string, boolean>>({})
const orderMap = reactive<Record<string, number>>({})

// search：标准筛选器开关
const SEARCH_FILTERS = [
  { key: 'keyword', label: '关键字' },
  { key: 'lifecycleStatus', label: '生命周期' },
  { key: 'locationId', label: '位置' },
  { key: 'ownerUserId', label: '使用人' },
  { key: 'responsibleUserId', label: '责任人' },
  { key: 'warranty', label: '保修到期' },
  { key: 'license', label: '许可到期' }
]
const searchEnabled = reactive<Record<string, boolean>>({})

const fields = computed<FieldDefinitionResponse[]>(() => meta.value?.fields ?? [])

function title(): string {
  return props.mode === 'form' ? '表单配置' : props.mode === 'list' ? '列表配置' : '查询配置'
}

async function load() {
  if (!props.assetTypeId) return
  loading.value = true
  try {
    meta.value = await getRuntimeMetadata(props.assetTypeId)
    if (props.mode === 'form') current.value = await getFormSchema(props.assetTypeId)
    else if (props.mode === 'list') current.value = await getListView(props.assetTypeId)
    else current.value = await getSearchSchema(props.assetTypeId)
    seedFromCurrent()
  } finally {
    loading.value = false
  }
}

function seedFromCurrent() {
  const json = current.value?.schemaJson as
    | { columns?: { fieldCode: string; order?: number }[]; enabledFilters?: string[] }
    | null
  // 重置
  fields.value.forEach((f) => {
    includeMap[f.fieldCode] = false
    orderMap[f.fieldCode] = f.sortOrder
  })
  Object.keys(searchEnabled).forEach((k) => (searchEnabled[k] = false))

  if (props.mode === 'search') {
    ;(json?.enabledFilters ?? []).forEach((k) => (searchEnabled[k] = true))
  } else {
    ;(json?.columns ?? []).forEach((c) => {
      includeMap[c.fieldCode] = true
      if (typeof c.order === 'number') orderMap[c.fieldCode] = c.order
    })
    // 无配置时默认全部显示，按 sortOrder 排序
    if (!json?.columns?.length) {
      fields.value.forEach((f) => (includeMap[f.fieldCode] = true))
    }
  }
}

const orderedFields = computed(() =>
  fields.value
    .filter((f) => includeMap[f.fieldCode])
    .sort((a, b) => (orderMap[a.fieldCode] ?? 0) - (orderMap[b.fieldCode] ?? 0))
)

async function save() {
  saving.value = true
  try {
    let payload: Record<string, unknown>
    if (props.mode === 'search') {
      const enabled = SEARCH_FILTERS.filter((f) => searchEnabled[f.key]).map((f) => f.key)
      payload = { enabledFilters: enabled }
      await putSearchSchema(props.assetTypeId, payload)
    } else {
      const columns = orderedFields.value.map((f, i) => ({
        fieldCode: f.fieldCode,
        label: f.fieldName,
        order: i + 1
      }))
      payload = { columns }
      if (props.mode === 'form') await putFormSchema(props.assetTypeId, payload)
      else await putListView(props.assetTypeId, payload)
    }
    ElMessage.success('已保存配置')
    await load()
  } catch {
    // 错误提示由拦截器处理
  } finally {
    saving.value = false
  }
}

onMounted(load)
watch(() => props.assetTypeId, load)
</script>

<template>
  <div v-loading="loading" class="schema-editor">
    <div class="schema-editor__head">
      <span class="schema-editor__title">{{ title() }}</span>
      <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
    </div>

    <!-- 表单 / 列表：字段显隐与排序 -->
    <template v-if="mode !== 'search'">
      <el-table :data="fields" border>
        <el-table-column type="selection" width="50" />
        <el-table-column prop="fieldName" label="字段" min-width="160" />
        <el-table-column prop="fieldCode" label="编码" min-width="180" />
        <el-table-column prop="fieldType" label="类型" min-width="100" />
        <el-table-column label="显示" width="90">
          <template #default="{ row }">
            <el-checkbox v-model="includeMap[row.fieldCode]" />
          </template>
        </el-table-column>
        <el-table-column label="顺序" width="110">
          <template #default="{ row }">
            <el-input-number v-model="orderMap[row.fieldCode]" :min="0" :controls="false" style="width: 70px" />
          </template>
        </el-table-column>
      </el-table>
      <p class="schema-editor__hint">
        勾选"显示"的字段将出现在{{ mode === 'form' ? '新增/编辑表单' : '资产列表' }}中，按"顺序"升序排列。
      </p>
    </template>

    <!-- 查询：标准筛选器开关 -->
    <template v-else>
      <el-checkbox-group>
        <el-checkbox v-for="f in SEARCH_FILTERS" :key="f.key" v-model="searchEnabled[f.key]" :label="f.label" border />
      </el-checkbox-group>
      <p class="schema-editor__hint">勾选的筛选项将出现在资产列表的查询栏中。</p>
    </template>
  </div>
</template>

<style scoped>
.schema-editor {
  background: #fff;
  padding: 16px;
  border-radius: 6px;
}
.schema-editor__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.schema-editor__title {
  font-size: 15px;
  font-weight: 600;
}
.schema-editor__hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
