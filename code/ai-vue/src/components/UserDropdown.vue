<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { logout as logoutApi } from '@/api/auth'
import UserSettingsDialog from './UserSettingsDialog.vue'

const router = useRouter()
const authStore = useAuthStore()
const settingsVisible = ref(false)

const displayName = computed(() => authStore.userInfo?.nickname || '用户')
const roleText = computed(() => (authStore.userInfo?.role === 'admin' ? '管理员' : '普通用户'))

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '退出确认', {
      confirmButtonText: '退出登录',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) { return }
  try { await logoutApi() } catch (e) {}
  authStore.logout()
  ElMessage.success('已退出登录')
  router.replace('/login')
}
</script>

<template>
  <el-dropdown trigger="click">
    <div class="user-trigger">
      <el-avatar :size="30" class="u-avatar">{{ displayName.charAt(0) }}</el-avatar>
      <span class="u-name">{{ displayName }}</span>
      <el-icon class="u-arrow"><ArrowDown /></el-icon>
    </div>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item disabled>{{ roleText }}</el-dropdown-item>
        <el-dropdown-item @click="settingsVisible = true">
          <el-icon><Setting /></el-icon>设置
        </el-dropdown-item>
        <el-dropdown-item divided @click="handleLogout">
          <el-icon><SwitchButton /></el-icon>退出登录
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
  <UserSettingsDialog v-model="settingsVisible" />
</template>

<style scoped>
.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 10px 4px 6px;
  border-radius: 999px;
  transition: background-color 0.2s;
}
.user-trigger:hover {
  background-color: rgba(99, 102, 241, 0.06);
}

.u-avatar {
  background: linear-gradient(135deg, #60a5fa, #3b82f6);
  color: #ffffff;
  font-weight: 600;
}

.u-name {
  font-size: 14px;
  color: #1f2937;
}

.u-arrow {
  font-size: 12px;
  color: #9ca3af;
}

/* 窄窗口时只保留头像，避免与顶部图标重叠 */
@media (max-width: 900px) {
  .u-name {
    display: none;
  }
}
@media (max-width: 520px) {
  .user-trigger {
    padding: 3px 7px 3px 4px;
    gap: 5px;
  }

  .u-avatar {
    width: 28px;
    height: 28px;
  }
}
</style>
