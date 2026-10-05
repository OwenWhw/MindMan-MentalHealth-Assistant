<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { updateProfile, changePassword } from '@/api/auth'
import { uploadFile } from '@/api/file'
import { getAvailableModels } from '@/api/consult'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue'])
const authStore = useAuthStore()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})
const activeTab = ref('profile')

// ===== 个人资料 =====
const profileForm = reactive({ nickname: '', phone: '', email: '', avatar: '' })
const savingProfile = ref(false)

function syncProfile() {
  const u = authStore.userInfo || {}
  profileForm.nickname = u.nickname || ''
  profileForm.phone = u.phone || ''
  profileForm.email = u.email || ''
  profileForm.avatar = u.avatar || ''
}

watch(
  () => props.modelValue,
  (v) => {
    if (v) syncProfile()
  }
)

async function onAvatarChange(file) {
  try {
    const data = await uploadFile(file.raw)
    profileForm.avatar = data.url
    ElMessage.success('头像已上传，记得保存资料')
  } catch (e) {
    if (!e?.handled) ElMessage.error(e.message || '头像上传失败')
  }
  return false
}

async function saveProfile() {
  // 手机号必填 + 格式校验
  const phone = (profileForm.phone || '').trim()
  if (!phone) {
    ElMessage.warning('请填写手机号')
    return
  }
  if (!/^1[3-9]\d{9}$/.test(phone)) {
    ElMessage.warning('手机号格式不正确')
    return
  }
  savingProfile.value = true
  try {
    const data = await updateProfile({
      nickname: profileForm.nickname,
      phone,
      email: profileForm.email,
      avatar: profileForm.avatar
    })
    authStore.setLogin({
      token: authStore.token,
      userInfo: { ...authStore.userInfo, ...data }
    })
    ElMessage.success('资料已更新')
  } catch (e) {
    if (!e?.handled) ElMessage.error(e.message || '保存失败，请稍后重试')
  } finally {
    savingProfile.value = false
  }
}

// ===== 账号安全 =====
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const savingPwd = ref(false)

async function savePassword() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    ElMessage.warning('请填写完整密码信息')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  savingPwd.value = true
  try {
    await changePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    ElMessage.success('密码已修改，下次登录请使用新密码')
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
  } catch (e) {
    if (!e?.handled) ElMessage.error(e.message || '修改失败，请稍后重试')
  } finally {
    savingPwd.value = false
  }
}

// ===== 系统设置 =====
const models = ref({})
const defaultModel = ref(localStorage.getItem('mha_model') || 'qwen3.8-max')
const autoAnalysis = ref(localStorage.getItem('mha_analysis_auto_open') === '1')

onMounted(async () => {
  try {
    models.value = await getAvailableModels()
  } catch {
    models.value = { 'Qwen3.8-Max（旗舰）': 'qwen3.8-max' }
  }
})

function saveModel(id) {
  defaultModel.value = id
  localStorage.setItem('mha_model', id)
  ElMessage.success('默认模型已更新')
}

function toggleAnalysis() {
  autoAnalysis.value = !autoAnalysis.value
  localStorage.setItem('mha_analysis_auto_open', autoAnalysis.value ? '1' : '0')
  ElMessage.success(autoAnalysis.value ? '已开启自动展开情绪分析' : '已关闭自动展开情绪分析')
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="设置"
    width="480px"
    class="settings-dialog"
    :close-on-click-modal="false"
    append-to-body
  >
    <el-tabs v-model="activeTab">
      <el-tab-pane label="个人资料" name="profile">
        <div class="setting-body">
          <div class="avatar-row">
            <el-avatar :size="64" :src="profileForm.avatar" class="set-avatar">
              {{ profileForm.nickname?.charAt(0) || '用' }}
            </el-avatar>
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              :on-change="onAvatarChange"
            >
              <el-button size="small" round>更换头像</el-button>
            </el-upload>
          </div>

          <el-form label-position="top">
            <el-form-item label="昵称">
              <el-input v-model="profileForm.nickname" placeholder="请输入昵称" maxlength="20" />
            </el-form-item>
            <el-form-item label="手机号" required>
              <el-input v-model="profileForm.phone" placeholder="请输入手机号" />
            </el-form-item>
            <el-form-item label="邮箱（选填）">
              <el-input v-model="profileForm.email" placeholder="请输入邮箱（选填）" />
            </el-form-item>
          </el-form>

          <div class="save-row">
            <el-button type="primary" :loading="savingProfile" @click="saveProfile">保存资料</el-button>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="账号安全" name="security">
        <div class="setting-body">
          <el-form label-position="top">
            <el-form-item label="原密码">
              <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少 6 位" />
            </el-form-item>
            <el-form-item label="确认新密码">
              <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
            </el-form-item>
          </el-form>
          <div class="save-row">
            <el-button type="primary" :loading="savingPwd" @click="savePassword">修改密码</el-button>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="系统设置" name="system">
        <div class="setting-body">
          <div class="set-item">
            <div>
              <div class="set-item-title">默认 AI 模型</div>
              <div class="set-item-desc">发起新对话时使用的模型</div>
            </div>
            <el-select :model-value="defaultModel" size="default" style="width: 190px" @change="saveModel">
              <el-option
                v-for="(id, label) in models"
                :key="id"
                :label="label"
                :value="id"
              />
            </el-select>
          </div>

          <div class="set-item">
            <div>
              <div class="set-item-title">自动展开情绪分析</div>
              <div class="set-item-desc">进入 AI 咨询时自动展开左侧情绪分析面板</div>
            </div>
            <el-switch :model-value="autoAnalysis" @change="toggleAnalysis" />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<style scoped>
.settings-dialog :deep(.el-dialog) {
  border-radius: 18px;
}

.setting-body {
  padding: 6px 4px;
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-bottom: 20px;
}

.set-avatar {
  background: linear-gradient(135deg, #60a5fa, #3b82f6);
  color: #ffffff;
  font-weight: 600;
  font-size: 22px;
}

.save-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 6px;
}

.set-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 0;
  border-bottom: 1px solid #f0f1f4;
}

.set-item:last-child {
  border-bottom: none;
}

.set-item-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}

.set-item-desc {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 4px;
}
</style>
