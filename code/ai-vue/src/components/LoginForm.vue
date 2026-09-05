<script setup>
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { login } from '@/api/auth'

defineEmits(['switch'])

const formRef = ref()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
// 预览版 / mock 模式：自动填入内置账号，方便直接体验
const isPreview = import.meta.env.VITE_PREVIEW === 'true' || import.meta.env.VITE_API_MODE === 'mock'
const form = reactive({
  account: isPreview ? 'demo' : '',
  password: isPreview ? '123456' : ''
})

const rules = {
  account: [{ required: true, message: '请输入手机号或用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' }
  ]
}

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const data = await login({ username: form.account, password: form.password })
    authStore.setLogin({
      token: data.token,
      userInfo: {
        id: data.userId,
        username: data.username,
        nickname: data.nickname,
        avatar: data.avatar || '',
        role: data.role
      }
    })
    ElMessage.success('登录成功')
    const role = data.role
    const fallback = role === 'admin' ? '/back/index' : '/home'
    const raw = route.query.redirect
    // 仅允许站内路径，防止开放重定向
    const redirect =
      typeof raw === 'string' && raw.startsWith('/') && !raw.startsWith('//') ? raw : fallback
    router.replace(redirect)
  } catch (e) {
    if (!e?.handled) ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <h2 class="auth-title">登录您的账户</h2>
  <p class="auth-subtitle">请输入您的登录信息</p>
  <p v-if="isPreview" class="auth-preview-tip">
    预览账号 demo / 123456 已自动填入（普通用户），点击下方「登录账户」即可进入
  </p>

  <el-form
    ref="formRef"
    :model="form"
    :rules="rules"
    label-position="top"
    size="large"
    @submit.prevent="handleLogin"
  >
    <el-form-item label="手机号或用户名" prop="account">
      <el-input v-model="form.account" placeholder="请输入手机号或用户名" :prefix-icon="User" clearable />
    </el-form-item>
    <el-form-item label="密码" prop="password">
      <el-input
        v-model="form.password"
        type="password"
        placeholder="请输入密码"
        :prefix-icon="Lock"
        show-password
      />
    </el-form-item>

    <el-button type="primary" class="auth-submit" size="large" :loading="loading" @click="handleLogin">
      登录账户
    </el-button>
  </el-form>

  <p class="auth-switch">
    还没有账户？<a @click="$emit('switch')">去注册</a>
  </p>
</template>

<style scoped>
.auth-preview-tip {
  margin: -14px 0 18px;
  font-size: 12px;
  color: #2f6fdb;
  background: rgba(47, 111, 219, 0.08);
  border-radius: 10px;
  padding: 8px 12px;
  line-height: 1.6;
}
</style>
