<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Message, Postcard, Iphone, Lock } from '@element-plus/icons-vue'
import { register } from '@/api/auth'

const emit = defineEmits(['switch'])

const formRef = ref()
const router = useRouter()
const form = reactive({
  account: '',
  email: '',
  nickname: '',
  phone: '',
  password: '',
  confirmPassword: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请再次输入密码'))
  } else if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  account: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  email: [
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  nickname: [],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' }
  ],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'blur' }]
}

async function handleRegister() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  try {
    await register({
      username: form.account,
      email: form.email,
      nickname: form.nickname,
      phone: form.phone,
      password: form.password
    })
    ElMessage.success('注册成功，请登录')
    emit('switch')
  } catch (e) {
    if (!e?.handled) ElMessage.error(e.message || '注册失败')
  }
}
</script>

<template>
  <h2 class="auth-title">创建您的账户</h2>
  <p class="auth-subtitle">请填写注册信息</p>

  <el-form
    ref="formRef"
    :model="form"
    :rules="rules"
    label-position="top"
    size="large"
    @submit.prevent="handleRegister"
  >
    <el-form-item label="用户名" prop="account">
      <el-input v-model="form.account" placeholder="请输入用户名" :prefix-icon="User" clearable />
    </el-form-item>
    <el-form-item label="邮箱（选填）" prop="email">
      <el-input v-model="form.email" placeholder="请输入邮箱（选填）" :prefix-icon="Message" clearable />
    </el-form-item>
    <el-form-item label="昵称（选填）" prop="nickname">
      <el-input v-model="form.nickname" placeholder="请输入昵称（可选）" :prefix-icon="Postcard" clearable />
    </el-form-item>
    <el-form-item label="手机号" prop="phone">
      <el-input v-model="form.phone" placeholder="请输入手机号" :prefix-icon="Iphone" clearable />
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
    <el-form-item label="确认密码" prop="confirmPassword">
      <el-input
        v-model="form.confirmPassword"
        type="password"
        placeholder="请再次输入密码"
        :prefix-icon="Lock"
        show-password
      />
    </el-form-item>

    <el-button type="primary" class="auth-submit" size="large" @click="handleRegister">
      创建用户
    </el-button>
  </el-form>

  <p class="auth-switch">
    已有账户？<a @click="$emit('switch')">去登录</a>
  </p>
</template>
