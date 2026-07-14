<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '../store/user'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 八位及以上（与后端 @Size(min=8) 一致）
const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, message: '新密码至少 8 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_r, value, cb) => {
        if (value !== form.newPassword) cb(new Error('两次输入的密码不一致'))
        else cb()
      },
      trigger: 'blur'
    }
  ]
}

async function onSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await userStore.changePassword(form.oldPassword, form.newPassword)
      ElMessage.success('密码修改成功')
      router.push('/')
    } catch {
      // 错误提示由 request 拦截器统一处理
    } finally {
      loading.value = false
    }
  })
}
</script>

<template>
  <div class="cp-page">
    <div class="cp-card">
      <h2>修改密码</h2>
      <p class="cp-tip">为保障账户安全，首次登录请先修改密码。</p>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @keyup.enter="onSubmit"
      >
        <el-form-item label="原密码" prop="oldPassword">
          <el-input
            v-model="form.oldPassword"
            type="password"
            show-password
            placeholder="请输入原密码"
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="form.newPassword"
            type="password"
            show-password
            placeholder="至少 8 位"
          />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            placeholder="再次输入新密码"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            class="cp-btn"
            :loading="loading"
            @click="onSubmit"
          >
            确认修改
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.cp-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0052d9 0%, #003a99 100%);
}
.cp-card {
  width: 400px;
  background: #fff;
  border-radius: 10px;
  padding: 32px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.18);
}
.cp-card h2 {
  margin: 0 0 6px;
  font-size: 20px;
}
.cp-tip {
  margin: 0 0 20px;
  font-size: 13px;
  color: #8a9099;
}
.cp-btn {
  width: 100%;
}
</style>
