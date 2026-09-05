<script setup>
import Sidebar from './Sidebar.vue'
import Navbar from './Navbar.vue'
</script>

<template>
  <div class="backend-layout">
    <Sidebar />
    <div class="main-wrapper">
      <Navbar />
      <main class="main-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.backend-layout {
  display: flex;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
}

.main-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background-color: #f8fafc;
}

.main-content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}
</style>
