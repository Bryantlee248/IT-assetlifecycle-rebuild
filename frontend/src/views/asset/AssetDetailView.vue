<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock } from '@element-plus/icons-vue'
import { getAsset } from '../../api/asset'
import { getRuntimeMetadata } from '../../api/metadata'
import type { AssetResponse, FieldPermissionView, RuntimeMetadataResponse } from '../../types'
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
</style>
