<script setup>
import { useRoute } from 'vue-router'
import AppNavBar from '@/components/AppNavBar.vue'
import UserDropdown from '@/components/UserDropdown.vue'

const route = useRoute()

const navActions = [
  { key: 'consult',  title: 'AI 咨询',  icon: 'ChatDotRound', path: '/consult' },
  { key: 'garden',   title: '情绪花园', icon: 'Cherry',       path: '/garden' },
  { key: 'articles', title: '知识文章', icon: 'Collection',   path: '/home/articles' },
  { key: 'home',     title: '回到主页', icon: 'HomeFilled',   path: '/home' }
]
</script>

<template>
  <div class="user-layout">
    <AppNavBar :actions="navActions" :current-path="route.path">
      <template #actions-after>
        <UserDropdown />
      </template>
    </AppNavBar>
    <main class="user-main">
      <router-view />
    </main>
    <footer class="user-footer">
      <p>心理健康助手 · 让心与心的连接温暖您的每一天</p>
    </footer>
  </div>
</template>

<style scoped>
.user-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  overflow-x: hidden;
  background:
    radial-gradient(900px 360px at 50% -160px, rgba(47, 111, 219, 0.14), transparent 70%),
    #f8fafc;
}
.user-main { flex: 1 0 auto; min-height: 0; }
.user-footer {
  flex-shrink: 0;
  padding: 24px;
  text-align: center;
  font-size: 13px;
  color: #9ca3af;
  border-top: 1px solid #eceef1;
  background: #ffffff;
}
</style>