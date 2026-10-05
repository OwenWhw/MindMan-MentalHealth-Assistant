<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { logout as logoutApi } from '@/api/auth'
import ChangePasswordDialog from './ChangePasswordDialog.vue'

const route = useRoute()
const router = useRouter()
const store = useAppStore()
const authStore = useAuthStore()
const passwordDialogVisible = ref(false)

const displayName = computed(() => authStore.userInfo?.nickname || '管理员')
const roleText = { admin: '超级管理员', user: '普通用户' }
const displayRole = computed(() => roleText[authStore.userInfo?.role] || '超级管理员')
const avatarText = computed(() => displayName.value.charAt(0) || '管')

const pageTitle = computed(() => route.meta.title || '后台管理')

function handleLogout() {
  ElMessageBox.confirm('确定要退出登录吗？', '退出确认', {
    confirmButtonText: '退出登录',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(async () => {
      try {
        await logoutApi()
      } catch (e) {
        // 后端注销失败也继续清空本地状态
      }
      authStore.logout()
      ElMessage.success('已退出登录')
      router.replace('/login')
    })
    .catch(() => {})
}

function handleProfile() {
  ElMessage.info('个人中心功能开发中')
}
</script>

<template>
  <header class="navbar">
    <div class="navbar-left">
      <div class="navbar-title-row">
        <el-button class="collapse-btn" text @click="store.toggleSidebar">
          <el-icon :size="20">
            <Expand v-if="store.sidebarCollapsed" />
            <Fold v-else />
          </el-icon>
        </el-button>
        <h2 class="page-title">{{ pageTitle }}</h2>
      </div>
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/back/index' }">管理后台</el-breadcrumb-item>
        <el-breadcrumb-item>{{ pageTitle }}</el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <div class="navbar-right">
      <el-button circle size="default" class="notice-btn">
        <el-icon :size="18"><Bell /></el-icon>
      </el-button>

      <el-dropdown>
        <div class="user-info">
          <el-avatar
            :size="36"
            :src="authStore.userInfo?.avatar || undefined"
            class="user-avatar"
          >
            {{ avatarText }}
          </el-avatar>
          <div class="user-meta">
            <span class="user-name">{{ displayName }}</span>
            <span class="user-role">{{ displayRole }}</span>
          </div>
          <el-icon class="arrow-down"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="handleProfile">
              <el-icon><User /></el-icon>个人中心
            </el-dropdown-item>
            <el-dropdown-item @click="passwordDialogVisible = true">
              <el-icon><Setting /></el-icon>账号设置
            </el-dropdown-item>
            <el-dropdown-item divided @click="handleLogout">
              <el-icon><SwitchButton /></el-icon>退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <ChangePasswordDialog v-model="passwordDialogVisible" />
    </div>
  </header>
</template>

<style scoped>
.navbar {
  height: 64px;
  background-color: #ffffff;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.navbar-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.navbar-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.collapse-btn {
  padding: 4px;
  margin-left: -8px;
  color: #6b7280;
}

.collapse-btn:hover {
  color: #1f2937;
  background-color: #f3f4f6;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.notice-badge :deep(.el-badge__content) {
  transform: translateY(20%) translateX(100%);
}

.notice-btn {
  font-size: 16px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background-color 0.2s;
}

.user-info:hover {
  background-color: #f3f4f6;
}

.user-avatar {
  background: #2f6fdb;
  color: #fff;
  font-weight: 600;
}

.user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: #1f2937;
}

.user-role {
  font-size: 12px;
  color: #6b7280;
}

.arrow-down {
  color: #9ca3af;
  font-size: 12px;
}
</style>
