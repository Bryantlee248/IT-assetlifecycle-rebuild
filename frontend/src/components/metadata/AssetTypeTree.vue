<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getAssetTypeTree } from '../../api/metadata'
import type { AssetTypeNode } from '../../types'

defineProps<{ modelValue: string | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: string | null): void }>()

const tree = ref<AssetTypeNode[]>([])
const loading = ref(false)

interface Option {
  value: string
  label: string
  children: Option[]
}
function toOptions(nodes: AssetTypeNode[]): Option[] {
  return nodes.map((n) => ({
    value: n.id,
    label: `${n.typeName}${n.enabled ? '' : '（停用）'}`,
    children: toOptions(n.children)
  }))
}

async function load() {
  loading.value = true
  try {
    tree.value = await getAssetTypeTree()
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<template>
  <el-tree-select
    :model-value="modelValue"
    :data="toOptions(tree)"
    :loading="loading"
    node-key="value"
    :props="{ label: 'label', children: 'children' }"
    check-strictly
    clearable
    placeholder="选择资产类型"
    style="width: 100%"
    @update:model-value="(v: string | null) => emit('update:modelValue', v ?? null)"
  />
</template>
