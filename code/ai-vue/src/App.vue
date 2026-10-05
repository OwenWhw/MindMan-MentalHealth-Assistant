<template>
  <router-view v-slot="{ Component }">
    <keep-alive :include="['LoginView']">
      <component :is="Component" />
    </keep-alive>
  </router-view>
  <FloatPlayer />
</template>

<script setup>
import FloatPlayer from '@/components/FloatPlayer.vue'
</script>
