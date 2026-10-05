<template>
  <aside v-if="isPublicDemo" class="public-demo-note" role="note">
    <strong>页面演示</strong>
    <span>未连接真实 AI 或云端数据库；记录仅在当前浏览器中模拟，请勿填写私人信息。</span>
  </aside>
  <router-view v-slot="{ Component }">
    <keep-alive :include="['LoginView']">
      <component :is="Component" />
    </keep-alive>
  </router-view>
  <FloatPlayer />
</template>

<script setup>
import FloatPlayer from '@/components/FloatPlayer.vue'
const isPublicDemo = import.meta.env.VITE_PUBLIC_DEMO === 'true'
</script>

<style>
.public-demo-note { display: flex; align-items: center; justify-content: center; gap: 12px; padding: 10px 18px; background: #edf2e7; color: #48634d; font-size: 12px; line-height: 1.6; border-bottom: 1px solid #dbe5d4; }
.public-demo-note strong { flex-shrink: 0; font-weight: 600; }
@media (max-width: 600px) { .public-demo-note { align-items: flex-start; justify-content: flex-start; gap: 8px; font-size: 11px; } }
</style>
