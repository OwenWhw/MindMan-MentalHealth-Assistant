<script setup>
import { computed } from 'vue'

const props = defineProps({
  name: {
    type: String,
    default: ''
  },
  userId: {
    type: [String, Number],
    default: ''
  },
  avatar: {
    type: String,
    default: ''
  },
  size: {
    type: Number,
    default: 32
  }
})

const initial = computed(() => (props.name || 'U').charAt(0).toUpperCase())
</script>

<template>
  <div class="user-cell">
    <el-avatar :size="size" :src="avatar || undefined" class="user-avatar">
      {{ initial }}
    </el-avatar>
    <div class="user-cell-info">
      <span class="user-name">{{ name }}</span>
      <span class="user-id">#{{ userId }}</span>
    </div>
  </div>
</template>

<style scoped>
.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.user-avatar {
  flex-shrink: 0;
  background: #2f6fdb;
  color: #fff;
  font-weight: 600;
}

.user-cell-info {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
  min-width: 0;
}

.user-name {
  font-size: 13px;
  font-weight: 500;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-id {
  font-size: 11px;
  color: #9ca3af;
}
</style>
