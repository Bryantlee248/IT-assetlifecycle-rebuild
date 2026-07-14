<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import StateView from '../../components/StateView.vue'
import { useViewState } from '../../composables/useViewState'
import { useUserStore } from '../../store/user'
import * as tenantApi from '../../api/tenant'
import type {
  CreateUserRequest,
  RoleResponse,
  UpdateUserRequest,
  UserResponse
} from '../../types'

const userStore = useUserStore()
const { status, errorDetail, setLoading, setError, setNoPermission, settle } =
  useViewState()

const list = ref<UserResponse[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')

async function load() {
  if (!userStore.hasPermission('user:list')) {
    setNoPermission()
    return
  }
  setLoading()
  try {
    const res = await tenantApi.listUsers(page.value, size.value, keyword.value || undefined)
    list.value = res.list
    total.value = res.total
    settle(res.list.length > 0)
  } catch (e) {
    setError((e as Error).message)
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

// ===== 新建 / 编辑 =====
const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const currentId = ref<string | null>(null)
const roles = ref<RoleResponse[]>([])
const formRef = ref<FormInstance>()
const form = reactive<
  CreateUserRequest & { id?: string; confirmPassword?: string }
>({
  username: '',
  password: '',
  displayName: '',
  email: '',
  phone: '',
  roleId: null,
  status: 'ACTIVE'
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 8, message: '初始密码至少 8 位', trigger: 'blur' }
  ],
  displayName: [{ required: true, message: '请输入显示名称', trigger: 'blur' }]
}

async function ensureRoles() {
  if (roles.value.length === 0) {
    roles.value = await tenantApi.listRoles()
  }
}

async function openCreate() {
  isEdit.value = false
  currentId.value = null
  Object.assign(form, {
    username: '',
    password: '',
    displayName: '',
    email: '',
    phone: '',
    roleId: null,
    status: 'ACTIVE'
  })
  await ensureRoles()
  dialogVisible.value = true
}

async function openEdit(row: UserResponse) {
  isEdit.value = true
  currentId.value = row.id
  Object.assign(form, {
    username: row.username,
    password: '',
    displayName: row.displayName || '',
    email: row.email || '',
    phone: row.phone || '',
    roleId: row.roleId,
    status: row.status
  })
  await ensureRoles()
  dialogVisible.value = true
}

async function submit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (isEdit.value && currentId.value) {
        const payload: UpdateUserRequest = {
          displayName: form.displayName,
          email: form.email || undefined,
          phone: form.phone || undefined,
          roleId: form.roleId,
          status: form.status
        }
        await tenantApi.updateUser(currentId.value, payload)
        ElMessage.success('保存成功')
      } else {
        const payload: CreateUserRequest = {
          username: form.username,
          password: form.password,
          displayName: form.displayName,
          email: form.email || undefined,
          phone: form.phone || undefined,
          roleId: form.roleId,
          status: form.status
        }
        await tenantApi.createUser(payload)
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

async function onDelete(row: UserResponse) {
  await ElMessageBox.confirm(`确认删除用户「${row.username}」？`, '提示', {
    type: 'warning'
  })
  try {
    await tenantApi.deleteUser(row.id)
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
        <el-input
          v-model="keyword"
          placeholder="搜索用户名 / 显示名"
          clearable
          style="width: 260px"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button
          v-if="userStore.hasPermission('user:create')"
          type="success"
          @click="openCreate"
        >
          新建用户
        </el-button>
      </div>

      <StateView
        :status="status"
        :error-detail="errorDetail"
        empty-text="暂无用户数据"
        no-permission-text="无用户管理权限"
        @retry="load"
      >
        <el-table :data="list" border stripe>
          <el-table-column prop="username" label="用户名" width="160" />
          <el-table-column prop="displayName" label="显示名" min-width="140" />
          <el-table-column prop="roleName" label="角色" width="160" />
          <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip />
          <el-table-column prop="phone" label="手机号" width="140" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
                {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="userStore.hasPermission('user:update')"
                size="small"
                type="primary"
                text
                @click="openEdit(row as UserResponse)"
              >
                编辑
              </el-button>
              <el-button
                v-if="userStore.hasPermission('user:delete')"
                size="small"
                type="danger"
                text
                @click="onDelete(row as UserResponse)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          class="pager"
          layout="total, prev, pager, next"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="onPageChange"
        />
      </StateView>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑用户' : '新建用户'"
      width="480px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="isEdit" />
        </el-form-item>
        <el-form-item v-if="!isEdit" label="初始密码" prop="password">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="显示名" prop="displayName">
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleId" placeholder="请选择角色" clearable style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
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
  gap: 10px;
  margin-bottom: 16px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
