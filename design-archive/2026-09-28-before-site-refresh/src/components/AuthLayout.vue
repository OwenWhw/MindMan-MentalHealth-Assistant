<script setup>
import { ref, onMounted, onBeforeUnmount, onActivated, onDeactivated, watch } from 'vue'
import { useRoute } from 'vue-router'
import LoginForm from './LoginForm.vue'
import RegisterForm from './RegisterForm.vue'

const route = useRoute()
const canvasRef = ref()
const panel = ref(null)
const backdropDown = ref(false)

// ===== 顶部 Slogan 轮播 =====
const slogans = [
  { zh: '每个情绪，都值得被温柔以待。', en: 'Let every feeling be cared for.' },
  { zh: '你不孤独，这里有人愿意倾听。',   en: 'You are not alone. Someone is here to listen.' },
  { zh: '把心事说给风听，让心回到安宁。', en: 'Speak your heart to the wind, let peace return.' },
  { zh: '允许自己慢下来，便是治愈的开始。', en: 'Allowing yourself to slow down is where healing begins.' },
  { zh: '今天的低语，是明天的力量。',     en: "Today's whispers become tomorrow's strength." }
]
const sloganIndex = ref(0)
const sloganVisible = ref(true)
let sloganTimer = 0
function nextSlogan() {
  sloganVisible.value = false
  clearTimeout(sloganTimer)
  setTimeout(() => {
    sloganIndex.value = (sloganIndex.value + 1) % slogans.length
    sloganVisible.value = true
  }, 600)
  scheduleNext()
}
function scheduleNext() {
  clearTimeout(sloganTimer)
  sloganTimer = setTimeout(nextSlogan, 5200)
}
function stopSloganTimer() {
  clearTimeout(sloganTimer)
}

function openPanel(name) {
  panel.value = name
  // 弹窗打开时清掉画布上所有活动元素，避免 backdrop-filter 重绘时闪
  if (ctx) {
    floaters.length = 0
    sparkles.length = 0
  }
}

// 点击弹窗外围关闭：必须按下和松开都发生在背板上，
// 避免在输入框里拖动选择文字、松手在弹窗外时误关弹窗
function onBackdropMouseDown(e) {
  if (e.target === e.currentTarget) {
    backdropDown.value = true
  }
}

function onBackdropMouseUp(e) {
  if (e.target === e.currentTarget && backdropDown.value) {
    panel.value = null
  }
  backdropDown.value = false
}

// 支持 /login?panel=login 或 ?panel=register 直接打开对应表单（便于预览）
onMounted(() => {
  const p = route.query.panel
  if (p === 'login' || p === 'register') {
    panel.value = p
  }
})

const DPR = Math.min(window.devicePixelRatio || 1, 2)
let ctx = null
let dots = []
let mouse = { x: -9999, y: -9999 }
let raf = 0
let width = 0
let height = 0
let resizeObserver = null
let particles = []
let floaters = []
let nextSpawnAt = 0
let sparkles = []
let nextSparkleAt = 0

// 像素点阵图案（0/1 网格，1 为点）
const SHAPES = [
  {
    name: 'mindman',
    grid: [
      [1,0,0,0,0,0,1],
      [1,1,0,0,0,1,1],
      [1,0,1,0,1,0,1],
      [1,0,0,1,0,0,1],
      [1,0,0,0,0,0,1],
      [1,0,0,0,0,0,1],
      [1,0,0,0,0,0,1]
    ]
  },
  {
    name: 'smile',
    grid: [
      [0, 0, 1, 1, 1, 1, 0, 0],
      [0, 1, 1, 1, 1, 1, 1, 0],
      [1, 1, 0, 1, 1, 0, 1, 1],
      [1, 1, 0, 1, 1, 0, 1, 1],
      [1, 1, 1, 1, 1, 1, 1, 1],
      [1, 0, 0, 0, 0, 0, 0, 1],
      [0, 1, 0, 0, 0, 0, 1, 0],
      [0, 0, 1, 1, 1, 1, 0, 0]
    ]
  },
  {
    name: 'heart',
    grid: [
      [0, 1, 1, 0, 0, 1, 1, 0],
      [1, 1, 1, 1, 1, 1, 1, 1],
      [1, 1, 1, 1, 1, 1, 1, 1],
      [1, 1, 1, 1, 1, 1, 1, 1],
      [0, 1, 1, 1, 1, 1, 1, 0],
      [0, 0, 1, 1, 1, 1, 0, 0],
      [0, 0, 0, 1, 1, 0, 0, 0]
    ]
  },
  {
    name: 'star',
    grid: [
      [0, 0, 0, 0, 1, 0, 0, 0, 0],
      [0, 0, 0, 1, 1, 1, 0, 0, 0],
      [0, 0, 1, 1, 1, 1, 1, 0, 0],
      [1, 1, 1, 1, 1, 1, 1, 1, 1],
      [0, 1, 1, 1, 1, 1, 1, 1, 0],
      [0, 0, 1, 1, 1, 1, 1, 0, 0],
      [0, 0, 1, 1, 1, 1, 1, 0, 0],
      [0, 1, 1, 0, 1, 0, 1, 1, 0],
      [0, 1, 0, 0, 1, 0, 0, 1, 0]
    ]
  },
  {
    name: 'moon',
    grid: [
      [0, 0, 0, 1, 1, 1, 1, 0],
      [0, 0, 1, 1, 1, 1, 1, 1],
      [0, 1, 1, 1, 1, 0, 0, 1],
      [0, 1, 1, 1, 0, 0, 0, 0],
      [0, 1, 1, 1, 0, 0, 0, 0],
      [0, 1, 1, 1, 1, 0, 0, 1],
      [0, 0, 1, 1, 1, 1, 1, 1],
      [0, 0, 0, 1, 1, 1, 1, 0]
    ]
  },
  {
    name: 'cloud',
    grid: [
      [0, 0, 0, 0, 1, 1, 1, 0, 0],
      [0, 0, 1, 1, 1, 1, 1, 1, 0],
      [0, 1, 1, 1, 1, 1, 1, 1, 1],
      [1, 1, 1, 1, 1, 1, 1, 1, 1],
      [1, 1, 1, 1, 1, 1, 1, 1, 1],
      [0, 0, 0, 0, 0, 0, 0, 0, 0]
    ]
  },
  {
    name: 'flower',
    grid: [
      [0, 0, 0, 1, 0, 1, 0, 0, 0],
      [0, 0, 1, 1, 1, 1, 1, 0, 0],
      [0, 1, 1, 1, 1, 1, 1, 1, 0],
      [1, 1, 1, 1, 1, 1, 1, 1, 1],
      [0, 1, 1, 1, 1, 1, 1, 1, 0],
      [0, 0, 1, 1, 1, 1, 1, 0, 0],
      [0, 0, 0, 1, 1, 1, 0, 0, 0],
      [0, 0, 0, 0, 1, 0, 0, 0, 0]
    ]
  },
  {
    name: 'sprout',
    grid: [
      [0, 0, 0, 0, 1, 0, 0, 0],
      [0, 0, 0, 1, 1, 1, 0, 0],
      [0, 0, 1, 1, 1, 1, 1, 0],
      [0, 0, 0, 1, 1, 1, 0, 0],
      [0, 0, 0, 0, 1, 0, 0, 0],
      [0, 0, 0, 0, 1, 0, 0, 0],
      [0, 0, 0, 0, 1, 0, 0, 0],
      [0, 0, 0, 1, 1, 0, 0, 0]
    ]
  },
  {
    name: 'sun',
    grid: [
      [0, 0, 0, 1, 0, 1, 0, 0, 0],
      [0, 0, 0, 1, 0, 1, 0, 0, 0],
      [0, 1, 1, 1, 1, 1, 1, 1, 0],
      [1, 0, 1, 1, 1, 1, 1, 0, 1],
      [0, 0, 1, 1, 1, 1, 1, 0, 0],
      [1, 0, 1, 1, 1, 1, 1, 0, 1],
      [0, 1, 1, 1, 1, 1, 1, 1, 0],
      [0, 0, 0, 1, 0, 1, 0, 0, 0],
      [0, 0, 0, 1, 0, 1, 0, 0, 0]
    ]
  }
]

const TEXT_W = 560
const TEXT_H = 200
const STEP = 7

// 用离屏画布采样 "MOOD" 字形，生成点阵
function buildDots() {
  const off = document.createElement('canvas')
  off.width = TEXT_W
  off.height = TEXT_H
  const octx = off.getContext('2d')
  octx.fillStyle = '#000'
  octx.fillRect(0, 0, TEXT_W, TEXT_H)
  octx.fillStyle = '#fff'
  octx.font = '700 148px Arial, Helvetica, sans-serif'
  octx.textAlign = 'center'
  octx.textBaseline = 'middle'
  octx.fillText('MOOD', TEXT_W / 2, TEXT_H / 2 + 8)

  const data = octx.getImageData(0, 0, TEXT_W, TEXT_H).data
  dots = []
  const cols = Math.ceil(TEXT_W / STEP)
  const rows = Math.ceil(TEXT_H / STEP)
  for (let y = 0; y < rows; y++) {
    for (let x = 0; x < cols; x++) {
      const px = Math.floor(x * STEP)
      const py = Math.floor(y * STEP)
      // 用亮度（红色通道）判断：只有白色文字处生成点，黑色背景不生成
      if (data[(py * TEXT_W + px) * 4] > 128) {
        dots.push({
          baseX: px / TEXT_W,
          baseY: py / TEXT_H,
          x: 0,
          y: 0,
          vx: 0,
          vy: 0
        })
      }
    }
  }
}

function resize() {
  const canvas = canvasRef.value
  const rect = canvas.parentElement.getBoundingClientRect()
  // 尺寸未变则跳过，避免弹窗出现时无故触发 canvas 重建导致闪烁
  if (rect.width === width && rect.height === height && canvas.width > 0) return
  width = rect.width
  height = rect.height
  canvas.width = width * DPR
  canvas.height = height * DPR
  canvas.style.width = width + 'px'
  canvas.style.height = height + 'px'
  ctx.setTransform(DPR, 0, 0, DPR, 0, 0)
}

function initParticles() {
  particles = Array.from({ length: 70 }, () => ({
    x: Math.random(),
    y: Math.random(),
    vx: (Math.random() - 0.5) * 0.12,
    vy: (Math.random() - 0.5) * 0.1 - 0.015,
    size: Math.random() * 1.2 + 0.6,
    phase: Math.random() * Math.PI * 2
  }))
}

function spawnFloater() {
  const shape = SHAPES[Math.floor(Math.random() * SHAPES.length)]
  const px = 5 + Math.random() * 6
  const w = shape.grid[0].length * px
  const h = shape.grid.length * px
  const side = Math.floor(Math.random() * 4)
  const speed = 0.25 + Math.random() * 0.55
  let x, y, vx, vy
  if (side === 0) {
    x = -w - 10
    y = Math.random() * height
    vx = speed
    vy = (Math.random() - 0.5) * 0.2
  } else if (side === 1) {
    x = width + w + 10
    y = Math.random() * height
    vx = -speed
    vy = (Math.random() - 0.5) * 0.2
  } else if (side === 2) {
    x = Math.random() * width
    y = -h - 10
    vy = speed
    vx = (Math.random() - 0.5) * 0.2
  } else {
    x = Math.random() * width
    y = height + h + 10
    vy = -speed
    vx = (Math.random() - 0.5) * 0.2
  }
  floaters.push({
    shape,
    px,
    x,
    y,
    vx,
    vy,
    born: performance.now(),
    life: 7000 + Math.random() * 5000,
    bob: Math.random() * Math.PI * 2
  })
}

function drawAmbientGlow(now) {
  const cx = width / 2
  const cy = height / 2
  const r = Math.min(width, height) * 0.55
  const grad = ctx.createRadialGradient(cx, cy, 0, cx, cy, r)
  const pulse = 0.075 + 0.03 * Math.sin(now * 0.001)
  grad.addColorStop(0, `rgba(255, 255, 255, ${pulse})`)
  grad.addColorStop(0.6, 'rgba(255, 255, 255, 0.03)')
  grad.addColorStop(1, 'rgba(255, 255, 255, 0)')
  ctx.fillStyle = grad
  ctx.fillRect(0, 0, width, height)
}

function drawParticles(now) {
  for (const p of particles) {
    p.x += p.vx / width
    p.y += p.vy / height
    if (p.x < -0.02) p.x = 1.02
    if (p.x > 1.02) p.x = -0.02
    if (p.y < -0.02) p.y = 1.02
    if (p.y > 1.02) p.y = -0.02
    const alpha = 0.12 + 0.3 * (0.5 + 0.5 * Math.sin(now * 0.0012 + p.phase))
    ctx.fillStyle = `rgba(255, 255, 255, ${alpha})`
    ctx.beginPath()
    ctx.arc(p.x * width, p.y * height, p.size, 0, Math.PI * 2)
    ctx.fill()
  }
}

function drawFloaters(now) {
  for (let i = floaters.length - 1; i >= 0; i--) {
    const f = floaters[i]
    const age = now - f.born
    if (age > f.life) {
      floaters.splice(i, 1)
      continue
    }
    f.x += f.vx
    f.y += f.vy + Math.sin(now * 0.001 + f.bob) * 0.12

    const fadeIn = Math.min(1, age / 900)
    const fadeOut = Math.min(1, (f.life - age) / 1200)
    const alpha = Math.max(0, Math.min(fadeIn, fadeOut))

    // 图案柔光晕
    const hw = (f.shape.grid[0].length * f.px) / 2
    const hh = (f.shape.grid.length * f.px) / 2
    const halo = ctx.createRadialGradient(f.x + hw, f.y + hh, 0, f.x + hw, f.y + hh, Math.max(hw, hh) * 1.6)
    halo.addColorStop(0, `rgba(255, 255, 255, ${alpha * 0.16})`)
    halo.addColorStop(1, 'rgba(255, 255, 255, 0)')
    ctx.fillStyle = halo
    ctx.fillRect(f.x - hw, f.y - hh, hw * 2, hh * 2)

    ctx.fillStyle = `rgba(255, 255, 255, ${alpha})`
    f.shape.grid.forEach((row, r) => {
      row.forEach((cell, c) => {
        if (cell) {
          ctx.fillRect(f.x + c * f.px, f.y + r * f.px, f.px * 0.88, f.px * 0.88)
        }
      })
    })
  }
}

function spawnSparkle() {
  sparkles.push({
    x: width * (0.18 + Math.random() * 0.64),
    y: height * (0.25 + Math.random() * 0.5),
    born: performance.now(),
    life: 800 + Math.random() * 700
  })
}

function drawSparkles(now) {
  for (let i = sparkles.length - 1; i >= 0; i--) {
    const s = sparkles[i]
    const age = now - s.born
    if (age > s.life) {
      sparkles.splice(i, 1)
      continue
    }
    const t = age / s.life
    const alpha = Math.sin(t * Math.PI) * 0.9
    const r = 3 + t * 9
    ctx.strokeStyle = `rgba(255, 255, 255, ${alpha})`
    ctx.lineWidth = 1
    ctx.beginPath()
    ctx.moveTo(s.x - r, s.y)
    ctx.lineTo(s.x + r, s.y)
    ctx.moveTo(s.x, s.y - r)
    ctx.lineTo(s.x, s.y + r)
    ctx.stroke()
  }
}

function draw(now) {
  if (!width || !height) {
    resize()
  }
  ctx.clearRect(0, 0, width, height)
  drawAmbientGlow(now)
  drawParticles(now)

  // 随机刷新飘过的像素图案
  if (floaters.length < 5 && now > nextSpawnAt) {
    spawnFloater()
    nextSpawnAt = now + 1800 + Math.random() * 2600
  }
  drawFloaters(now)

  // 星光闪烁
  if (sparkles.length < 4 && now > nextSparkleAt) {
    spawnSparkle()
    nextSparkleAt = now + 1200 + Math.random() * 1800
  }
  drawSparkles(now)

  const scale = Math.min(width / TEXT_W, height / TEXT_H) * 0.92
  const ox = (width - TEXT_W * scale) / 2
  // 将 MOOD 整体上移一点，为居中的玻璃表单卡留出空间
  const oy = (height - TEXT_H * scale) / 2 - Math.min(80, height * 0.07)
  const REPEL = 100
  const LINK = 170

  for (const dot of dots) {
    const tx = ox + dot.baseX * TEXT_W * scale
    const ty = oy + dot.baseY * TEXT_H * scale
    if (!dot.x) {
      dot.x = tx
      dot.y = ty
    }

    // 弹簧回位
    dot.vx += (tx - dot.x) * 0.06 - dot.vx * 0.1
    dot.vy += (ty - dot.y) * 0.06 - dot.vy * 0.1

    // 鼠标斥力
    const mdx = dot.x - mouse.x
    const mdy = dot.y - mouse.y
    const dist = Math.hypot(mdx, mdy)
    if (dist < REPEL && dist > 0.001) {
      const force = ((REPEL - dist) / REPEL) * 1.6
      dot.vx += (mdx / dist) * force
      dot.vy += (mdy / dist) * force
    }

    dot.x += dot.vx
    dot.y += dot.vy
    dot.vx *= 0.93
    dot.vy *= 0.93

    // 鼠标附近连线
    if (dist < LINK && dist > 0.001) {
      const alpha = (1 - dist / LINK) * 0.35
      ctx.strokeStyle = `rgba(255, 255, 255, ${alpha})`
      ctx.lineWidth = 1
      ctx.beginPath()
      ctx.moveTo(dot.x, dot.y)
      ctx.lineTo(mouse.x, mouse.y)
      ctx.stroke()
    }

    ctx.fillStyle = 'rgba(255, 255, 255, 0.92)'
    ctx.beginPath()
    ctx.arc(dot.x, dot.y, 1.6, 0, Math.PI * 2)
    ctx.fill()
  }
  raf = requestAnimationFrame(draw)
}

function startLoop() {
  if (raf) return
  raf = requestAnimationFrame(draw)
}

function stopLoop() {
  cancelAnimationFrame(raf)
  raf = 0
}

function onMouseMove(e) {
  const rect = canvasRef.value.getBoundingClientRect()
  mouse.x = e.clientX - rect.left
  mouse.y = e.clientY - rect.top
}

function onMouseLeave() {
  mouse.x = -9999
  mouse.y = -9999
}

onMounted(() => {
  ctx = canvasRef.value.getContext('2d')
  buildDots()
  initParticles()
  nextSpawnAt = performance.now() + 1200
  nextSparkleAt = performance.now() + 800
  requestAnimationFrame(() => {
    resize()
    resizeObserver = new ResizeObserver(() => resize())
    resizeObserver.observe(canvasRef.value.parentElement)
    startLoop()
  })
  canvasRef.value.addEventListener('mousemove', onMouseMove)
  canvasRef.value.addEventListener('mouseleave', onMouseLeave)
  scheduleNext()
})

// keep-alive 缓存时暂停/恢复动画，保持画布状态不重建
onActivated(() => {
  if (ctx && canvasRef.value) {
    if (!width || !height) resize()
    startLoop()
  }
})

onDeactivated(() => {
  stopLoop()
  stopSloganTimer()
  // 离开登录页后重置弹窗状态，避免退出登录回来时弹窗又自动打开
  panel.value = null
  backdropDown.value = false
})

onBeforeUnmount(() => {
  stopLoop()
  stopSloganTimer()
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  if (canvasRef.value) {
    canvasRef.value.removeEventListener('mousemove', onMouseMove)
    canvasRef.value.removeEventListener('mouseleave', onMouseLeave)
  }
})
</script>

<template>
  <div class="auth-layout">
    <canvas ref="canvasRef" class="mood-canvas"></canvas>

    <div class="brand-block">
      <div class="brand-title">MindMan</div>
      <div class="brand-sub">心理健康助手</div>
      <p class="brand-slogan">每个深夜，每个焦虑的时刻，我们都在这里。</p>
    </div>

    <div class="hero-copy" @click="nextSlogan" style="cursor: pointer">
      <p class="hero-tagline" :class="{ 'fade-out': !sloganVisible, 'fade-in': sloganVisible }">
        {{ slogans[sloganIndex].zh }}
      </p>
      <p class="hero-tagline-en" :class="{ 'fade-out': !sloganVisible, 'fade-in': sloganVisible }">
        {{ slogans[sloganIndex].en }}
      </p>
    </div>

    <div class="auth-actions">
      <button class="glass-btn" @click="openPanel('login')">登录</button>
      <button class="glass-btn" @click="openPanel('register')">注册</button>
    </div>

    <Transition name="fade">
      <div
        v-if="panel"
        class="glass-modal"
        @mousedown="onBackdropMouseDown"
        @mouseup="onBackdropMouseUp"
      >
        <div class="glass-panel">
          <button class="panel-close" @click="panel = null">
            <el-icon><Close /></el-icon>
          </button>
          <LoginForm v-if="panel === 'login'" @switch="panel = 'register'" />
          <RegisterForm v-else @switch="panel = 'login'" />
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.auth-layout {
  position: relative;
  min-height: 100vh;
  background:
    radial-gradient(900px 620px at 78% 18%, rgba(84, 140, 241, 0.26), transparent 62%),
    radial-gradient(760px 540px at 12% 88%, rgba(29, 79, 165, 0.55), transparent 66%),
    linear-gradient(160deg, #0d1b3a 0%, #14306b 52%, #1d4fa5 100%);
}

.mood-canvas {
  position: fixed;
  inset: 0;
  display: block;
  z-index: 0;
}

.brand-block {
  position: fixed;
  top: 38px;
  left: 46px;
  z-index: 2;
}

.brand-title {
  font-size: 18px;
  font-weight: 600;
  color: #ffffff;
  letter-spacing: 2px;
  margin-bottom: 4px;
}

.brand-sub {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.65);
  letter-spacing: 1.5px;
  margin-bottom: 10px;
}

.brand-slogan {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.55);
  margin: 0;
}

/* 衬线体点缀标语（参考 Lassie 官网的标题手法） */
.hero-copy {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 128px;
  z-index: 2;
  text-align: center;
  padding: 0 24px;
}

.hero-tagline {
  font-family: 'Songti SC', 'STSong', 'Noto Serif SC', serif;
  font-size: 22px;
  font-weight: 300;
  letter-spacing: 6px;
  background: linear-gradient(120deg, #ffffff 0%, #e0eaff 50%, #ffffff 100%);
  background-size: 200% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  color: transparent;
  margin: 0 0 12px;
  text-shadow: 0 0 24px rgba(96, 165, 250, 0.18);
  transition: opacity 0.6s ease, transform 0.6s ease, letter-spacing 0.6s ease;
}

.hero-tagline-en {
  font-family: 'Georgia', 'Times New Roman', 'Songti SC', serif;
  font-style: italic;
  font-size: 14px;
  font-weight: 400;
  letter-spacing: 2px;
  color: rgba(255, 255, 255, 0.78);
  margin: 0;
  transition: opacity 0.6s ease, transform 0.6s ease;
}

.hero-tagline.fade-in,
.hero-tagline-en.fade-in {
  opacity: 1;
  transform: translateY(0);
}

.hero-tagline.fade-out,
.hero-tagline-en.fade-out {
  opacity: 0;
  transform: translateY(10px);
}

.slogan-dots {
  display: flex;
  gap: 6px;
  margin-top: 14px;
}
.slogan-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.28);
  cursor: pointer;
  transition: all 0.3s ease;
}
.slogan-dot.active {
  background: rgba(255, 255, 255, 0.92);
  width: 18px;
  border-radius: 999px;
}

/* 底部毛玻璃按钮 */
.auth-actions {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 40px;
  z-index: 2;
  display: flex;
  justify-content: center;
  gap: 16px;
  padding: 0 20px;
}

.glass-btn {
  position: relative;
  min-width: 170px;
  padding: 15px 50px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.85);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  color: #1d3a72;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 6px;
  text-indent: 6px;
  cursor: pointer;
  transition: all 0.25s ease;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.9),
    0 14px 36px rgba(2, 10, 32, 0.3);
}

.glass-btn:hover {
  transform: translateY(-2px);
  background: #2f6fdb;
  color: #ffffff;
  border-color: #2f6fdb;
  box-shadow: 0 18px 44px rgba(47, 111, 219, 0.45);
}

.glass-btn:active {
  transform: translateY(0);
}

/* 深色毛玻璃弹窗 */
.glass-modal {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(3, 12, 36, 0.35);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.glass-panel {
  position: relative;
  width: 100%;
  max-width: 420px;
  max-height: calc(100vh - 72px);
  overflow-y: auto;
  padding: 44px 42px 38px;
  border-radius: 30px;
  background: linear-gradient(165deg, rgba(255, 255, 255, 0.15) 0%, rgba(255, 255, 255, 0.06) 100%);
  backdrop-filter: blur(30px) saturate(1.6);
  -webkit-backdrop-filter: blur(30px) saturate(1.6);
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.24),
    0 40px 110px rgba(1, 8, 28, 0.6);
  color: #ffffff;
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 255, 255, 0.25) transparent;
}

.glass-panel::before {
  content: '';
  position: absolute;
  top: 0;
  left: 30px;
  right: 30px;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.55), transparent);
}

.glass-panel::-webkit-scrollbar {
  width: 5px;
}

.glass-panel::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.22);
  border-radius: 99px;
}

.glass-panel::-webkit-scrollbar-track {
  background: transparent;
}

.panel-close {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.3);
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  color: rgba(255, 255, 255, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.panel-close:hover {
  background: rgba(255, 255, 255, 0.2);
  color: #ffffff;
}

/* 深色玻璃弹窗内的表单样式 */
.glass-panel :deep(.auth-title) {
  color: #ffffff;
}

.glass-panel :deep(.auth-subtitle) {
  color: rgba(255, 255, 255, 0.6);
}

.glass-panel :deep(.el-form-item__label) {
  color: rgba(255, 255, 255, 0.82);
}

.glass-panel :deep(.el-input__wrapper) {
  --el-input-icon-color: rgba(255, 255, 255, 0.55);
  --el-input-placeholder-color: rgba(255, 255, 255, 0.42);
  --el-input-text-color: #ffffff;
  --el-input-border-color: transparent;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.22) inset;
  transition: box-shadow 0.2s;
}

.glass-panel :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.34) inset;
}

.glass-panel :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.65) inset,
    0 0 0 4px rgba(255, 255, 255, 0.1);
}

.glass-panel :deep(.el-input__inner) {
  color: #ffffff;
}

.glass-panel :deep(.el-input__inner) {
  -webkit-text-fill-color: #ffffff;
  caret-color: #ffffff;
}

/* 浏览器自动填充会把输入框涂成浅蓝色，这里强制保持深色玻璃 */
.glass-panel :deep(.el-input__wrapper input:-webkit-autofill),
.glass-panel :deep(.el-input__wrapper input:-webkit-autofill:hover),
.glass-panel :deep(.el-input__wrapper input:-webkit-autofill:focus) {
  -webkit-text-fill-color: #ffffff;
  -webkit-box-shadow: 0 0 0 1000px #1b2d58 inset;
  caret-color: #ffffff;
  transition: background-color 9999s ease-in-out 0s;
}

.glass-panel :deep(.el-form-item__error) {
  color: #ffb3ab;
}

.glass-panel :deep(.auth-submit) {
  height: 48px;
  margin-top: 6px;
  font-size: 15px;
  letter-spacing: 4px;
  border: none;
  border-radius: 14px;
  color: #ffffff;
  background: linear-gradient(135deg, #5b92f6 0%, #2f6fdb 55%, #2459b3 100%);
  box-shadow:
    0 14px 34px rgba(26, 80, 180, 0.45),
    inset 0 1px 0 rgba(255, 255, 255, 0.25);
}

.glass-panel :deep(.auth-submit:hover),
.glass-panel :deep(.auth-submit:focus) {
  background: linear-gradient(135deg, #6da0f7 0%, #3a7be0 55%, #2a67c7 100%);
}

.glass-panel :deep(.auth-switch) {
  color: rgba(255, 255, 255, 0.65);
}

.glass-panel :deep(.auth-switch a) {
  color: #9fc0ff;
}

.glass-panel :deep(.auth-switch a:hover) {
  color: #c2d8ff;
}

/* 弹窗过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s ease;
}

.fade-enter-active .glass-panel,
.fade-leave-active .glass-panel {
  transition: transform 0.25s ease, opacity 0.25s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.fade-enter-from .glass-panel,
.fade-leave-to .glass-panel {
  transform: translateY(16px) scale(0.97);
  opacity: 0;
}

@media (max-width: 860px) {
  .brand-block {
    top: 24px;
    left: 22px;
  }

  .brand-slogan {
    display: none;
  }

  .glass-panel {
    padding: 36px 24px;
  }

  .glass-btn {
    min-width: 134px;
    padding: 14px 34px;
    font-size: 14px;
    letter-spacing: 4px;
    text-indent: 4px;
  }
}

/* iPhone 窄屏：UI 不变，仅压缩尺寸防止错乱 */
@media (max-width: 520px) {
  .brand-title {
    font-size: 15px;
  }

  .brand-sub {
    font-size: 10px;
  }

  .auth-actions {
    bottom: 30px;
    gap: 12px;
  }

  .glass-btn {
    min-width: 118px;
    padding: 13px 26px;
    font-size: 13px;
    letter-spacing: 3px;
    text-indent: 3px;
  }

  .glass-panel {
    padding: 30px 20px 28px;
    border-radius: 24px;
  }

  .mood-panel {
    flex-wrap: wrap;
    justify-content: center;
    max-width: calc(100vw - 24px);
  }

  .hero-copy {
    bottom: 104px;
  }

  .hero-tagline {
    font-size: 16px;
    letter-spacing: 3px;
  }

  .hero-tagline-en {
    font-size: 11px;
  }
}
</style>
