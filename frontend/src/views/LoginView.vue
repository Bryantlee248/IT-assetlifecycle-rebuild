<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '../store/user'
import { login } from '../api/auth'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({
  username: '',
  password: ''
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function onSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const resp = await login({ username: form.username, password: form.password })
      userStore.applyLogin(resp)
      ElMessage.success('登录成功')
      // 若需强制改密，守卫会重定向到 /change-password；否则进主页
      router.push('/')
    } catch {
      // 错误提示由 request 拦截器统一处理（后端 message）
    } finally {
      loading.value = false
    }
  })
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <div class="login-logo">ITAM</div>
        <h2>IT 资产管理系统</h2>
        <p class="login-sub">企业级 IT 资产全生命周期管理平台</p>
      </div>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        @keyup.enter="onSubmit"
      >
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            class="login-btn"
            :loading="loading"
            @click="onSubmit"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0052d9 0%, #003a99 100%);
}
.login-card {
  width: 380px;
  background: #fff;
  border-radius: 10px;
  padding: 36px 32px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.18);
}
.login-header {
  text-align: center;
  margin-bottom: 24px;
}
.login-logo {
  width: 56px;
  height: 56px;
  line-height: 56px;
  margin: 0 auto 12px;
  border-radius: 12px;
  background: var(--itam-primary);
  color: #fff;
  font-weight: 700;
  letter-spacing: 1px;
}
.login-header h2 {
  margin: 0 0 6px;
  font-size: 20px;
  color: #1f2329;
}
.login-sub {
  margin: 0;
  font-size: 13px;
  color: #8a9099;
}
.login-btn {
  width: 100%;
}
</style>
