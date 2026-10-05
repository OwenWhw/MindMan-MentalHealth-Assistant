<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  value: { type: Number, default: 0 },
  label: { type: String, default: '' },
  level: { type: String, default: '平稳' },
  color: { type: String, default: '#4d7cff' },
  color2: { type: String, default: '#a78bfa' },
  size: { type: Number, default: 96 }
})

const R = 46
const C = 2 * Math.PI * R
const gradId = 'ring-' + Math.random().toString(36).slice(2, 9)

const shown = ref(0)
const display = ref('0')
let raf = 0

function animate(to) {
  cancelAnimationFrame(raf)
  const from = shown.value
  const start = performance.now()
  const dur = 1400
  const tick = (t) => {
    const p = Math.min(1, (t - start) / dur)
    const eased = 1 - Math.pow(1 - p, 3)
    shown.value = Math.round(from + (to - from) * eased)
    display.value = String(shown.value)
    if (p < 1) raf = requestAnimationFrame(tick)
  }
  raf = requestAnimationFrame(tick)
}

const dashOffset = computed(() => C * (1 - shown.value / 100))

watch(() => props.value, (v) => animate(Number(v) || 0), { immediate: true })
onMounted(() => animate(Number(props.value) || 0))
onUnmounted(() => cancelAnimationFrame(raf))
</script>

<template>
  <div class="ring" :style="{ width: size + 'px', height: size + 'px', '--s': size / 96 }">
    <svg viewBox="0 0 120 120" class="ring-svg">
      <defs>
        <linearGradient :id="gradId" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" :stop-color="color" />
          <stop offset="100%" :stop-color="color2" />
        </linearGradient>
      </defs>
      <circle class="ring-track" cx="60" cy="60" :r="R" />
      <circle
        class="ring-arc"
        cx="60"
        cy="60"
        :r="R"
        :stroke="`url(#${gradId})`"
        :stroke-dasharray="C"
        :stroke-dashoffset="dashOffset"
      />
      <circle class="ring-glow" cx="60" cy="60" :r="R" :stroke="`url(#${gradId})`" />
    </svg>
    <div class="ring-num">
      {{ display }}<span class="ring-pct">%</span>
    </div>
    <div class="ring-label">{{ label }}</div>
    <div class="ring-level">{{ level }}</div>
  </div>
</template>

<style scoped>
.ring {
  position: relative;
  width: 96px;
  height: 96px;
  --s: 1;
  --c1: #4d7cff;
  --c2: #a78bfa;
}

.ring-svg {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
  filter: drop-shadow(0 3px 10px color-mix(in srgb, var(--c1) 32%, transparent));
}

.ring-track {
  fill: none;
  stroke: rgba(96, 165, 250, 0.14);
  stroke-width: 11;
}

.ring-arc {
  fill: none;
  stroke-width: 11;
  stroke-linecap: round;
  transition: stroke-dashoffset 0.1s linear;
}

/* 内圈虚线装饰，增强科技感 */
.ring-glow {
  fill: none;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-dasharray: 4 12;
  opacity: 0.4;
  transform: rotate(-90deg);
  transform-origin: center;
  animation: ringSpin 16s linear infinite;
}

@keyframes ringSpin {
  to {
    transform: rotate(270deg);
  }
}

.ring-num {
  position: absolute;
  top: calc(20px * var(--s));
  left: 0;
  right: 0;
  text-align: center;
  font-size: calc(26px * var(--s));
  font-weight: 800;
  letter-spacing: -0.5px;
  background: linear-gradient(135deg, var(--c1), var(--c2));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.ring-pct {
  font-size: calc(12px * var(--s));
  font-weight: 600;
  color: rgba(15, 23, 42, 0.35);
  margin-left: 1px;
}

.ring-label {
  position: absolute;
  bottom: calc(22px * var(--s));
  left: 0;
  right: 0;
  text-align: center;
  font-size: calc(10px * var(--s));
  color: rgba(15, 23, 42, 0.55);
  letter-spacing: 1.5px;
  font-weight: 500;
}

.ring-level {
  position: absolute;
  bottom: calc(8px * var(--s));
  left: 50%;
  transform: translateX(-50%);
  padding: 1px 8px;
  border-radius: 999px;
  font-size: calc(9px * var(--s));
  font-weight: 600;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid color-mix(in srgb, var(--c1) 25%, transparent);
  white-space: nowrap;
  line-height: 1.6;
}
</style>
