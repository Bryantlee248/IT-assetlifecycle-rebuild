<script setup lang="ts">
import { computed } from 'vue'
import type { AssetListItem, RuntimeMetadataResponse } from '../../types'

const props = defineProps<{
  meta: RuntimeMetadataResponse | null
  rows: AssetListItem[]
}>()

interface Column {
  code: string
  label: string
}

// 固定物理列已在模板中单独渲染（资产编号/名称/类型/生命周期/状态），
// 此处将其从动态列中剔除，避免重复列（MVP-2 T7c）。
const DEDUP_FIXED = new Set(['asset_no', 'asset_name', 'lifecycle_status', 'status'])

// 兼容 V4 的 listView.columns（{field,title}）与未来的（{fieldCode,label}）。
const columns = computed<Column[]>(() => {
  if (!props.meta) return []
  const fields = props.meta.fields
  const listView = props.meta.listView as { columns?: Array<Record<string, string>> } | null
  const raw = listView?.columns
  if (Array.isArray(raw) && raw.length) {
    return raw
      .map((c) => {
        const code = c['field'] ?? c['fieldCode'] ?? ''
        const label = c['title'] ?? c['label'] ?? fields.find((f) => f.fieldCode === code)?.fieldName ?? code
        return { code, label }
      })
      .filter((c) => c.code && isColumnVisible(c.code) && !DEDUP_FIXED.has(c.code))
  }
  // 回退：使用该类型全部可见字段定义。
  return fields
    .filter(
      (f) =>
        props.meta?.fieldPermissions[f.fieldCode]?.visible !== false &&
        !DEDUP_FIXED.has(f.fieldCode)
    )
    .map((f) => ({ code: f.fieldCode, label: f.fieldName }))
})

// 固定物理列的 snake_case 编码 → 顶层属性（camelCase）映射。
// 注意：AssetListItem 顶层仅含 id/assetNo/assetName/.../fields，热点物理列（brand/model 等）
// 不在顶层，运行时取不到则返回 undefined（由后端按权限决定是否回填，P0-2 已处理）。
const TOP_MAP: Record<string, string> = {
  asset_no: 'assetNo',
  asset_name: 'assetName',
  brand: 'brand',
  model: 'model',
  serial_no: 'serialNo',
  lifecycle_status: 'lifecycleStatus',
  status: 'status',
  responsible_user_id: 'responsibleUserId',
  vendor: 'vendor',
  license_end_date: 'licenseEndDate',
  updated_at: 'updatedAt'
}

function isColumnVisible(code: string): boolean {
  const perm = props.meta?.fieldPermissions?.[code]
  return !perm || perm.visible !== false
}

function cellValue(row: AssetListItem, code: string): unknown {
  if (row.fields && Object.prototype.hasOwnProperty.call(row.fields, code)) {
    return row.fields[code]
  }
  const top = TOP_MAP[code]
  const rec = row as unknown as Record<string, unknown>
  if (top && rec[top] !== undefined) {
    return rec[top]
  }
  return undefined
}

function format(v: unknown): string {
  if (v === null || v === undefined) return ''
  if (typeof v === 'boolean') return v ? '是' : '否'
  return String(v)
}
</script>

<template>
  <el-table :data="rows" border stripe style="width: 100%">
    <el-table-column prop="assetNo" label="资产编号" min-width="140" />
    <el-table-column prop="assetName" label="资产名称" min-width="160" />
    <el-table-column prop="assetTypeName" label="类型" min-width="110" />
    <el-table-column prop="lifecycleStatus" label="生命周期" min-width="100" />
    <el-table-column prop="status" label="状态" min-width="80" />
    <el-table-column
      v-for="col in columns"
      :key="col.code"
      :prop="col.code"
      :label="col.label"
      min-width="140"
    >
      <template #default="{ row }">
        {{ format(cellValue(row as AssetListItem, col.code)) }}
      </template>
    </el-table-column>
    <el-table-column label="操作" fixed="right" min-width="160">
      <template #default="{ row }">
        <slot name="actions" :row="row" />
      </template>
    </el-table-column>
  </el-table>
</template>
