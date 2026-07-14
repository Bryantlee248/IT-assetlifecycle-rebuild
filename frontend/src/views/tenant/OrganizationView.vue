<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import StateView from '../../components/StateView.vue'
import { useViewState } from '../../composables/useViewState'
import { useUserStore } from '../../store/user'
import * as orgApi from '../../api/tenant'
import type { CreateOrgRequest, OrgNode, UpdateOrgRequest } from '../../types'

const userStore = useUserStore()
const { status, errorDetail, setLoading, setError, setNoPermission, settle } =
  useViewState()

const tree = ref<OrgNode[]>([])

async function load() {
  if (!userStore.hasPermission('org:list')) {
    setNoPermission()
    return
  }
  setLoading()
  try {
    const data = await orgApi.orgTree()
    tree.value = data
    settle(data.length > 0)
  } catch (e) {
    setError((e as Error).message)
  }
}

// 平铺为父级下拉候选项
const flatOptions = computed<OrgNode[]>(() => {
  const out: OrgNode[] = []
  const walk = (nodes: OrgNode[], prefix: string) => {
    for (const n of nodes) {
      out.push({ ...n, name: prefix + n.name })
      if (n.children?.length) walk(n.children, prefix + n.name + ' / ')
    }
  }
  walk(tree.value, '')
  return out
})

// ===== 新建 / 编辑 =====
const dialogVisible = ref(false)
const saving = ref(false)
const currentId = ref<string | null>(null)
const formRef = ref<FormInstance>()
const isEdit = ref(false)
const form = reactive<CreateOrgRequest & { id?: string }>({
  parentId: null,
  name: '',
  code: '',
  type: '',
  sort: 0
})
const rules: FormRules = {
  name: [{ required: true, message: '请输入组织名称', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入组织编码', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_-]{1,64}$/,
      message: '编码仅含字母数字下划线',
      trigger: 'blur'
    }
  ],
  sort: [{ required: true, message: '请输入排序值', trigger: 'blur' }]
}

function openCreate() {
  isEdit.value = false
  currentId.value = null
  form.parentId = null
  form.name = ''
  form.code = ''
  form.type = ''
  form.sort = 0
  dialogVisible.value = true
}

function openEdit(node: OrgNode) {
  isEdit.value = true
  currentId.value = node.id
  // OrgNode 不返回 parentId，编辑时不修改上级，避免误改父子关系
  form.parentId = null
  form.name = node.name
  form.code = node.code
  form.type = node.type || ''
  form.sort = node.sort
  dialogVisible.value = true
}

async function submit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (isEdit.value && currentId.value) {
        // 编辑时不传 parentId（后端树节点无此字段，避免误改父子关系）
        const payload: UpdateOrgRequest = {
          name: form.name,
          code: form.code,
          type: form.type || undefined,
          sort: form.sort
        }
        await orgApi.updateOrg(currentId.value, payload)
        ElMessage.success('保存成功')
      } else {
        const payload: CreateOrgRequest = {
          parentId: form.parentId,
          name: form.name,
          code: form.code,
          type: form.type || undefined,
          sort: form.sort
        }
        await orgApi.createOrg(payload)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      load()
    } catch {
      // 拦截器提示
    } finally {
      saving.value = false
    }
  })
}

async function onDelete(node: OrgNode) {
  await ElMessageBox.confirm(`确认删除组织「${node.name}」？`, '提示', {
    type: 'warning'
  })
  try {
    await orgApi.deleteOrg(node.id)
    ElMessage.success('删除成功')
    load()
  } catch {
    // 拦截器提示
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <div class="toolbar">
        <span class="toolbar-title">组织树</span>
        <el-button
          v-if="userStore.hasPermission('org:create')"
          type="success"
          @click="openCreate"
        >
          新建组织
        </el-button>
      </div>

      <StateView
        :status="status"
        :error-detail="errorDetail"
        empty-text="暂无组织数据"
        no-permission-text="无组织管理权限"
        @retry="load"
      >
        <el-tree
          :data="tree"
          node-key="id"
          :props="{ children: 'children', label: 'name' }"
          default-expand-all
          class="org-tree"
        >
          <template #default="{ data }">
            <div class="tree-node">
              <span class="tree-label">
                {{ data.name }}
                <small class="tree-code">{{ data.code }}</small>
              </span>
              <el-tag
                v-if="data.status"
                size="small"
                :type="data.status === 'ACTIVE' ? 'success' : 'info'"
              >
                {{ data.status === 'ACTIVE' ? '启用' : '停用' }}
              </el-tag>
              <span class="tree-actions">
                <el-button
                  v-if="userStore.hasPermission('org:update')"
                  size="small"
                  text
                  type="primary"
                  @click.stop="openEdit(data)"
                >
                  编辑
                </el-button>
                <el-button
                  v-if="userStore.hasPermission('org:delete')"
                  size="small"
                  text
                  type="danger"
                  @click.stop="onDelete(data)"
                >
                  删除
                </el-button>
              </span>
            </div>
          </template>
        </el-tree>
      </StateView>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑组织' : '新建组织'"
      width="460px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item v-if="!isEdit" label="上级组织">
          <el-select
            v-model="form.parentId"
            placeholder="无（顶级组织）"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="o in flatOptions"
              :key="o.id"
              :label="o.name"
              :value="o.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="组织名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="组织编码" prop="code">
          <el-input v-model="form.code" />
        </el-form-item>
        <el-form-item label="类型">
          <el-input v-model="form.type" placeholder="如 DEPT / TEAM" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.toolbar-title {
  font-weight: 600;
}
.org-tree {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 8px;
}
.tree-node {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.tree-label {
  font-weight: 500;
}
.tree-code {
  color: #99a0ab;
  margin-left: 6px;
}
.tree-actions {
  margin-left: auto;
}
</style>
