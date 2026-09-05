<script setup>
import PageHead from '@/components/PageHead.vue'
</script>

<template>
  <div class="page">
    <PageHead icon="DataLine" title="测评数据" />
    <el-card class="placeholder">
      <el-empty description="页面内容开发中" />
    </el-card>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.placeholder {
  min-height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
