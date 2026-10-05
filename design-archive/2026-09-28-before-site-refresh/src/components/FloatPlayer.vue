<script setup>
import { ref, computed } from 'vue'
import { usePlayerStore } from '@/stores/player'
import {
  Play,
  Pause,
  Volume2,
  ChevronDown,
  X
} from 'lucide-vue-next'

const store = usePlayerStore()
const noisePresets = store.NOISE_PRESETS

/** 当前白噪音 */
const current = computed(() => noisePresets.find(p => p.id === store.noiseId) || null)
const currentCover = computed(() => current.value?.cover || '')
const currentName = computed(() => current.value?.name || '')
const currentDesc = computed(() => current.value?.desc || '白噪音')

const noisePlaying = computed(() => store.playing)

/** 切换白噪音（点击列表项） */
function switchNoise(p) {
  store.playNoise(p)
}

/** 浮球底部按钮：暂停/恢复当前白噪音 */
function toggleCurrentNoise() {
  const p = noisePresets.find(x => x.id === store.noiseId)
  if (p) store.playNoise(p)
}
</script>

<template>
  <Transition name="float-pop">
    <div
      v-if="store.visible"
      class="float-player"
      :class="{ minimized: store.minimized }"
    >
      <!-- ═════ 收起：小球 ═════ -->
      <div
        v-if="store.minimized"
        class="fp-ball"
        @click="store.toggleMinimized()"
      >
        <img v-if="currentCover" class="fp-ball-cover" :src="currentCover" alt="" />
        <span v-if="noisePlaying" class="fp-ball-eq">
          <i v-for="n in 3" :key="n"></i>
        </span>
        <span v-else class="fp-ball-icon">
          <Volume2 :size="22" />
        </span>
      </div>

      <!-- ═════ 展开：玻璃面板 ═════ -->
      <div v-else class="fp-panel">
        <div class="fp-head">
          <div class="fp-cover">
            <img v-if="currentCover" :src="currentCover" alt="" />
          </div>
          <div class="fp-info">
            <div class="fp-title">{{ currentName || '未选择' }}</div>
            <div class="fp-sub">{{ currentDesc }}</div>
          </div>
          <div class="fp-actions">
            <button class="fp-icon-btn" title="收起" @click="store.toggleMinimized()">
              <ChevronDown :size="15" />
            </button>
            <button class="fp-icon-btn fp-close" title="关闭" @click="store.closePlayer()">
              <X :size="15" />
            </button>
          </div>
        </div>

        <!-- 白噪音切换列表 -->
        <div class="fp-list">
          <button
            v-for="p in noisePresets"
            :key="p.id"
            class="fp-chip"
            :class="{ active: store.noiseId === p.id }"
            @click="switchNoise(p)"
          >
            {{ p.name }}
          </button>
        </div>

        <!-- 底部：播放/暂停（最左）+ 迷你音量 -->
        <div class="fp-bottom">
          <button
            class="fp-play-btn"
            :disabled="!store.noiseId"
            @click="toggleCurrentNoise"
          >
            <Pause v-if="noisePlaying" :size="19" />
            <Play v-else :size="19" />
          </button>
          <div class="fp-volume-mini">
            <Volume2 :size="15" />
            <el-slider
              :model-value="store.volume"
              :min="0"
              :max="100"
              :show-tooltip="false"
              size="small"
              @input="store.setVolume"
            />
          </div>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
/* ═══ 容器：右下角 ═══ */
.float-player {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 3000;
}

/* ═════ 收起小球 ═════ */
.fp-ball {
  position: relative;
  width: 60px; height: 60px;
  border-radius: 50%;
  cursor: pointer;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.35);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  border: 1.5px solid rgba(255, 255, 255, 0.55);
  box-shadow: 0 10px 30px rgba(31, 41, 55, 0.28);
  display: flex; align-items: center; justify-content: center;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}
.fp-ball:hover { transform: scale(1.06); box-shadow: 0 14px 36px rgba(31, 41, 55, 0.34); }
.fp-ball-cover { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.fp-ball-icon {
  width: 100%; height: 100%;
  display: flex; align-items: center; justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, rgba(79, 70, 229, 0.75), rgba(236, 72, 153, 0.65));
}
.fp-ball-icon svg { color: #fff; }

/* 小球内播放动画 */
.fp-ball-eq {
  position: absolute; inset: 0;
  display: flex; align-items: center; justify-content: center; gap: 4px;
  background: rgba(15, 23, 42, 0.55);
}
.fp-ball-eq i {
  width: 4px; border-radius: 2px; background: #fff;
  animation: ballEq 0.8s ease-in-out infinite;
}
.fp-ball-eq i:nth-child(2) { animation-delay: 0.18s; }
.fp-ball-eq i:nth-child(3) { animation-delay: 0.36s; }
@keyframes ballEq {
  0%, 100% { height: 8px; }
  50% { height: 22px; }
}

/* ═════ 展开玻璃面板 ═════ */
.fp-panel {
  width: 300px;
  border-radius: 22px;
  padding: 16px 18px 14px;
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(28px) saturate(1.4);
  -webkit-backdrop-filter: blur(28px) saturate(1.4);
  border: 1px solid rgba(255, 255, 255, 0.7);
  box-shadow: 0 18px 48px rgba(31, 41, 55, 0.18);
  color: #1f2937;
  transition: all 0.28s ease;
}

/* 头部 */
.fp-head { display: flex; align-items: center; gap: 12px; }
.fp-cover {
  width: 48px; height: 48px; border-radius: 13px; overflow: hidden; flex-shrink: 0;
  border: 1px solid rgba(31, 41, 55, 0.12);
}
.fp-cover img { width: 100%; height: 100%; object-fit: cover; }
.fp-info { flex: 1; min-width: 0; }
.fp-title {
  font-size: 13.5px; font-weight: 700; color: #111827;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-bottom: 3px;
}
.fp-sub { font-size: 11px; color: #64748b; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.fp-actions { display: flex; gap: 6px; flex-shrink: 0; }
.fp-icon-btn {
  width: 28px; height: 28px; border-radius: 50%;
  border: 1px solid rgba(31, 41, 55, 0.15);
  background: rgba(255, 255, 255, 0.7);
  color: #374151; cursor: pointer;
  display: inline-flex; align-items: center; justify-content: center;
  transition: all 0.2s;
}
.fp-icon-btn:hover { background: #eef2ff; transform: scale(1.08); }
.fp-close:hover { background: #ef4444; color: #fff; border-color: transparent; }

/* 白噪音切换列表 */
.fp-list {
  display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px;
  max-height: 92px; overflow-y: auto;
}
.fp-list::-webkit-scrollbar { width: 3px; }
.fp-list::-webkit-scrollbar-thumb { background: rgba(31,41,55,0.25); border-radius: 3px; }
.fp-chip {
  padding: 5px 12px; border-radius: 999px; font-size: 11.5px;
  border: 1px solid rgba(31, 41, 55, 0.16);
  background: rgba(255, 255, 255, 0.75);
  color: #374151;
  cursor: pointer; transition: all 0.2s; max-width: 100%;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.fp-chip:hover { background: #eef2ff; color: #1e40af; }
.fp-chip.active {
  background: #2f6fdb;
  color: #fff;
  border-color: transparent;
  font-weight: 700;
  box-shadow: 0 4px 14px rgba(47, 111, 219, 0.35);
}

/* 底部：播放/暂停（最左）+ 迷你音量 */
.fp-bottom {
  display: flex; align-items: center; gap: 12px; margin-top: 14px;
}
.fp-play-btn {
  width: 40px; height: 40px; border-radius: 50%;
  border: none; cursor: pointer; color: #1f2937;
  background: linear-gradient(135deg, rgba(255,255,255,0.95), rgba(255,255,255,0.75));
  display: inline-flex; align-items: center; justify-content: center;
  transition: all 0.22s; flex-shrink: 0;
}
.fp-play-btn:hover { transform: scale(1.08); box-shadow: 0 8px 20px rgba(0,0,0,0.22); }
.fp-play-btn:disabled { opacity: 0.45; cursor: not-allowed; transform: none; box-shadow: none; }

/* 迷你音量（更小，紧贴播放键右侧） */
.fp-volume-mini {
  display: flex; align-items: center; gap: 8px; flex: 1; min-width: 0;
}
.fp-volume-mini svg { color: #64748b; flex-shrink: 0; }
.fp-volume-mini :deep(.el-slider) { flex: 1; }
.fp-volume-mini :deep(.el-slider__runway) { background: rgba(31,41,55,0.12); margin: 12px 0; }
.fp-volume-mini :deep(.el-slider__bar) { background: #2f6fdb; }
.fp-volume-mini :deep(.el-slider__button) { border-color: #2f6fdb; background: #fff; box-shadow: 0 2px 6px rgba(0,0,0,0.25); }

/* 弹出/收起动画 */
.float-pop-enter-active, .float-pop-leave-active { transition: all 0.25s ease; }
.float-pop-enter-from { opacity: 0; transform: translateY(12px) scale(0.92); }
.float-pop-leave-to { opacity: 0; transform: translateY(12px) scale(0.92); }
</style>