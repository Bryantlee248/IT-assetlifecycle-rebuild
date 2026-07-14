<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createRelation,
  deleteRelation,
  listRelations
} from '../../api/asset'
import type { AssetRelationDto, CreateRelationRequest } from '../../types'

const props = defineProps<{ assetId: string }>()

const relations = ref<AssetRelationDto[]>([])
const loading = ref(false)
const saving = ref(false)

const form = reactive<CreateRelationRequest>({
  targetAssetId: '',
  relationType: '',
  description: ''
})

const relationTypes = ['DEPENDS_ON', 'CONTAINS', 'CONNECTED_TO', 'OWNED_BY', 'LOCATED_IN']

async function load() {
  loading.value = true
  try {
    relations.value = await listRelations(props.assetId)
  } finally {
    loading.value = false
  }
}

async function add() {
  if (!form.targetAssetId || !form.relationType) {
    ElMessage.warning('请填写目标资产与关系类型')
    return
  }
  saving.value = true
  try {
    await createRelation(props.assetId, { ...form })
    ElMessage.success('已添加关系')
    form.targetAssetId = ''
    form.relationType = ''
    form.description = ''
    await load()
  } finally {
    saving.value = false
  }
}

async function remove(id: string) {
  try {
    await deleteRelation(props.assetId, id)
    ElMessage.success('已删除关系')
    await load()
  } catch {
    // 错误提示由拦截器处理
  }
}

onMounted(load)
</script>

<template>
  <div class="relation-tab">
    <el-table :data="relations" border style="margin-bottom: 16px">
      <el-table-column prop="relationType" label="关系类型" min-width="140" />
      <el-table-column prop="targetAssetId" label="目标资产ID" min-width="280" />
      <el-table-column prop="description" label="说明" min-width="160" />
      <el-table-column prop="createdAt" label="创建时间" min-width="180" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button type="danger" link @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-form :inline="true" class="relation-form">
      <el-form-item label="目标资产">
        <el-input v-model="form.targetAssetId" placeholder="目标资产ID" style="width: 280px" />
      </el-form-item>
      <el-form-item label="关系类型">
        <el-select v-model="form.relationType" placeholder="选择" style="width: 160px">
          <el-option v-for="t in relationTypes" :key="t" :label="t" :value="t" />
        </el-select>
      </el-form-item>
      <el-form-item label="说明">
        <el-input v-model="form.description" placeholder="可选" style="width: 200px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="add">添加关系</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped>
.relation-form {
  background: #fff;
  padding: 14px;
  border-radius: 6px;
}
</style>
