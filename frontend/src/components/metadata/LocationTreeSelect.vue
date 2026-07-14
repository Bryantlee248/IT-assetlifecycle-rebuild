<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getLocationTree } from '../../api/metadata'
import type { LocationNode } from '../../types'

defineProps<{ modelValue: string | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: string | null): void }>()

const tree = ref<LocationNode[]>([])
const loading = ref(false)

interface Option {
  value: string
  label: string
  children: Option[]
}

function toOptions(nodes: LocationNode[]): Option[] {
  return nodes.map((n) => ({
    value: n.id,
    label: n.name,
    children: toOptions(n.children)
  }))
}

async function load() {
  loading.value = true
  try {
    tree.value = await getLocationTree()
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
    placeholder="选择位置"
    style="width: 100%"
    @update:model-value="(v: string | null) => emit('update:modelValue', v ?? null)"
  />
</template>
