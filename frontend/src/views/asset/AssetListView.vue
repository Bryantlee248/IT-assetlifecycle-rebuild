<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAssets as apiList, deleteAsset } from '../../api/asset'
import { getRuntimeMetadata } from '../../api/metadata'
import type { AssetListItem, AssetQuery, RuntimeMetadataResponse } from '../../types'
import { useUserStore } from '../../store/user'
import { useViewState } from '../../composables/useViewState'
import StateView from '../../components/StateView.vue'
import DynamicFilter from '../../components/metadata/DynamicFilter.vue'
import DynamicTable from '../../components/metadata/DynamicTable.vue'
import AssetTypeTree from '../../components/metadata/AssetTypeTree.vue'

const router = useRouter()
const userStore = useUserStore()
const view = useViewState()

const selectedTypeId = ref<string | null>(null)
const meta = ref<RuntimeMetadataResponse | null>(null)
const rows = ref<AssetListItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

const canCreate = computed(() => userStore.hasPermission('asset:create'))
const canUpdate = computed(() => userStore.hasPermission('asset:update'))
const canDelete = computed(() => userStore.hasPermission('asset:delete'))

const query = ref<AssetQuery>({ page: 1, size: 20 })

async function loadMeta() {
  meta.value = selectedTypeId.value ? await getRuntimeMetadata(selectedTypeId.value) : null
}

async function load() {
  if (!userStore.hasPermission('asset:view')) {
    view.setNoPermission()
    return
  }
  view.setLoading()
  try {
    query.value.assetTypeId = selectedTypeId.value || undefined
    query.value.page = page.value
    query.value.size = size.value
    const res = await apiList(query.value)
    rows.value = res.list
    total.value = res.total
    view.settle(rows.value.length > 0)
  } catch (e: unknown) {
    view.setError((e as { message?: string }).message || '加载失败')
  }
}

function onSearch() {
  page.value = 1
  load()
}
function onPageChange(p: number) {
  page.value = p
  load()
}

function viewDetail(row: AssetListItem) {
  router.push(`/assets/${row.id}`)
}
function editAsset(row: AssetListItem) {
  router.push(`/assets/${row.id}/edit`)
}
async function removeAsset(row: AssetListItem) {
  try {
    await ElMessageBox.confirm(`确认删除资产「${row.assetName}」？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteAsset(row.id)
    ElMessage.success('已删除')
    await load()
  } catch {
    // 错误提示由拦截器处理
  }
}

watch(selectedTypeId, async () => {
  page.value = 1
  await loadMeta()
  await load()
})

onMounted(async () => {
  await loadMeta()
  await load()
})
</script>

<template>
  <div class="asset-list">
    <div class="toolbar">
      <AssetTypeTree v-model="selectedTypeId" />
      <el-button v-if="canCreate" type="primary" @click="router.push('/assets/new')">
        新增资产
      </el-button>
    </div>

    <StateView
      :status="view.status.value"
      :error-detail="view.errorDetail.value"
      @retry="load"
    >
      <DynamicFilter
        :meta="meta"
        :model-value="query"
        :key="'filter-' + (selectedTypeId || 'all')"
        @update:model-value="(v: AssetQuery) => (query = v)"
        @search="onSearch"
      />

      <el-card shadow="never">
        <DynamicTable :meta="meta" :rows="rows">
          <template #actions="{ row }">
            <el-button link type="primary" @click="viewDetail(row as AssetListItem)">详情</el-button>
            <el-button v-if="canUpdate" link type="primary" @click="editAsset(row as AssetListItem)">编辑</el-button>
            <el-button v-if="canDelete" link type="danger" @click="removeAsset(row as AssetListItem)">删除</el-button>
          </template>
        </DynamicTable>

        <el-pagination
          class="pager"
          layout="total, prev, pager, next"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="onPageChange"
        />
      </el-card>
    </StateView>
  </div>
</template>

<style scoped>
.asset-list {
  width: 100%;
}
.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 14px;
}
.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
</style>
