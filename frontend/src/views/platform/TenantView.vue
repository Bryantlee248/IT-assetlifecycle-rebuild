<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import StateView from '../../components/StateView.vue'
import { useViewState } from '../../composables/useViewState'
import { useUserStore } from '../../store/user'
import * as tenantApi from '../../api/platform'
import type { CreateTenantRequest, TenantResponse } from '../../types'

const userStore = useUserStore()
const { status, errorDetail, setLoading, setError, setNoPermission, settle } =
  useViewState()

const list = ref<TenantResponse[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')

async function load() {
  if (!userStore.hasPermission('tenant:list')) {
    setNoPermission()
    return
  }
  setLoading()
  try {
    const res = await tenantApi.listTenants(page.value, size.value, keyword.value || undefined)
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

// ===== 新建 =====
const dialogVisible = ref(false)
const creating = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<CreateTenantRequest>({ name: '', code: '', description: '' })
const rules: FormRules = {
  name: [{ required: true, message: '请输入租户名称', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入租户编码', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_-]{2,32}$/,
      message: '编码仅含字母数字下划线，2-32 位',
      trigger: 'blur'
    }
  ]
}

function openCreate() {
  form.name = ''
  form.code = ''
  form.description = ''
  dialogVisible.value = true
}

async function submitCreate() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    creating.value = true
    try {
      await tenantApi.createTenant({ ...form })
      ElMessage.success('创建成功')
      dialogVisible.value = false
      load()
    } catch {
      // 拦截器提示
    } finally {
      creating.value = false
    }
  })
}

async function toggleStatus(row: TenantResponse) {
  const enable = row.status !== 'ACTIVE'
  try {
    await tenantApi.updateTenantStatus(row.id, enable)
    ElMessage.success(enable ? '已启用' : '已停用')
    load()
  } catch {
    // 拦截器提示
  }
}

async function confirmDisable(row: TenantResponse) {
  if (row.status !== 'ACTIVE') return
  await ElMessageBox.confirm(`确认停用租户「${row.name}」？`, '提示', {
    type: 'warning'
  })
  await toggleStatus(row)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索租户名称 / 编码"
          clearable
          style="width: 260px"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button
          v-if="userStore.hasPermission('tenant:create')"
          type="success"
          @click="openCreate"
        >
          新建租户
        </el-button>
      </div>

      <StateView
        :status="status"
        :error-detail="errorDetail"
        empty-text="暂无租户数据"
        no-permission-text="无租户管理权限"
        @retry="load"
      >
        <el-table :data="list" border stripe>
          <el-table-column prop="name" label="租户名称" min-width="160" />
          <el-table-column prop="code" label="编码" width="160" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
                {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
          <el-table-column label="创建时间" width="180">
            <template #default="{ row }">
              {{ row.createdAt ? row.createdAt.replace('T', ' ').slice(0, 19) : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="userStore.hasPermission('tenant:disable')"
                size="small"
                :type="row.status === 'ACTIVE' ? 'danger' : 'success'"
                @click="confirmDisable(row as TenantResponse)"
              >
                {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
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

    <el-dialog v-model="dialogVisible" title="新建租户" width="460px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="租户名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入租户名称" />
        </el-form-item>
        <el-form-item label="租户编码" prop="code">
          <el-input v-model="form.code" placeholder="字母数字下划线，2-32 位" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">
          确定
        </el-button>
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
