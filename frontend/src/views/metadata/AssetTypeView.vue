<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  createAssetType,
  createField,
  getAssetTypeTree,
  listFields,
  setAssetTypeStatus,
  setFieldStatus,
  updateAssetType,
  updateField
} from '../../api/metadata'
import type {
  AssetTypeNode,
  AssetTypeResponse,
  CreateAssetTypeRequest,
  FieldDefinitionResponse,
  CreateFieldRequest,
  UpdateFieldRequest
} from '../../types'
import SchemaEditor from '../../components/metadata/SchemaEditor.vue'

const route = useRoute()
const activeTab = ref<string>((route.query.tab as string) || 'types')

const tree = ref<AssetTypeNode[]>([])
const selectedTypeId = ref<string | null>(null)

const loadingTree = ref(false)
async function loadTree() {
  loadingTree.value = true
  try {
    tree.value = await getAssetTypeTree()
    if (!selectedTypeId.value) {
      const first = flatten(tree.value)[0]
      selectedTypeId.value = first?.id ?? null
    }
  } finally {
    loadingTree.value = false
  }
}

interface FlatType {
  id: string
  typeCode: string
  typeName: string
  assetKind: string
  enabled: boolean
  depth: number
}
function flatten(nodes: AssetTypeNode[], depth = 0): FlatType[] {
  return nodes.flatMap((n) => [
    { id: n.id, typeCode: n.typeCode, typeName: n.typeName, assetKind: n.assetKind, enabled: n.enabled, depth },
    ...flatten(n.children, depth + 1)
  ])
}
const flatTypes = computed(() => flatten(tree.value))

// ===== 资产类型 增改 =====
const typeDialog = ref(false)
const typeEditing = ref<AssetTypeResponse | null>(null)
const typeForm = ref<CreateAssetTypeRequest>({
  parentId: null,
  typeCode: '',
  typeName: '',
  assetKind: 'DEVICE',
  icon: '',
  enabled: true,
  sortOrder: 0
})
async function openTypeCreate() {
  typeEditing.value = null
  typeForm.value = { parentId: null, typeCode: '', typeName: '', assetKind: 'DEVICE', icon: '', enabled: true, sortOrder: 0 }
  typeDialog.value = true
}
async function openTypeEdit(row: FlatType) {
  const detail = await getAssetTypeTree() // 树已含基础信息；详情由父级提供，这里用扁平数据近似
  void detail
  typeEditing.value = null
  // 用扁平数据填充（缺少个别字段时以默认值补全）
  typeForm.value = {
    parentId: null,
    typeCode: row.typeCode,
    typeName: row.typeName,
    assetKind: row.assetKind,
    icon: '',
    enabled: row.enabled,
    sortOrder: 0
  }
  typeDialog.value = true
}
async function saveType() {
  try {
    if (typeEditing.value) {
      await updateAssetType(typeEditing.value.id, typeForm.value)
    } else {
      await createAssetType(typeForm.value)
    }
    ElMessage.success('已保存')
    typeDialog.value = false
    await loadTree()
  } catch {
    // 错误提示由拦截器处理
  }
}
async function toggleType(row: FlatType) {
  try {
    await setAssetTypeStatus(row.id, !row.enabled)
    await loadTree()
  } catch {
    // 错误提示由拦截器处理
  }
}

// ===== 字段定义 =====
const fields = ref<FieldDefinitionResponse[]>([])
const loadingFields = ref(false)
async function loadFields() {
  if (!selectedTypeId.value) {
    fields.value = []
    return
  }
  loadingFields.value = true
  try {
    fields.value = await listFields(selectedTypeId.value)
  } finally {
    loadingFields.value = false
  }
}

const fieldDialog = ref(false)
const fieldEditing = ref<FieldDefinitionResponse | null>(null)
const fieldForm = ref<CreateFieldRequest>(emptyFieldForm())
function emptyFieldForm(): CreateFieldRequest {
  return {
    fieldCode: '',
    fieldName: '',
    fieldType: 'string',
    storageType: 'jsonb',
    physicalColumn: null,
    required: false,
    uniqueScope: 'none',
    visible: true,
    editable: true,
    sensitive: false,
    encrypted: false,
    maskRule: null,
    sortOrder: 0,
    searchable: false,
    sortable: false
  }
}
function openFieldCreate() {
  if (!selectedTypeId.value) {
    ElMessage.warning('请先在左侧选择资产类型')
    return
  }
  fieldEditing.value = null
  fieldForm.value = emptyFieldForm()
  fieldDialog.value = true
}
function openFieldEdit(row: FieldDefinitionResponse) {
  fieldEditing.value = row
  fieldForm.value = {
    fieldCode: row.fieldCode,
    fieldName: row.fieldName,
    fieldType: row.fieldType,
    storageType: row.storageType,
    physicalColumn: row.physicalColumn,
    required: row.required,
    uniqueScope: row.uniqueScope,
    visible: row.visible,
    editable: row.editable,
    sensitive: row.sensitive,
    encrypted: row.encrypted,
    maskRule: row.maskRule,
    sortOrder: row.sortOrder,
    searchable: row.searchable,
    sortable: row.sortable
  }
  fieldDialog.value = true
}
async function saveField() {
  if (!selectedTypeId.value) return
  try {
    if (fieldEditing.value) {
      const patch: UpdateFieldRequest = { ...fieldForm.value }
      await updateField(fieldEditing.value.id, patch)
    } else {
      await createField(selectedTypeId.value, fieldForm.value)
    }
    ElMessage.success('已保存')
    fieldDialog.value = false
    await loadFields()
  } catch {
    // 错误提示由拦截器处理
  }
}
async function toggleField(row: FieldDefinitionResponse) {
  try {
    await setFieldStatus(row.id, !row.visible)
    await loadFields()
  } catch {
    // 错误提示由拦截器处理
  }
}

watch(selectedTypeId, () => {
  if (activeTab.value === 'fields') loadFields()
})
watch(activeTab, (t) => {
  if (t === 'fields') loadFields()
})

onMounted(() => {
  loadTree()
})
</script>

<template>
  <div class="at-view">
    <el-row :gutter="16">
      <el-col :span="6">
        <el-card shadow="never" class="at-tree">
          <template #header>资产类型</template>
          <el-tree
            v-loading="loadingTree"
            :data="tree"
            node-key="id"
            :props="{ label: 'typeName', children: 'children' }"
            highlight-current
            @current-change="(n: AssetTypeNode | null) => (selectedTypeId = n?.id ?? null)"
          />
        </el-card>
      </el-col>

      <el-col :span="18">
        <el-tabs v-model="activeTab">
          <!-- 资产类型 -->
          <el-tab-pane label="资产类型" name="types">
            <div class="toolbar">
              <el-button type="primary" @click="openTypeCreate">新增类型</el-button>
            </div>
            <el-table :data="flatTypes" border>
              <el-table-column label="名称" min-width="200">
                <template #default="{ row }">
                  <span :style="{ paddingLeft: row.depth * 16 + 'px' }">{{ row.typeName }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="typeCode" label="编码" min-width="140" />
              <el-table-column prop="assetKind" label="大类" min-width="100" />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="160">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openTypeEdit(row as FlatType)">编辑</el-button>
                  <el-button link :type="row.enabled ? 'warning' : 'success'" @click="toggleType(row as FlatType)">
                    {{ row.enabled ? '停用' : '启用' }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <!-- 字段定义 -->
          <el-tab-pane label="字段定义" name="fields">
            <div class="toolbar">
              <span class="hint" v-if="!selectedTypeId">请先在左侧选择资产类型</span>
              <el-button type="primary" :disabled="!selectedTypeId" @click="openFieldCreate">
                新增字段
              </el-button>
            </div>
            <el-table v-loading="loadingFields" :data="fields" border>
              <el-table-column prop="fieldName" label="字段名" min-width="140" />
              <el-table-column prop="fieldCode" label="编码" min-width="180" />
              <el-table-column prop="fieldType" label="类型" width="90" />
              <el-table-column prop="storageType" label="存储" width="90" />
              <el-table-column prop="uniqueScope" label="唯一范围" width="100" />
              <el-table-column label="可见/可编辑" width="110">
                <template #default="{ row }">
                  {{ row.visible ? '可见' : '隐藏' }}/{{ row.editable ? '可编' : '只读' }}
                </template>
              </el-table-column>
              <el-table-column label="敏感/加密" width="100">
                <template #default="{ row }">
                  {{ row.sensitive ? '敏感' : '' }}{{ row.encrypted ? '加密' : '' }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="160">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openFieldEdit(row as FieldDefinitionResponse)">编辑</el-button>
                  <el-button link :type="row.visible ? 'warning' : 'success'" @click="toggleField(row as FieldDefinitionResponse)">
                    {{ row.visible ? '停用' : '启用' }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <!-- 表单 / 列表 / 查询 配置 -->
          <el-tab-pane label="表单配置" name="form">
            <SchemaEditor v-if="selectedTypeId" mode="form" :asset-type-id="selectedTypeId" :key="'form-' + selectedTypeId" />
            <el-empty v-else description="请先在左侧选择资产类型" />
          </el-tab-pane>
          <el-tab-pane label="列表配置" name="list">
            <SchemaEditor v-if="selectedTypeId" mode="list" :asset-type-id="selectedTypeId" :key="'list-' + selectedTypeId" />
            <el-empty v-else description="请先在左侧选择资产类型" />
          </el-tab-pane>
          <el-tab-pane label="查询配置" name="search">
            <SchemaEditor v-if="selectedTypeId" mode="search" :asset-type-id="selectedTypeId" :key="'search-' + selectedTypeId" />
            <el-empty v-else description="请先在左侧选择资产类型" />
          </el-tab-pane>
        </el-tabs>
      </el-col>
    </el-row>

    <!-- 资产类型对话框 -->
    <el-dialog v-model="typeDialog" :title="typeEditing ? '编辑资产类型' : '新增资产类型'" width="520px">
      <el-form :model="typeForm" label-width="100px">
        <el-form-item label="类型编码">
          <el-input v-model="typeForm.typeCode" :disabled="!!typeEditing" />
        </el-form-item>
        <el-form-item label="类型名称">
          <el-input v-model="typeForm.typeName" />
        </el-form-item>
        <el-form-item label="资产大类">
          <el-select v-model="typeForm.assetKind">
            <el-option label="设备" value="DEVICE" />
            <el-option label="软件" value="SOFTWARE" />
            <el-option label="网络" value="NETWORK" />
            <el-option label="安全" value="SECURITY" />
          </el-select>
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="typeForm.icon" placeholder="如 Server / Monitor" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="typeForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="typeForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialog = false">取消</el-button>
        <el-button type="primary" @click="saveType">保存</el-button>
      </template>
    </el-dialog>

    <!-- 字段定义对话框 -->
    <el-dialog v-model="fieldDialog" :title="fieldEditing ? '编辑字段' : '新增字段'" width="560px">
      <el-form :model="fieldForm" label-width="100px">
        <el-form-item label="字段编码">
          <el-input v-model="fieldForm.fieldCode" :disabled="!!fieldEditing" placeholder="如 cpu_cores" />
        </el-form-item>
        <el-form-item label="字段名称">
          <el-input v-model="fieldForm.fieldName" />
        </el-form-item>
        <el-form-item label="字段类型">
          <el-select v-model="fieldForm.fieldType">
            <el-option label="字符串" value="string" />
            <el-option label="文本" value="text" />
            <el-option label="整数" value="integer" />
            <el-option label="小数" value="decimal" />
            <el-option label="布尔" value="boolean" />
            <el-option label="日期" value="date" />
            <el-option label="时间" value="datetime" />
            <el-option label="枚举" value="enum" />
          </el-select>
        </el-form-item>
        <el-form-item label="存储方式">
          <el-select v-model="fieldForm.storageType">
            <el-option label="JSONB 扩展" value="jsonb" />
            <el-option label="物理列" value="physical" />
            <el-option label="加密(JSONB)" value="encrypted" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="fieldForm.storageType === 'physical'" label="物理列">
          <el-input v-model="fieldForm.physicalColumn" placeholder="须为热点物理列" />
        </el-form-item>
        <el-form-item label="唯一范围">
          <el-select v-model="fieldForm.uniqueScope">
            <el-option label="无" value="none" />
            <el-option label="租户内唯一" value="tenant" />
            <el-option label="类型内唯一" value="asset_type" />
          </el-select>
        </el-form-item>
        <el-form-item label="脱敏规则">
          <el-select v-model="fieldForm.maskRule" clearable>
            <el-option label="不脱敏" value="" />
            <el-option label="保留后4位" value="last4" />
            <el-option label="保留前4位" value="first4" />
            <el-option label="中间掩码" value="middle" />
          </el-select>
        </el-form-item>
        <el-form-item label="必填/排序">
          <el-switch v-model="fieldForm.required" />&nbsp;
          <el-input-number v-model="fieldForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="开关">
          <el-checkbox v-model="fieldForm.visible">可见</el-checkbox>
          <el-checkbox v-model="fieldForm.editable">可编辑</el-checkbox>
          <el-checkbox v-model="fieldForm.sensitive">敏感</el-checkbox>
          <el-checkbox v-model="fieldForm.encrypted">加密</el-checkbox>
          <el-checkbox v-model="fieldForm.searchable">可搜索</el-checkbox>
          <el-checkbox v-model="fieldForm.sortable">可排序</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fieldDialog = false">取消</el-button>
        <el-button type="primary" @click="saveField">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.at-view {
  width: 100%;
}
.at-tree {
  min-height: 420px;
}
.toolbar {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
