<script setup>
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const store = useAppStore()

const menuList = [
  { title: '数据分析', path: '/back/index', icon: 'DataAnalysis' },
  { title: '知识文章', path: '/back/articles', icon: 'Document' },
  { title: '分类管理', path: '/back/categories', icon: 'Collection' },
  { title: '用户管理', path: '/back/users', icon: 'User' },
  { title: '咨询记录', path: '/back/records', icon: 'ChatDotRound' },
  { title: '情绪日志', path: '/back/diaries', icon: 'Sunny' },
]
</script>

<template>
  <aside class="sidebar" :class="{ collapsed: store.sidebarCollapsed }">
    <div class="brand">
      <div class="brand-text" v-show="!store.sidebarCollapsed">
        <div class="brand-title">MindMan</div>
        <div class="brand-sub">心理健康助手</div>
        <div class="brand-badge">管理端 · ADMIN</div>
      </div>
    </div>

    <el-menu
      :default-active="route.path"
      router
      :collapse="store.sidebarCollapsed"
      :collapse-transition="false"
      class="sidebar-menu"
      background-color="#ffffff"
      text-color="#5b6472"
      active-text-color="#2459b3"
    >
      <el-menu-item v-for="item in menuList" :key="item.path" :index="item.path">
        <el-icon><component :is="item.icon" /></el-icon>
        <template #title>{{ item.title }}</template>
      </el-menu-item>
    </el-menu>

    <div class="sidebar-footer" v-show="!store.sidebarCollapsed">
      <span>v0.1.0</span>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 240px;
  min-width: 240px;
  height: 100vh;
  background-color: #ffffff;
  color: #3f4a5a;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #eceef1;
  overflow: hidden;
  transition: width 0.25s ease, min-width 0.25s ease;
}

.sidebar.collapsed {
  width: 64px;
  min-width: 64px;
}

.brand {
  height: 88px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid #f0f1f4;
}

.sidebar.collapsed .brand {
  padding: 0 12px;
  justify-content: center;
}

.brand-title {
  font-size: 17px;
  font-weight: 600;
  color: #1f2937;
  letter-spacing: 2px;
  line-height: 1.2;
}

.brand-sub {
  font-size: 10px;
  color: #98a1ae;
  letter-spacing: 1.5px;
  text-transform: uppercase;
  line-height: 1.2;
}

.brand-badge {
  display: inline-block;
  margin-top: 6px;
  padding: 2px 10px;
  border-radius: 999px;
  background: #111111;
  color: #ffffff;
  font-size: 10px;
  letter-spacing: 2px;
  line-height: 1.6;
}

.sidebar-menu {
  flex: 1;
  border-right: none;
  padding: 12px 0;
}

.sidebar-menu :deep(.el-menu-item) {
  height: 48px;
  line-height: 48px;
  margin: 4px 10px;
  border-radius: 8px;
  font-size: 14px;
  transition: all 0.2s ease;
}

.sidebar-menu :deep(.el-menu-item:hover) {
  background-color: #f3f5f7 !important;
  color: #1f2937 !important;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background-color: rgba(47, 111, 219, 0.1) !important;
  color: #2459b3 !important;
  font-weight: 600;
}

.sidebar-menu:not(.el-menu--collapse) {
  width: 100%;
}

.sidebar-menu.el-menu--collapse {
  width: 64px;
}

.sidebar-menu.el-menu--collapse :deep(.el-menu-item) {
  margin: 4px 0;
  padding: 0;
  line-height: normal;
  justify-content: center;
}

.sidebar-menu.el-menu--collapse :deep(.el-menu-item .el-icon) {
  margin-right: 0;
}

.sidebar-footer {
  height: 48px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  border-top: 1px solid #f0f1f4;
  font-size: 12px;
  color: #9aa3af;
}
</style>
