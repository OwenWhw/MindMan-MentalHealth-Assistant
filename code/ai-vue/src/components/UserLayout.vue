<script setup>
import { useRoute } from 'vue-router'
import AppNavBar from '@/components/AppNavBar.vue'
import UserDropdown from '@/components/UserDropdown.vue'
import { USER_NAV_ACTIONS } from '@/constants/userNavigation'

const route = useRoute()

</script>

<template>
  <div class="user-layout">
    <AppNavBar :actions="USER_NAV_ACTIONS" :current-path="route.path">
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
    radial-gradient(900px 360px at 50% -160px, rgba(121, 156, 115, 0.15), transparent 70%),
    #faf9f5;
}
.user-main { flex: 1 0 auto; min-height: 0; }
.user-footer {
  flex-shrink: 0;
  padding: 24px;
  text-align: center;
  font-size: 13px;
  color: #849487;
  border-top: 1px solid #e2e9de;
  background: #fffdf9;
}
</style>
