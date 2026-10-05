<script setup>
defineProps({
  title: {
    type: String,
    required: true
  },
  icon: {
    type: String,
    default: ''
  }
})
</script>

<template>
  <div class="page-head">
    <h2>
      <el-icon v-if="icon">
        <component :is="icon" />
      </el-icon>
      <span>{{ title }}</span>
    </h2>
    <div class="page-head-actions">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.page-head h2 {
  margin: 0;
  font-size: 20px;
  color: #1f2937;
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
