<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps({
  brandName:   { type: String, default: 'MindMan' },
  brandSub:    { type: String, default: '心理健康助手' },
  actions:     { type: Array, default: () => [] }, // [{key, title, icon, path}]
  currentPath: { type: String, default: '' }
})

const emit = defineEmits(['action'])

const router = useRouter()

// 所有按钮都显示；当前页面的按钮加 active 高亮（不再过滤隐藏）
const visibleActions = computed(() => props.actions)

function handleAction(action) {
  if (action.path && action.path !== props.currentPath) {
    router.push(action.path)
  }
  emit('action', action)
}
</script>

<template>
  <div class="app-nav">
    <div class="nav-pill">
      <!-- 左：品牌 -->
      <slot name="brand">
        <router-link to="/home" class="nav-brand">
          <span class="brand-name">{{ brandName }}</span>
          <span class="brand-sub">{{ brandSub }}</span>
        </router-link>
      </slot>

      <!-- 中：可选的额外内容（如进度条） -->
      <slot name="extra" />

      <!-- 右：图标按钮组 -->
      <div class="nav-actions">
        <el-tooltip
          v-for="action in visibleActions"
          :key="action.key"
          :content="action.title"
          placement="bottom"
          :show-after="400"
        >
          <button
            class="nav-icon"
            :class="{ active: action.path === props.currentPath }"
            @click="handleAction(action)"
          >
            <el-icon><component :is="action.icon" /></el-icon>
          </button>
        </el-tooltip>
        <slot name="actions-after" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.app-nav {
  position: fixed;
  top: 14px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 100;
  width: calc(100% - 44px);
  max-width: 1136px;
  pointer-events: none;
}

.nav-pill {
  pointer-events: auto;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 16px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(20px) saturate(1.6);
  -webkit-backdrop-filter: blur(20px) saturate(1.6);
  border: 1px solid rgba(255, 255, 255, 0.72);
  box-shadow:
    0 10px 34px rgba(47, 111, 219, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.85);
}

/* 让品牌插槽不被挤压 */
.nav-pill > :slotted([class*="brand"]),
.nav-pill > :slotted([class*="-brand"]) {
  flex-shrink: 0;
}

/* 让 extra 插槽（搜索/进度条）占据中间剩余空间 */
.nav-pill > [class*="nav-search"],
.nav-pill > [class*="nav-progress"] {
  flex: 1;
  min-width: 120px;
  max-width: 360px;
}

.nav-brand {
  display: flex;
  align-items: baseline;
  gap: 10px;
  text-decoration: none;
  flex-shrink: 0;
}
.brand-name {
  font-size: 17px;
  font-weight: 700;
  color: #111111;
  letter-spacing: 1px;
}
.brand-sub {
  font-size: 10px;
  color: #98a1ae;
  letter-spacing: 1.3px;
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.nav-icon {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  border: 1px solid rgba(226, 232, 240, 0.85);
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(14px);
  color: #64748b;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
}
.nav-icon:hover {
  color: #2f6fdb;
  border-color: #93c5fd;
  transform: translateY(-1px);
}
.nav-icon.active {
  color: #2f6fdb;
  border-color: #2f6fdb;
  background: rgba(47, 111, 219, 0.1);
}

@media (max-width: 860px) {
  .app-nav { padding: 0 14px; }
  .brand-sub { display: none; }
}

/* 窄窗口时收紧间距，保证右侧按钮组与用户信息不重叠 */
@media (max-width: 900px) {
  .nav-pill {
    gap: 10px;
    padding: 9px 12px;
  }

  .nav-actions {
    gap: 6px;
  }

  .nav-icon {
    width: 34px;
    height: 34px;
  }

  .nav-pill > [class*="nav-search"] {
    min-width: 100px;
  }
}
/* iPhone 窄屏：压缩胶囊、隐藏搜索，防溢出错乱 */
@media (max-width: 520px) {
  .app-nav {
    top: 8px;
    width: calc(100% - 20px);
  }

  .nav-pill {
    padding: 8px 12px;
    gap: 8px;
  }

  .brand-name {
    font-size: 15px;
  }

  .nav-icon {
    width: 34px;
    height: 34px;
    border-radius: 10px;
    font-size: 15px;
  }

  .nav-actions {
    gap: 5px;
  }

  .nav-pill > [class*="nav-search"] {
    display: none;
  }
}
</style>
