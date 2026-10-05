<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import AppNavBar from '@/components/AppNavBar.vue'
import UserDropdown from '@/components/UserDropdown.vue'
import { USER_NAV_ACTIONS } from '@/constants/userNavigation'
import { usePlayerStore, NOISE_PRESETS } from '@/stores/player'

const route = useRoute()
const store = usePlayerStore()

/** 播放白噪音（store 统一引擎 + 浮球） */
function playNoise(p) {
  store.playNoise(p)
}

/* ═══ 背景粒子波（Canvas 实时渲染） ═══
 * 每颗粒子沿水平方向 sin 波动 + 明暗呼吸，行间能量中间强边缘弱，
 * 形成类似声波/水波从中心向四周流动的动效。 */
const waveCanvas = ref(null)
let rafId = null
let t = 0
let particlePool = []

onMounted(() => {
  const canvas = waveCanvas.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  const DPR = Math.min(window.devicePixelRatio || 1, 2)
  let W = 0, H = 0

  const resize = () => {
    W = canvas.clientWidth
    H = canvas.clientHeight
    canvas.width = W * DPR
    canvas.height = H * DPR
    ctx.setTransform(DPR, 0, 0, DPR, 0, 0)
  }
  resize()
  window.addEventListener('resize', resize)

  // 预生成粒子：14 行 × 80 列
  const rows = 14, cols = 80
  particlePool = []
  for (let r = 0; r < rows; r++) {
    // 行在纵向 8% ~ 92% 分布
    const baseY = 0.08 + r * (0.84 / (rows - 1))
    // 能量权重：中间行最强，边缘衰减（正弦包络）
    const rowWeight = Math.sin(Math.PI * (r / (rows - 1)))
    for (let c = 0; c < cols; c++) {
      particlePool.push({
        x: c / (cols - 1),          // 水平位置 0~1
        baseY,
        rowWeight,
        phase: r * 0.42 + c * 0.09  // 相位错位 → 斜向流动
      })
    }
  }

  const draw = () => {
    t += 0.0065
    ctx.clearRect(0, 0, W, H)
    const color = '#10b981'
    for (const p of particlePool) {
      // 波动位移：sin 相位驱动，中间行振幅大
      const wave = Math.sin(p.phase + t * 2.4) * 0.014 * p.rowWeight
      // 额外的慢速次级波动，增加流动层次
      const wave2 = Math.sin(p.phase * 0.6 + t * 1.1) * 0.006 * p.rowWeight
      const y = (p.baseY + wave + wave2) * H
      // 明暗呼吸：随同一相位起伏
      const alpha = 0.10 + 0.45 * (0.5 + 0.5 * Math.sin(p.phase + t * 2.4))
      // 粒子大小随能量权重变化
      const rad = 0.6 + p.rowWeight * 1.3
      ctx.globalAlpha = alpha
      ctx.fillStyle = color
      ctx.beginPath()
      ctx.arc(p.x * W, y, rad, 0, Math.PI * 2)
      ctx.fill()
    }
    ctx.globalAlpha = 1
    rafId = requestAnimationFrame(draw)
  }
  draw()

  onUnmounted(() => {
    cancelAnimationFrame(rafId)
    window.removeEventListener('resize', resize)
  })
})
</script>

<template>
  <div class="relax-page">
    <!-- ═══ 背景：浅色底 + Canvas 绿色粒子波浪 ═══ -->
    <div class="relax-bg" aria-hidden="true">
      <canvas ref="waveCanvas" class="bg-wave"></canvas>
    </div>

    <AppNavBar :actions="USER_NAV_ACTIONS" :current-path="route.path">
      <template #actions-after>
        <UserDropdown />
      </template>
    </AppNavBar>

    <main class="relax-wrap">
      <div class="relax-intro"><div class="relax-intro-top"><span>声音陪伴</span><p class="relax-whisper" aria-hidden="true">breathe in, let go.</p></div><h1>让世界，<em>安静一点。</em></h1><p>雨声、海浪、风穿过树叶。挑一个喜欢的声音，给自己片刻留白。</p></div>
      <div class="panel-head">
        <h2 class="sec-title">选择一段声音</h2>
        <span class="panel-hint">点击播放 · 再点暂停</span>
      </div>

      <div class="noise-grid">
        <div
          v-for="p in NOISE_PRESETS"
          :key="p.id"
          class="noise-card"
          :class="{ playing: store.noiseId === p.id && store.playing }"
          :style="{ '--accent': p.color }"
          @click="playNoise(p)"
        >
          <img class="noise-cover" :src="p.cover" :alt="p.name" loading="lazy" />
          <div class="noise-veil" :class="{ on: store.noiseId === p.id }"></div>
          <div class="noise-top">
            <span class="noise-status">
              {{ store.noiseId === p.id && store.playing ? '正在播放' : '播放' }}
            </span>
            <span v-if="store.noiseId === p.id && store.playing" class="noise-eq">
              <i v-for="n in 4" :key="n"></i>
            </span>
          </div>
          <div class="noise-info">
            <div class="noise-name">{{ p.name }}</div>
            <div class="noise-desc">{{ p.desc }}</div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
/* ═══ 页面基色（保持浅色渐变） ═══ */
.relax-page {
  position: relative;
  height: 100vh;
  overflow-y: auto;
  background:
    radial-gradient(640px 380px at 88% 6%, rgba(16, 185, 129, 0.10), transparent 62%),
    radial-gradient(560px 340px at 8% 30%, rgba(59, 130, 246, 0.08), transparent 60%),
    linear-gradient(180deg, #f6faf9 0%, #eef6f3 52%, #f8fafc 100%);
}

/* ═══ 背景动效层（fixed 全屏，pointer-events:none 不阻挡交互） ═══ */
.relax-bg {
  position: fixed; inset: 0; pointer-events: none; overflow: hidden; z-index: 0;
}

/* ═══ 背景粒子波浪（Canvas） ═══ */
.bg-wave {
  position: absolute; inset: 0; width: 100%; height: 100%;
  pointer-events: none; z-index: 0;
}

/* ═══ 内容层（在背景之上） ═══ */
.relax-wrap { position: relative; z-index: 1; max-width: 980px; margin: 0 auto; padding: 96px 28px 72px; }
.relax-intro{border-top:1px solid #c8d7c8;padding:28px 0 33px;margin-bottom:27px;animation:relax-enter .8s ease-out both}
.relax-intro-top>span{display:block;color:#56795e;font-size:12px;font-weight:600;letter-spacing:.07em}
.relax-intro-top{display:flex;align-items:flex-start;justify-content:space-between;gap:20px}
.relax-intro-top .relax-whisper{margin:0 clamp(0px,4vw,40px) 0 0;padding-bottom:3px;color:#a5bb9f;font:italic 500 clamp(20px,2.2vw,25px)/1.12 Georgia,'Noto Serif SC',serif;letter-spacing:-.05em;white-space:nowrap}
.relax-intro h1{font:500 clamp(35px,4vw,54px)/1.25 'Noto Serif SC',Georgia,serif;letter-spacing:-.06em;color:#2d4936;margin:15px 0 10px;text-wrap:balance}
.relax-intro h1 em{font-style:normal;color:#779478}.relax-intro p{color:#607465;font-size:13px;line-height:1.8;max-width:470px;margin:0}
.noise-card{border-radius:4px !important}.noise-card:active{transform:translateY(0) scale(.99)}
@keyframes relax-enter{from{opacity:0;transform:translateY(18px)}to{opacity:1;transform:translateY(0)}}
@media(prefers-reduced-motion:reduce){.relax-intro{animation:none}}

.panel-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
.sec-title { margin: 0; font-size: 20px; font-weight: 800; color: #111827; }
.panel-hint { font-size: 12px; color: #94a3b8; }

/* ═══ 白噪音卡片（保持白底以和深色背景形成对比） ═══ */
.noise-grid {
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 18px;
}
.noise-card {
  position: relative; height: 220px; border-radius: 20px; overflow: hidden;
  cursor: pointer; transition: all 0.3s ease;
  background: #fff;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.10);
}
.noise-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 18px 44px rgba(17, 24, 39, 0.16);
}
.noise-card.playing {
  box-shadow: 0 18px 44px color-mix(in srgb, var(--accent) 35%, transparent);
  outline: 2px solid var(--accent); outline-offset: -2px;
}
.noise-cover {
  position: absolute; inset: 0; width: 100%; height: 100%;
  object-fit: cover; transition: transform 0.6s ease;
}
.noise-card:hover .noise-cover { transform: scale(1.06); }

.noise-veil {
  position: absolute; inset: 0;
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.05) 30%, rgba(15, 23, 42, 0.62) 78%, rgba(15, 23, 42, 0.85) 100%);
  transition: opacity 0.3s;
}
.noise-veil.on { opacity: 0.92; }

.noise-top {
  position: absolute; top: 14px; left: 14px; right: 14px;
  display: flex; align-items: center; justify-content: space-between;
  z-index: 2;
}
.noise-status {
  font-size: 11px; font-weight: 700; letter-spacing: 2px; color: rgba(255, 255, 255, 0.92);
  background: rgba(15, 23, 42, 0.35); backdrop-filter: blur(8px);
  padding: 5px 12px; border-radius: 999px; text-transform: uppercase;
}
.noise-eq { display: flex; align-items: flex-end; gap: 3px; height: 16px; }
.noise-eq i {
  width: 3px; border-radius: 2px; background: #fff;
  animation: eq 0.9s ease-in-out infinite;
}
.noise-eq i:nth-child(2) { animation-delay: 0.15s; }
.noise-eq i:nth-child(3) { animation-delay: 0.3s; }
.noise-eq i:nth-child(4) { animation-delay: 0.45s; }
@keyframes eq {
  0%, 100% { height: 5px; }
  50% { height: 16px; }
}

.noise-info {
  position: absolute; left: 18px; right: 18px; bottom: 16px; z-index: 2;
}
.noise-name {
  font-size: 20px; font-weight: 800; color: #fff;
  letter-spacing: 1.5px; margin-bottom: 6px;
  font-family: 'Songti SC', 'STSong', serif;
}
.noise-desc {
  font-size: 11.5px; color: rgba(255, 255, 255, 0.78);
  line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}

/* 响应式 */
@media (max-width: 860px) {
  .noise-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 520px) {
  .relax-page { height: 100dvh; }
  .relax-wrap { padding: 84px 14px 48px; }
  .noise-grid { grid-template-columns: 1fr; }
}
</style>
