<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import StateView from '../../components/StateView.vue'
import { useViewState } from '../../composables/useViewState'
import { useUserStore } from '../../store/user'
import * as roleApi from '../../api/tenant'
import { PERMISSION_CATALOG, PERMISSION_GROUPS } from '../../constants/permissions'
import type {
  CreateRoleRequest,
  RoleResponse,
  UpdateRoleRequest
} from '../../types'

const userStore = useUserStore()
const { status, errorDetail, setLoading, setError, setNoPermission, settle } =
  useViewState()

const list = ref<RoleResponse[]>([])

async function load() {
  if (!userStore.hasPermission('role:list')) {
    setNoPermission()
    return
  }
  setLoading()
  try {
    const data = await roleApi.listRoles()
    list.value = data
    settle(data.length > 0)
  } catch (e) {
    setError((e as Error).message)
  }
}

// ===== 新建 / 编辑角色 =====
const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const currentId = ref<string | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<CreateRoleRequest & { id?: string }>({
  code: '',
  name: '',
  description: ''
})
const rules: FormRules = {
  code: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_-]{2,48}$/,
      message: '编码仅含字母数字下划线，2-48 位',
      trigger: 'blur'
    }
  ],
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

function openCreate() {
  isEdit.value = false
  currentId.value = null
  form.code = ''
  form.name = ''
  form.description = ''
  dialogVisible.value = true
}

function openEdit(row: RoleResponse) {
  isEdit.value = true
  currentId.value = row.id
  form.code = row.code
  form.name = row.name
  form.description = row.description || ''
  dialogVisible.value = true
}

async function submit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (isEdit.value && currentId.value) {
        const payload: UpdateRoleRequest = {
          name: form.name,
          description: form.description || undefined
        }
        await roleApi.updateRole(currentId.value, payload)
        ElMessage.success('保存成功')
      } else {
        const payload: CreateRoleRequest = {
          code: form.code,
          name: form.name,
          description: form.description || undefined
        }
        await roleApi.createRole(payload)
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

async function onDelete(row: RoleResponse) {
  await ElMessageBox.confirm(`确认删除角色「${row.name}」？`, '提示', {
    type: 'warning'
  })
  try {
    await roleApi.deleteRole(row.id)
    ElMessage.success('删除成功')
    load()
  } catch {
    // 拦截器提示
  }
}

// ===== 角色权限设置 =====
const permDialogVisible = ref(false)
const permSaving = ref(false)
const permRoleId = ref<string | null>(null)
const permRoleName = ref('')
const selectedPerms = ref<string[]>([])

async function openPerms(row: RoleResponse) {
  permRoleId.value = row.id
  permRoleName.value = row.name
  selectedPerms.value = []
  try {
    const res = await roleApi.getRolePermissions(row.id)
    selectedPerms.value = res.permissions || []
  } catch {
    // 拦截器提示
  }
  permDialogVisible.value = true
}

async function savePerms() {
  if (!permRoleId.value) return
  permSaving.value = true
  try {
    await roleApi.setRolePermissions(permRoleId.value, selectedPerms.value)
    ElMessage.success('权限已保存')
    permDialogVisible.value = false
  } catch {
    // 拦截器提示
  } finally {
    permSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <div class="toolbar">
        <span class="toolbar-title">角色列表</span>
        <el-button
          v-if="userStore.hasPermission('role:create')"
          type="success"
          @click="openCreate"
        >
          新建角色
        </el-button>
      </div>

      <StateView
        :status="status"
        :error-detail="errorDetail"
        empty-text="暂无角色数据"
        no-permission-text="无角色管理权限"
        @retry="load"
      >
        <el-table :data="list" border stripe>
          <el-table-column prop="name" label="角色名称" min-width="160" />
          <el-table-column prop="code" label="编码" width="180" />
          <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
          <el-table-column label="系统角色" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.system" type="warning">系统</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="userStore.hasPermission('role:assign')"
                size="small"
                type="primary"
                text
                @click="openPerms(row as RoleResponse)"
              >
                权限
              </el-button>
              <el-button
                v-if="userStore.hasPermission('role:update')"
                size="small"
                type="primary"
                text
                @click="openEdit(row as RoleResponse)"
              >
                编辑
              </el-button>
              <el-button
                v-if="userStore.hasPermission('role:delete')"
                size="small"
                type="danger"
                text
                @click="onDelete(row as RoleResponse)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </StateView>
    </el-card>

    <!-- 角色新建/编辑 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑角色' : '新建角色'"
      width="460px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色编码" prop="code">
          <el-input v-model="form.code" :disabled="isEdit" placeholder="2-48 位字母数字下划线" />
        </el-form-item>
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 角色权限设置 -->
    <el-dialog
      v-model="permDialogVisible"
      :title="`设置权限 - ${permRoleName}`"
      width="560px"
    >
      <el-form label-width="80px">
        <el-form-item label="权限码">
          <el-select
            v-model="selectedPerms"
            multiple
            filterable
            default-first-option
            placeholder="勾选权限码"
            style="width: 100%"
          >
            <el-option-group
              v-for="grp in PERMISSION_GROUPS"
              :key="grp"
              :label="grp"
            >
              <el-option
                v-for="p in PERMISSION_CATALOG.filter((x) => x.group === grp)"
                :key="p.code"
                :label="`${p.label}（${p.code}）`"
                :value="p.code"
              />
            </el-option-group>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="permSaving" @click="savePerms">
          保存
        </el-button>
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
</style>
