<script setup lang="ts">
import { computed } from 'vue'
import type { FieldDefinitionResponse } from '../../types'

const props = defineProps<{
  field: FieldDefinitionResponse
  modelValue: unknown
  disabled?: boolean
  masked?: boolean
}>()
const emit = defineEmits<{ (e: 'update:modelValue', v: unknown): void }>()

interface Option {
  label: string
  value: unknown
}

const enumOptions = computed<Option[]>(() => {
  const ds = props.field.dataSource as { options?: unknown[] } | null
  if (ds && Array.isArray(ds.options)) {
    return ds.options.map((o) => {
      if (typeof o === 'string') return { label: o, value: o }
      const obj = o as { label?: string; value?: unknown }
      return { label: obj.label ?? String(obj.value), value: obj.value ?? obj.label }
    })
  }
  return []
})

const isDisabled = computed(() => props.disabled || props.masked)

function onInput(v: unknown) {
  emit('update:modelValue', v === '' || v === undefined ? null : v)
}
</script>

<template>
  <div>
    <el-input
      v-if="field.fieldType === 'string'"
      :model-value="(modelValue as string) ?? ''"
      :disabled="isDisabled"
      @update:model-value="onInput"
    />
    <el-input
      v-else-if="field.fieldType === 'text'"
      type="textarea"
      :rows="3"
      :model-value="(modelValue as string) ?? ''"
      :disabled="isDisabled"
      @update:model-value="onInput"
    />
    <el-input-number
      v-else-if="field.fieldType === 'integer'"
      :model-value="(modelValue as number) ?? null"
      :disabled="isDisabled"
      @update:model-value="onInput"
    />
    <el-input-number
      v-else-if="field.fieldType === 'decimal'"
      :model-value="(modelValue as number) ?? null"
      :step="0.01"
      :disabled="isDisabled"
      @update:model-value="onInput"
    />
    <el-switch
      v-else-if="field.fieldType === 'boolean'"
      :model-value="!!modelValue"
      :disabled="isDisabled"
      @update:model-value="onInput"
    />
    <el-date-picker
      v-else-if="field.fieldType === 'date'"
      type="date"
      value-format="YYYY-MM-DD"
      :model-value="(modelValue as string) ?? ''"
      :disabled="isDisabled"
      @update:model-value="onInput"
    />
    <el-date-picker
      v-else-if="field.fieldType === 'datetime'"
      type="datetime"
      value-format="YYYY-MM-DDTHH:mm:ss"
      :model-value="(modelValue as string) ?? ''"
      :disabled="isDisabled"
      @update:model-value="onInput"
    />
    <el-select
      v-else-if="field.fieldType === 'enum'"
      :model-value="(modelValue as string) ?? ''"
      :disabled="isDisabled"
      clearable
      placeholder="请选择"
      @update:model-value="onInput"
    >
      <el-option
        v-for="o in enumOptions"
        :key="String(o.value)"
        :label="o.label"
        :value="(o.value as string)"
      />
    </el-select>
    <el-input
      v-else
      :model-value="(modelValue as string) ?? ''"
      :disabled="isDisabled"
      @update:model-value="onInput"
    />
    <span v-if="masked" class="masked-hint">已脱敏</span>
  </div>
</template>

<style scoped>
.masked-hint {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-color-warning);
}
</style>
