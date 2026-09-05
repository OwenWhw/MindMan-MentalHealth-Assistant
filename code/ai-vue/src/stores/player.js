import { ref } from 'vue'
import { defineStore } from 'pinia'

/**
 * 全局播放器 Store
 *
 * 白噪音：
 *  - preset 带 src → 真实音效循环（Mixkit 免费音效库）
 *  - preset 无 src → Web Audio 实时合成（AudioContext + BufferSource 循环 + 滤波/LFO）
 *
 * FloatPlayer 与 RelaxView 都消费这个 store，
 * 实现「切页后仍然在播、浮球统一控制」。
 */

// ═══ 白噪音预设（与 RelaxView 共用） ═══
// src 存在 → 用真实音效循环播放（本地 public/sounds/ 已内置，不再依赖外部 CDN）
// src 为空 → 用 Web Audio 实时合成
// blend 存在 → 真实音效 + Web Audio 垫底（可叠加空间氛围）
// gain 为响度基准（真实音效与合成引擎都按它缩放），整体已调大保证听感清晰
export const NOISE_PRESETS = [
  {
    id: 'rain', name: '雨声', desc: '淅淅沥沥的雨点声，让心静下来',
    cover: 'https://images.unsplash.com/photo-1519692933481-e162a57d6721?w=300&q=80',
    color: '#3b82f6', type: 'rain', gain: 0.7,
    src: '/sounds/rain.mp3'
  },
  {
    id: 'ocean', name: '海浪', desc: '潮起潮落，把烦恼冲刷到远方',
    cover: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=300&q=80',
    color: '#0ea5e9', type: 'ocean', gain: 0.7,
    src: '/sounds/ocean.mp3'
  },
  {
    id: 'forest', name: '森林', desc: '风声与虫鸣，自然的白噪音',
    cover: 'https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=300&q=80',
    color: '#10b981', type: 'forest', gain: 0.7,
    src: '/sounds/forest.mp3'
  },
  {
    id: 'fire', name: '壁炉', desc: '噼啪柴火声，温暖而安心',
    cover: 'https://images.unsplash.com/photo-1697834158336-705dbfa2cc9a?w=300&q=80',
    color: '#f97316', type: 'fire', gain: 1.3,
    src: '/sounds/fire.mp3'
  },
  {
    id: 'white', name: '纯白噪音', desc: '均匀遮盖杂音，专注工作学习',
    cover: 'https://images.unsplash.com/photo-1470252649378-9c29740c9fa8?w=300&q=80',
    color: '#a78bfa', type: 'white', gain: 0.22
  },
  {
    id: 'brown', name: '棕色噪音', desc: '低沉浑厚，最接近入睡环境',
    cover: 'https://images.unsplash.com/photo-1585314062340-f1a5a7c9328d?w=300&q=80',
    color: '#d97706', type: 'brown', gain: 0.6
  },
  {
    id: 'office', name: '办公室', desc: '键盘声、电话铃声，安静的办公氛围',
    cover: 'https://images.unsplash.com/photo-1497366754035-f200968a6e72?w=300&q=80',
    color: '#6366f1', type: 'office', gain: 0.7,
    src: '/sounds/office.mp3'
  },
  {
    id: 'coffee', name: '咖啡厅', desc: '人声与杯碟声，像在街角咖啡店',
    cover: 'https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=300&q=80',
    color: '#b45309', type: 'coffee', gain: 0.7,
    src: '/sounds/coffee.mp3'
  },
  {
    id: 'barber', name: '理发店', desc: '咔嚓咔嚓的剪发声，沉浸式沙龙白噪音',
    cover: 'https://images.unsplash.com/photo-1521590832167-7bcbfaa6381f?w=300&q=80',
    color: '#0d9488', type: 'barber', gain: 0.7,
    src: '/sounds/scissors.mp3'
  },
  {
    id: 'asmr', name: '掏耳 ASMR', desc: '棉签轻触的沙沙声，酥麻治愈放松',
    cover: 'https://images.unsplash.com/photo-1589365354848-78104c17f92d?w=300&q=80',
    color: '#db2777', type: 'asmr', gain: 0.7,
    src: '/sounds/cloth.mp3'
  },
  {
    id: 'wood', name: '木头声', desc: '笃笃的敲木声，沉稳安宁',
    cover: 'https://images.unsplash.com/photo-1611072337226-1140ab367200?w=300&q=80',
    color: '#92400e', type: 'wood', gain: 0.7,
    src: '/sounds/wood.mp3'
  },
  {
    id: 'library', name: '图书馆', desc: '沙沙的翻书声，沉浸书页之间',
    cover: 'https://images.unsplash.com/photo-1481627834876-b7833e8f5570?w=300&q=80',
    color: '#78716c', type: 'library', gain: 0.7,
    src: '/sounds/library.mp3'
  }
]

/** 音频引擎：所有节点在此 store 内创建 */
export const usePlayerStore = defineStore('player', () => {
  // ═══ 状态 ═══
  const visible = ref(false)        // 浮球是否显示
  const minimized = ref(true)       // 收起为小球
  const noiseId = ref(null)         // 当前白噪音
  const playing = ref(false)        // 是否正在播放
  const volume = ref(50)            // 默认音量 50%（居中）
  const pos = ref({ x: null, y: null }) // 浮球拖动位置（null=默认右下角）

  // 音频引用（不在响应式上）
  let audioCtx = null
  let currentStop = null            // 白噪音合成 stop 函数
  let activeGain = null             // 当前 master gain
  let noiseAudioEl = null            // 白噪音真实音效 audio 元素
  let blendStop = null              // 垫底白噪（blend）stop 函数
  let blendGain = null              // 垫底白噪 gain 节点（音量联动）

  // ═══ 白噪音合成（Web Audio） ═══
  function ensureCtx() {
    if (!audioCtx) audioCtx = new (window.AudioContext || window.webkitAudioContext)()
    if (audioCtx.state === 'suspended') audioCtx.resume()
    return audioCtx
  }

  function makeNoiseBuffer(ctx, type = 'white', seconds = 4) {
    const size = Math.floor(ctx.sampleRate * seconds)
    const buffer = ctx.createBuffer(1, size, ctx.sampleRate)
    const data = buffer.getChannelData(0)
    if (type === 'brown') {
      let last = 0
      for (let i = 0; i < size; i++) {
        const w = Math.random() * 2 - 1
        last = (last + 0.02 * w) / 1.02
        data[i] = last * 3.5
      }
    } else if (type === 'pink') {
      let b0 = 0, b1 = 0, b2 = 0, b3 = 0, b4 = 0, b5 = 0, b6 = 0
      for (let i = 0; i < size; i++) {
        const w = Math.random() * 2 - 1
        b0 = 0.99886 * b0 + w * 0.0555179
        b1 = 0.99332 * b1 + w * 0.0750759
        b2 = 0.96900 * b2 + w * 0.1538520
        b3 = 0.86650 * b3 + w * 0.3104856
        b4 = 0.55000 * b4 + w * 0.5329522
        b5 = -0.7616 * b5 - w * 0.0168980
        data[i] = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362) * 0.11
        b6 = w * 0.115926
      }
    } else {
      for (let i = 0; i < size; i++) data[i] = Math.random() * 2 - 1
    }
    return buffer
  }

  function attachLFO(ctx, target, { rate = 0.1, depth = 1, base = 0 }) {
    const lfo = ctx.createOscillator()
    const lfoGain = ctx.createGain()
    lfo.frequency.value = rate
    lfoGain.gain.value = depth
    lfo.connect(lfoGain)
    lfoGain.connect(target)
    lfo.start()
    return () => {
      try { lfo.stop(); lfo.disconnect(); lfoGain.disconnect() } catch { /* noop */ }
    }
  }

  function buildNoiseGraph(ctx, preset, masterGain) {
    const type = preset.type
    const buffer = makeNoiseBuffer(ctx, (type === 'fire' || type === 'brown') ? 'brown' : 'white', 4)

    if (type === 'white') {
      // 纯白噪音：加低通柔化，避免刺耳
      const src = ctx.createBufferSource(); src.buffer = buffer; src.loop = true
      const filter = ctx.createBiquadFilter(); filter.type = 'lowpass'; filter.frequency.value = 3000
      src.connect(filter); filter.connect(masterGain); src.start()
      return { stop: () => { try { src.stop(); src.disconnect(); filter.disconnect() } catch {} } }
    }

    if (type === 'brown') {
      const src = ctx.createBufferSource(); src.buffer = buffer; src.loop = true
      const filter = ctx.createBiquadFilter(); filter.type = 'lowpass'; filter.frequency.value = 1000
      src.connect(filter); filter.connect(masterGain); src.start()
      return { stop: () => { try { src.stop(); src.disconnect(); filter.disconnect() } catch {} } }
    }

    if (type === 'rain') {
      const s1 = ctx.createBufferSource(); s1.buffer = makeNoiseBuffer(ctx, 'white', 4); s1.loop = true
      const hp = ctx.createBiquadFilter(); hp.type = 'highpass'; hp.frequency.value = 1500
      const g1 = ctx.createGain(); g1.gain.value = 0.35
      s1.connect(hp); hp.connect(g1); g1.connect(masterGain); s1.start()
      const s2 = ctx.createBufferSource(); s2.buffer = makeNoiseBuffer(ctx, 'pink', 4); s2.loop = true
      const bp = ctx.createBiquadFilter(); bp.type = 'bandpass'; bp.frequency.value = 800; bp.Q.value = 0.5
      const g2 = ctx.createGain(); g2.gain.value = 0.5
      s2.connect(bp); bp.connect(g2); g2.connect(masterGain); s2.start()
      return { stop: () => {
        try { s1.stop(); s1.disconnect(); hp.disconnect(); g1.disconnect() } catch {}
        try { s2.stop(); s2.disconnect(); bp.disconnect(); g2.disconnect() } catch {}
      } }
    }

    if (type === 'ocean') {
      const src = ctx.createBufferSource(); src.buffer = buffer; src.loop = true
      const filter = ctx.createBiquadFilter(); filter.type = 'lowpass'; filter.frequency.value = 700
      const seaGain = ctx.createGain(); seaGain.gain.value = 0.4
      src.connect(filter); filter.connect(seaGain); seaGain.connect(masterGain)
      const c1 = attachLFO(ctx, filter.frequency, { rate: 0.08, depth: 500 })
      const c2 = attachLFO(ctx, seaGain.gain, { rate: 0.16, depth: 0.3, base: 0.4 })
      src.start()
      return { stop: () => {
        try { src.stop(); src.disconnect(); filter.disconnect(); seaGain.disconnect() } catch {}
        c1(); c2()
      } }
    }

    if (type === 'forest') {
      const src = ctx.createBufferSource(); src.buffer = buffer; src.loop = true
      const filter = ctx.createBiquadFilter(); filter.type = 'bandpass'; filter.frequency.value = 1200; filter.Q.value = 0.8
      const fGain = ctx.createGain(); fGain.gain.value = 0.5
      src.connect(filter); filter.connect(fGain); fGain.connect(masterGain)
      const c1 = attachLFO(ctx, filter.frequency, { rate: 0.05, depth: 600 })
      const c2 = attachLFO(ctx, fGain.gain, { rate: 0.1, depth: 0.15, base: 0.5 })
      src.start()
      return { stop: () => {
        try { src.stop(); src.disconnect(); filter.disconnect(); fGain.disconnect() } catch {}
        c1(); c2()
      } }
    }

    if (type === 'fire') {
      const s1 = ctx.createBufferSource(); s1.buffer = buffer; s1.loop = true
      const lp = ctx.createBiquadFilter(); lp.type = 'lowpass'; lp.frequency.value = 400
      s1.connect(lp); lp.connect(masterGain); s1.start()
      const s2 = ctx.createBufferSource(); s2.buffer = makeNoiseBuffer(ctx, 'white', 4); s2.loop = true
      const hp = ctx.createBiquadFilter(); hp.type = 'bandpass'; hp.frequency.value = 3500; hp.Q.value = 2
      const cGain = ctx.createGain(); cGain.gain.value = 0.05
      s2.connect(hp); hp.connect(cGain); cGain.connect(masterGain)
      const cleanup = attachLFO(ctx, cGain.gain, { rate: 9, depth: 0.04, base: 0.05 })
      s2.start()
      return { stop: () => {
        try { s1.stop(); s1.disconnect(); lp.disconnect() } catch {}
        try { s2.stop(); s2.disconnect(); hp.disconnect(); cGain.disconnect() } catch {}
        cleanup()
      } }
    }

    const src = ctx.createBufferSource(); src.buffer = buffer; src.loop = true
    src.connect(masterGain); src.start()
    return { stop: () => { try { src.stop(); src.disconnect() } catch {} } }
  }

  // ═══ 白噪音真实音效 audio ═══
  function ensureNoiseAudioEl() {
    if (!noiseAudioEl) {
      noiseAudioEl = new Audio()
      noiseAudioEl.loop = true
      noiseAudioEl.addEventListener('ended', () => { playing.value = false })
    }
    return noiseAudioEl
  }

  // ═══ Actions ═══
  /** 播放/暂停白噪音（RelaxView 卡片 / 浮球按钮调用）。 */
  function playNoise(preset) {
    // 正在播放同一种 → 暂停
    if (noiseId.value === preset.id && playing.value) {
      pauseNoiseAudio()
      playing.value = false
      return
    }
    stopNoiseAudio()

    noiseId.value = preset.id
    playing.value = true
    visible.value = true
    minimized.value = false

    // 真实音效优先
    if (preset.src) {
      const el = ensureNoiseAudioEl()
      if (el.src !== preset.src) {
        el.src = preset.src
        el.currentTime = 0
      }
      el.volume = ((preset.gain || 0.5) * volume.value) / 100
      el.play().then(() => { playing.value = true }).catch(() => { playing.value = false })

      // 叠加垫底白噪（如壁炉 + 微弱白噪，营造空间氛围）
      if (preset.blend) {
        try {
          const ctx = ensureCtx()
          const g = ctx.createGain()
          g.gain.value = (preset.blend.gain || 0.08) * (volume.value / 100)
          g.connect(ctx.destination)
          blendGain = g
          const graph = buildNoiseGraph(ctx, preset.blend, g)
          blendStop = () => {
            try { g.disconnect() } catch {}
            if (blendGain === g) blendGain = null
            graph.stop()
          }
        } catch (e) {
          console.warn('blend noise failed:', e)
        }
      }
      return
    }

    // Web Audio 合成兜底（纯白/棕色噪音）
    const ctx = ensureCtx()
    const masterGain = ctx.createGain()
    masterGain.gain.value = (preset.gain || 0.4) * (volume.value / 100)
    masterGain.connect(ctx.destination)
    activeGain = masterGain

    const graph = buildNoiseGraph(ctx, preset, masterGain)
    currentStop = () => {
      try { masterGain.disconnect() } catch {}
      if (activeGain === masterGain) activeGain = null
      graph.stop()
    }
  }

  /** 暂停白噪音（双路都清，防止残留声音） */
  function pauseNoiseAudio() {
    if (noiseAudioEl) { noiseAudioEl.pause() }
    if (currentStop) { currentStop(); currentStop = null }
    if (blendStop) { blendStop(); blendStop = null }
    if (activeGain) { activeGain = null }
  }

  /** 停止白噪音音频（彻底） */
  function stopNoiseAudio() {
    if (noiseAudioEl) { noiseAudioEl.pause(); noiseAudioEl.currentTime = 0 }
    if (currentStop) { currentStop(); currentStop = null }
    if (blendStop) { blendStop(); blendStop = null }
    if (activeGain) { activeGain = null }
  }

  /** 停止一切 */
  function stopAll() {
    stopNoiseAudio()
    noiseId.value = null
    playing.value = false
  }

  /** 音量调节（真实音效与合成引擎都用 preset.gain 缩放保持一致） */
  function setVolume(v) {
    volume.value = v
    const p = NOISE_PRESETS.find(x => x.id === noiseId.value)
    if (activeGain) activeGain.gain.value = ((p?.gain || 0.4) * v) / 100
    if (noiseAudioEl) {
      noiseAudioEl.volume = ((p?.gain || 0.5) * v) / 100
    }
    if (blendGain) {
      blendGain.gain.value = ((p?.blend?.gain || 0.08) * v) / 100
    }
  }

  /** 浮球收起/展开 */
  function toggleMinimized() { minimized.value = !minimized.value }

  /** 关闭浮球（停止播放 + 隐藏） */
  function closePlayer() {
    stopAll()
    visible.value = false
    minimized.value = true
  }

  /** 位置（保留接口兼容） */
  function setPos(x, y) { pos.value = { x, y } }

  return {
    visible, minimized, noiseId, playing, volume, pos,
    NOISE_PRESETS,
    playNoise, setVolume, toggleMinimized, closePlayer, setPos
  }
})