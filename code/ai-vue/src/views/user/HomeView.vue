<script setup>
import { ref, computed, onMounted, onActivated, onDeactivated } from 'vue'
import { useRouter, useRoute } from 'vue-router'

// 显式组件名（keep-alive include 匹配用）
defineOptions({ name: 'HomeView' })
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useEmotionStore } from '@/stores/emotion'
import { getArticlePage, getRecommendArticles } from '@/api/knowledge'
import { getMySessions } from '@/api/consult'
import { getGarden, getInsightThisWeek } from '@/api/emotion'
import { getRandomQuote } from '@/api/quote'
import AppNavBar from '@/components/AppNavBar.vue'
import UserDropdown from '@/components/UserDropdown.vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const emotionStore = useEmotionStore()

const displayName = computed(() => authStore.userInfo?.nickname || '用户')
const articles = ref([])
const loading = ref(false)

const navActions = [
  { key: 'consult', title: 'AI 咨询', icon: 'ChatDotRound', path: '/consult' },
  { key: 'garden',  title: '情绪花园', icon: 'Cherry',       path: '/garden' },
  { key: 'articles', title: '知识文章', icon: 'Collection', path: '/home/articles' },
  { key: 'relax',   title: '白噪音空间', icon: 'WindPower',  path: '/relax' },
  { key: 'home',    title: '回到主页', icon: 'HomeFilled',   path: '/home' }
]

// 时间问候
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

// ═══ 今日概览（真实数据，加载前为空） ═══
const overview = ref([])

// ═══ 今日心情 ═══
const moodList = ['开心', '平静', '感恩', '焦虑', '疲惫', '期待']
const todayMood = ref('')
function pickMood(m) {
  todayMood.value = m
  ElMessage.success(`已记录：${m} 🌿`)
}

// ═══ 情绪趋势（真实数据，加载前为空） ═══
const trendDays = ref([])
const trendValues = ref([])
const maxTrend = computed(() => {
  const arr = trendValues.value
  return arr.length ? Math.max(...arr) : 1
})
const avgTrend = computed(
  () => trendValues.value.reduce((s, v) => s + v, 0) / (trendValues.value.length || 1)
)
const avgPct = computed(() => (avgTrend.value / (maxTrend.value || 1)) * 100)

// 按情绪分值分档配色：良好绿 / 平稳蓝 / 偏低暖黄
function barGradient(v) {
  if (v >= 4) return 'linear-gradient(180deg, #6ee7b7 0%, #34d399 55%, #059669 100%)'
  if (v >= 3.5) return 'linear-gradient(180deg, #93c5fd 0%, #60a5fa 55%, #3b82f6 100%)'
  return 'linear-gradient(180deg, #fcd34d 0%, #fbbf24 55%, #f59e0b 100%)'
}

// ═══ 最近聊天（真实数据，加载前为空） ═══
const recentChats = ref([])

// 后端 LocalDateTime 序列化为 ISO（yyyy-MM-ddTHH:mm:ss），统一取时分秒展示
function formatTime(s) {
  if (!s) return ''
  const t = String(s).replace('T', ' ')
  return t.slice(11, 19) || t
}

// 相对时间：刚刚 / N分钟前 / N小时前 / 昨天 / N天前
function relativeTime(s) {
  if (!s) return ''
  const dt = new Date(String(s).replace('T', ' '))
  if (isNaN(dt.getTime())) return ''
  const now = new Date()
  const diff = Math.floor((now - dt) / 1000)
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`
  if (diff < 172800) return '昨天'
  if (diff < 604800) return `${Math.floor(diff / 86400)} 天前`
  return `${dt.getMonth() + 1}月${dt.getDate()}日`
}

// 拉取真实数据：情绪花园 → 趋势/概览；我的会话 → 最近聊天
async function loadDashboard() {
  try {
    const [garden, sess] = await Promise.all([
      getGarden().catch(() => []),
      getMySessions({ page: 1, pageSize: 20 }).catch(() => [])
    ])
    const flowers = Array.isArray(garden) ? garden : []
    const sessList = Array.isArray(sess) ? sess : (sess?.list || [])

    // 趋势：按日期聚合情绪分，取最近 7 天
    const byDate = {}
    flowers.forEach((f) => {
      const d = f.date || String(f.createdAt || '').slice(0, 10)
      if (!d) return
      if (!byDate[d]) byDate[d] = { sum: 0, n: 0 }
      byDate[d].sum += Number(f.emotionScore) || 0
      byDate[d].n += 1
    })
    const dates = Object.keys(byDate).sort().slice(-7)
    const wk = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
    trendValues.value = dates.map((d) => +(byDate[d].sum / byDate[d].n).toFixed(1))
    trendDays.value = dates.map((d) => wk[new Date(d.replace(/-/g, '/')).getDay()])

    // 概览
    const avgEmo = flowers.length
      ? flowers.reduce((s, f) => s + (Number(f.emotionScore) || 0), 0) / flowers.length
      : null
    const avgStress = flowers.length
      ? flowers.reduce((s, f) => s + (Number(f.stressScore) || 0), 0) / flowers.length
      : null
    const stressLevel = avgStress == null ? '—' : avgStress < 2 ? '偏低' : avgStress <= 3 ? '中等' : '偏高'

    // 连续记录天数：从今天往前数连续有记录的日期
    const dateSet = new Set(Object.keys(byDate))
    const today = new Date()
    const todayStr = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
    let streak = 0
    let cursor = new Date(today)
    // 如果今天没记录，从昨天开始往前数
    if (!dateSet.has(todayStr)) {
      cursor.setDate(cursor.getDate() - 1)
    }
    while (true) {
      const s = `${cursor.getFullYear()}-${String(cursor.getMonth() + 1).padStart(2, '0')}-${String(cursor.getDate()).padStart(2, '0')}`
      if (dateSet.has(s)) {
        streak++
        cursor.setDate(cursor.getDate() - 1)
      } else {
        break
      }
    }

    overview.value = [
      { label: '情绪评分', value: avgEmo == null ? '—' : avgEmo.toFixed(1), unit: '/5', icon: 'Sunny', color: '#f59e0b' },
      { label: '连续记录', value: String(streak), unit: '天', icon: 'Fire', color: '#f97316' },
      { label: '累计咨询', value: String(sess?.total || sessList.length || 0), unit: '次', icon: 'ChatDotRound', color: '#6366f1' },
      { label: '压力指数', value: stressLevel, unit: '', icon: 'Warning', color: '#ef4444' }
    ]

    // 最近聊天
    recentChats.value = sessList.slice(0, 4).map((s) => ({
      id: s.id,
      title: s.title || '咨询会话',
      time: relativeTime(s.updatedAt),
      excerpt: s.lastMessagePreview || '开始一次新的对话吧',
      status: s.status,
      statusText: s.statusText || (s.status === 2 ? '已结束' : '进行中'),
      messageCount: s.messageCount || 0
    }))
  } catch (e) {
    // 保留默认占位，首页不崩溃
  }
}

// ═══ AI 洞察 ═══（基于真实数据异步拉取）
const insight = ref({ summary: '', highlights: [] })

async function loadInsight() {
  try {
    const res = await getInsightThisWeek()
    if (res) insight.value = res
  } catch (e) {
    console.warn('loadInsight failed:', e?.message)
  }
}

// ═══ 每日一句 ═══
const dailyQuote = ref({
  content: '不必匆忙，不必火花四溅，不必成为别人，只需做自己。',
  translation: "You don't have to be on fire. Just be yourself.",
  author: '弗吉尼亚·伍尔夫',
  source: null
})
const quoteChanging = ref(false)
let quoteTimer = null
const QUOTE_INTERVAL = 5 * 60 * 1000 // 5 分钟自动切换一次

async function changeQuote() {
  if (quoteChanging.value) return
  quoteChanging.value = true
  try {
    const data = await getRandomQuote()
    if (data?.content) {
      dailyQuote.value = {
        content: data.content,
        translation: data.translation || dailyQuote.value.translation,
        author: data.author || '佚名',
        source: data.source || null
      }
    }
  } catch (e) {
    // 失败时保持当前语句，不打扰用户
  } finally {
    setTimeout(() => (quoteChanging.value = false), 400)
  }
}

function startQuoteAutoPlay() {
  stopQuoteAutoPlay()
  quoteTimer = setInterval(changeQuote, QUOTE_INTERVAL)
}

function stopQuoteAutoPlay() {
  if (quoteTimer) {
    clearInterval(quoteTimer)
    quoteTimer = null
  }
}

const aiRecs = ref([])

// 估算阅读时长：按 content 字符数 / 300 取上限，例如 "X 分钟阅读"
function estReadingMinutes(article) {
  const len = ((article.summary || '') + (article.content || '')).length || 600
  const mins = Math.max(3, Math.min(20, Math.ceil(len / 300)))
  return `${mins} 分钟阅读`
}

async function loadRecommendations() {
  try {
    const res = await getRecommendArticles(4)
    const list = Array.isArray(res) ? res : []
    aiRecs.value = list.map((a) => ({
      id: a.articleId,
      title: a.title,
      tag: a.categoryName || '心理',
      summary: a.summary,
      cover: a.cover,
      sourceType: a.sourceType,
      sourceName: a.sourceName,
      time: estReadingMinutes(a)
    }))
  } catch (e) {
    console.warn('loadRecommendations failed:', e?.message)
    aiRecs.value = []
  }
}

function openArticle(id) {
  if (!id) return
  router.push(`/home/articles/${id}`)
}

async function loadArticles() {
  try {
    const data = await getArticlePage({ page: 1, pageSize: 4 })
    articles.value = data.list
  } catch (e) { articles.value = [] }
}
function goArticles() { router.push('/home/articles') }
function goGarden() { router.push('/garden') }

// 首次挂载 + 每次从其他页面返回（keep-alive 激活）都触发静默刷新
// 注意：onMounted 仅在首次挂载触发，onActivated 在每次从缓存激活触发
// 必须两者都监听，否则首次进入看不到数据（清占位后空数组不会自动加载）
onMounted(async () => {
  loadArticles()
  loadRecommendations()
  loadInsight()
  await loadDashboard()
  changeQuote()
  startQuoteAutoPlay()
})

onActivated(() => {
  loadArticles()
  loadRecommendations()
  loadInsight()
  loadDashboard()
  changeQuote()
  startQuoteAutoPlay()
})

onDeactivated(() => {
  stopQuoteAutoPlay()
})
</script>

<template>
  <div class="home">
    <!-- 顶部导航 -->
    <AppNavBar :actions="navActions" :current-path="route.path">
      <template #actions-after>
        <UserDropdown />
      </template>
    </AppNavBar>

    <!-- ═══ Hero ═══ -->
    <section class="hero">
      <div class="glow glow-right"></div>
      <div class="glow glow-left"></div>
      <div class="blob-float blob-a"></div>
      <div class="blob-float blob-b"></div>

      <div class="hero-copy">
        <span class="hero-tag">MINDMAN</span>
        <h1 class="hero-greet">{{ greeting }}，{{ displayName }}</h1>
        <p class="hero-sub">今天感觉怎么样？让我们一起关注你的心理状态。</p>
        <div class="hero-actions">
          <button class="hero-btn primary" @click="router.push('/consult')">开始咨询</button>
          <button class="hero-btn ghost" @click="goGarden">记录心情</button>
        </div>
      </div>

      <div class="hero-visual">
        <div class="mock-chat">
          <div class="mock-head">
            <span class="mock-avatar">M</span>
            <div class="mock-head-info">
              <div class="mock-name">MindMan</div>
              <div class="mock-status"><span class="mock-dot"></span>在线</div>
            </div>
          </div>
          <div class="mock-bubble mock-user">最近总是失眠，该怎么办？</div>
          <div class="mock-bubble mock-ai">别担心，我们先从一次深呼吸开始…</div>
        </div>
        <div class="mock-chip chip-one"><el-icon><Cherry /></el-icon>情绪花园</div>
        <div class="mock-chip chip-two"><el-icon><ChatDotRound /></el-icon>AI 陪伴</div>
      </div>
    </section>

    <!-- ═══ 今日概览 ═══ -->
    <section class="overview-section">
      <h2 class="sec-title">今日概览</h2>
      <div class="overview-grid">
        <div v-for="o in overview" :key="o.label" class="ov-card">
          <div class="ov-row">
            <div class="ov-icon" :style="{ background: o.color + '18', color: o.color }">
              <el-icon><component :is="o.icon" /></el-icon>
            </div>
            <span class="ov-label">{{ o.label }}</span>
          </div>
          <div class="ov-value">
            <span class="ov-num">{{ o.value }}</span>
            <span class="ov-unit">{{ o.unit }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- ═══ 快捷入口 + AI 推荐 ═══ -->
    <div class="dual-section">
      <section class="quick-section">
        <h2 class="sec-title">快捷入口</h2>
        <div class="quick-grid">
          <div class="qk-card" @click="router.push('/consult')">
            <el-icon><ChatDotRound /></el-icon><span>AI 咨询</span>
          </div>
          <div class="qk-card" @click="goGarden">
            <el-icon><Cherry /></el-icon><span>情绪花园</span>
          </div>
          <div class="qk-card" @click="goArticles">
            <el-icon><Collection /></el-icon><span>知识文章</span>
          </div>
          <div class="qk-card qk-relax" @click="router.push('/relax')">
            <el-icon><WindPower /></el-icon><span>白噪音空间</span>
          </div>
        </div>
      </section>

      <section class="rec-section">
        <h2 class="sec-title">AI 推荐</h2>
        <div class="rec-list">
          <div v-for="r in aiRecs" :key="r.id || r.title" class="rec-item" @click="openArticle(r.id)">
            <span class="rec-tag" :class="{ 'rec-tag-live': r.sourceType === 'crawled' }">
              {{ r.sourceType === 'crawled' ? '🛰 实时 · ' + (r.sourceName || '实时心理') : r.tag }}
            </span>
            <span class="rec-title">{{ r.title }}</span>
            <span class="rec-time">{{ r.time }}</span>
          </div>
          <div v-if="!aiRecs.length" class="rec-empty">
            暂时没有推荐内容，稍候再来看看吧 ☕️
          </div>
        </div>
      </section>
    </div>

    <!-- ═══ 情绪趋势 ═══ -->
    <section class="trend-section">
      <div class="sec-head">
        <h2 class="sec-title">本周情绪趋势</h2>
        <span v-if="trendValues.length" class="trend-note">本周均值 {{ avgTrend.toFixed(1) }}</span>
      </div>
      <div v-if="trendValues.length" class="trend-chart">
        <div class="trend-plot">
          <div class="avg-line" :style="{ bottom: avgPct + '%' }">
            <span class="avg-label">均值 {{ avgTrend.toFixed(1) }}</span>
          </div>
          <div v-for="(v, i) in trendValues" :key="i" class="trend-col">
            <div class="trend-track">
              <div
                class="trend-bar"
                :style="{
                  height: (v / maxTrend) * 100 + '%',
                  background: barGradient(v),
                  animationDelay: i * 0.06 + 's'
                }"
              >
                <span class="trend-val">{{ v }}</span>
              </div>
            </div>
          </div>
        </div>
        <div class="trend-days">
          <span v-for="(d, i) in trendDays" :key="d" class="trend-day" :class="{ high: trendValues[i] >= 4 }">
            {{ d }}
          </span>
        </div>
      </div>
      <div v-else class="trend-empty">本周还没有情绪记录，去情绪花园种下一朵花吧 🌱</div>
      <div class="trend-legend">
        <span><i class="dot" style="background: #34d399"></i>良好 ≥ 4</span>
        <span><i class="dot" style="background: #60a5fa"></i>平稳 3.5–4</span>
        <span><i class="dot" style="background: #f59e0b"></i>偏低 &lt; 3.5</span>
      </div>
    </section>

    <!-- ═══ 最近聊天 + AI 洞察 ═══ -->
    <div class="dual-section">
      <section class="recent-section">
        <div class="recent-header">
          <h2 class="sec-title">最近聊天</h2>
          <router-link to="/consult" class="recent-more">查看全部 <el-icon><ArrowRight /></el-icon></router-link>
        </div>
        <div v-if="loading" class="recent-loading">
          <el-icon class="is-loading"><Loading /></el-icon>
        </div>
        <div v-else-if="!recentChats.length" class="recent-empty">
          <div class="recent-empty-icon">💬</div>
          <p class="recent-empty-title">还没有咨询记录</p>
          <p class="recent-empty-desc">和 AI 咨询师聊聊，释放一下情绪吧</p>
          <router-link to="/consult" class="recent-empty-btn">开始倾诉</router-link>
        </div>
        <div v-else class="recent-list">
          <div v-for="c in recentChats" :key="c.id" class="recent-item" @click="router.push(`/consult?session=${c.id}`)">
            <div class="recent-head">
              <div class="recent-title-wrap">
                <span class="recent-name">{{ c.title }}</span>
                <span class="recent-status" :class="{ archived: c.status === 2 }">{{ c.statusText }}</span>
              </div>
              <span class="recent-time">{{ c.time }}</span>
            </div>
            <p class="recent-excerpt">{{ c.excerpt }}</p>
            <div class="recent-meta">
              <span class="recent-count">{{ c.messageCount }} 条消息</span>
            </div>
          </div>
        </div>
      </section>

      <section class="insight-section">
        <h2 class="sec-title">AI 本周洞察</h2>
        <div class="insight-card">
          <p v-if="insight.summary" class="insight-text">{{ insight.summary }}</p>
          <p v-else class="insight-text insight-empty">本周还没有足够的数据来生成洞察，去情绪花园记录一次吧 🌱</p>
          <div v-if="insight.highlights?.length" class="insight-tags">
            <span v-for="h in insight.highlights" :key="h" class="insight-tag">{{ h }}</span>
          </div>
        </div>
      </section>
    </div>

    <!-- ═══ 每日一句 ═══ -->
    <section class="quote-section">
      <div class="quote-card">
        <div class="quote-body" :class="{ changing: quoteChanging }">
          <p class="quote-zh">"{{ dailyQuote.content }}"</p>
          <p v-if="dailyQuote.translation" class="quote-en">{{ dailyQuote.translation }}</p>
          <p class="quote-author">
            — {{ dailyQuote.author }}
            <span v-if="dailyQuote.source" class="quote-source">《{{ dailyQuote.source }}》</span>
          </p>
        </div>
        <div class="quote-hint">
          <button class="quote-refresh-btn" @click="changeQuote">
            <el-icon><Refresh /></el-icon>
            <span>换一个</span>
          </button>
          <div class="quote-status">
            <span class="quote-dot" :class="{ active: !quoteChanging }"></span>
            <span class="quote-timer">每 5 分钟自动切换</span>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home { max-width: 1180px; margin: 0 auto; padding: 96px 32px 80px; }

/* ═══════════════════════════
   Shared
   ═══════════════════════════ */
.sec-title {
  font-size: 22px; font-weight: 700; color: #111827;
  margin-bottom: 18px; letter-spacing: 0.3px;
}

/* ═══════════════════════════
   Hero
   ═══════════════════════════ */
.hero {
  position: relative; display: grid; grid-template-columns: 1fr 1.15fr;
  align-items: center; gap: 60px; margin-top: 36px;
  padding: 72px 64px 64px; border-radius: 32px;
  background: linear-gradient(170deg, #f0f7ff 0%, #fafcff 40%, #ffffff 90%);
  border: 1px solid rgba(226,232,240,0.7); overflow: hidden;
}
.glow { position: absolute; border-radius: 50%; filter: blur(140px); pointer-events: none; z-index: 0; }
.glow-right { width: 520px; height: 520px; right: -120px; top: -160px; background: rgba(47,111,219,0.10); }
.glow-left  { width: 400px; height: 400px; left: -100px; bottom: -140px; background: rgba(99,102,241,0.07); }
.blob-float { position: absolute; border-radius: 50%; pointer-events: none; z-index: 0; animation: blobDrift 12s ease-in-out infinite; }
.blob-a { width: 180px; height: 180px; right: 42%; top: -60px; background: radial-gradient(circle, rgba(147,197,253,0.28), transparent 70%); filter: blur(40px); }
.blob-b { width: 140px; height: 140px; left: 48%; bottom: -50px; background: radial-gradient(circle, rgba(167,139,250,0.20), transparent 70%); filter: blur(40px); animation-delay: 4s; }
@keyframes blobDrift {
  0%,100% { transform: translate(0,0) scale(1); }
  25%     { transform: translate(18px,-12px) scale(1.08); }
  50%     { transform: translate(-10px,-20px) scale(0.94); }
  75%     { transform: translate(-16px,8px) scale(1.04); }
}
.hero-copy { position: relative; z-index: 1; }
.hero-tag {
  display: inline-block; font-size: 11px; font-weight: 600;
  letter-spacing: 2.5px; color: #2f6fdb; text-transform: uppercase;
  padding: 6px 14px; border-radius: 999px; background: rgba(47,111,219,0.08); margin-bottom: 16px;
}
.hero-greet { font-size: 42px; font-weight: 700; line-height: 1.28; color: #111827; letter-spacing: 0.3px; margin: 0 0 12px; }
.hero-sub { font-size: 18px; line-height: 1.7; color: #64748b; margin: 0 0 28px; font-weight: 400; }
.hero-actions { display: flex; gap: 14px; }
.hero-btn { height: 50px; padding: 0 28px; border-radius: 999px; font-size: 15px; font-weight: 600; letter-spacing: 0.5px; cursor: pointer; transition: all 0.25s; border: none; }
.hero-btn.primary { background: linear-gradient(135deg,#2563eb,#3b82f6); color: #fff; box-shadow: 0 4px 20px rgba(37,99,235,0.32); }
.hero-btn.primary:hover { transform: scale(1.03); box-shadow: 0 6px 28px rgba(37,99,235,0.4); }
.hero-btn.ghost { background: #fff; color: #374151; border: 1px solid rgba(209,213,219,0.8); box-shadow: 0 1px 3px rgba(0,0,0,0.04); }
.hero-btn.ghost:hover { border-color: #3b82f6; color: #2563eb; transform: scale(1.03); }

.hero-visual { position: relative; z-index: 1; display: flex; align-items: center; justify-content: center; }
.mock-chat {
  width: 370px; padding: 22px 20px 24px; background: rgba(255,255,255,0.65);
  backdrop-filter: blur(18px); border-radius: 22px; border: 1px solid rgba(255,255,255,0.8);
  box-shadow: 0 20px 60px rgba(47,111,219,0.14); animation: floaty 6s ease-in-out infinite;
}
@keyframes floaty { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-8px); } }
.mock-head { display: flex; align-items: center; gap: 11px; padding-bottom: 14px; margin-bottom: 14px; border-bottom: 1px solid #f0f1f4; }
.mock-avatar { width: 38px; height: 38px; border-radius: 12px; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg,#2563eb,#3b82f6); color: #fff; font-weight: 700; font-size: 14px; }
.mock-name { font-size: 14px; font-weight: 700; color: #111827; }
.mock-status { display: flex; align-items: center; gap: 5px; font-size: 11px; color: #94a3b8; margin-top: 2px; }
.mock-dot { width: 8px; height: 8px; border-radius: 50%; background: #34c98a; animation: pulse 2s infinite; }
@keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.5; } }
.mock-bubble { font-size: 13px; line-height: 1.6; padding: 11px 14px; border-radius: 14px; width: fit-content; max-width: 85%; }
.mock-user { margin-left: auto; background: linear-gradient(135deg,#2563eb,#4f46e5); color: #fff; border-bottom-right-radius: 5px; margin-bottom: 12px; }
.mock-ai { background: #f5f7fb; color: #1f2937; border: 1px solid #e9eef7; border-bottom-left-radius: 5px; }
.mock-chip {
  position: absolute; display: inline-flex; align-items: center; gap: 6px;
  padding: 7px 14px; border-radius: 999px; background: rgba(255,255,255,0.75);
  backdrop-filter: blur(12px); border: 1px solid rgba(226,232,240,0.6);
  box-shadow: 0 8px 24px rgba(0,0,0,0.06); font-size: 11px; color: #475569; animation: floaty 5s ease-in-out infinite;
}
.chip-one { top: 16px; left: -6px; animation-delay: 0.6s; }
.chip-two { bottom: 22px; right: -2px; animation-delay: 1.4s; }

/* ═══════════════════════════
   Today Overview
   ═══════════════════════════ */
.overview-section { margin-top: 52px; }
.overview-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.ov-card {
  padding: 20px 18px; border-radius: 18px;
  background: rgba(255,255,255,0.7); backdrop-filter: blur(18px);
  border: 1px solid rgba(226,232,240,0.7);
  box-shadow: 0 4px 16px rgba(0,0,0,0.03);
  transition: transform 0.25s, box-shadow 0.25s;
}
.ov-card:hover { transform: translateY(-4px); box-shadow: 0 10px 28px rgba(47,111,219,0.08); }
.ov-row { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.ov-icon { width: 36px; height: 36px; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 16px; }
.ov-label { font-size: 13px; color: #64748b; font-weight: 500; }
.ov-value { display: flex; align-items: baseline; gap: 3px; }
.ov-num { font-size: 30px; font-weight: 800; color: #111827; letter-spacing: -1px; }
.ov-unit { font-size: 13px; color: #94a3b8; }

/* ═══════════════════════════
   Mood
   ═══════════════════════════ */
.mood-section { margin-top: 48px; }
.mood-recorded { color: #059669; font-size: 14px; font-weight: 400; }
.mood-hint { color: #94a3b8; font-size: 13px; font-weight: 400; }
.mood-pills { display: flex; flex-wrap: wrap; gap: 10px; }
.mood-pill {
  padding: 8px 20px; border-radius: 999px; border: 1px solid rgba(209,213,219,0.8);
  background: rgba(255,255,255,0.6); color: #475569; font-size: 14px;
  cursor: pointer; transition: all 0.22s; font-weight: 500;
}
.mood-pill:hover { border-color: #93c5fd; color: #2563eb; transform: translateY(-2px); }
.mood-pill.picked { background: #2563eb; color: #fff; border-color: #2563eb; box-shadow: 0 2px 12px rgba(37,99,235,0.3); }

/* ═══════════════════════════
   Dual section (quick + rec, recent + insight)
   ═══════════════════════════ */
.dual-section { display: grid; grid-template-columns: 1fr 1fr; gap: 40px; margin-top: 48px; }

.quick-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.qk-card {
  display: flex; align-items: center; gap: 10px; padding: 16px 18px;
  border-radius: 14px; background: rgba(255,255,255,0.7); backdrop-filter: blur(18px);
  border: 1px solid rgba(226,232,240,0.7); font-size: 14px; font-weight: 600;
  color: #374151; cursor: pointer; transition: all 0.25s;
}
.qk-card:hover { transform: translateY(-4px); box-shadow: 0 8px 24px rgba(47,111,219,0.08); border-color: #93c5fd; }
.qk-card .el-icon { font-size: 18px; color: #3b82f6; }
.qk-card.qk-relax .el-icon { color: #8b5cf6; }
.qk-card.disable { opacity: 0.45; cursor: default; }
.qk-card.disable:hover { transform: none; box-shadow: none; border-color: rgba(226,232,240,0.7); }

.rec-list { display: flex; flex-direction: column; gap: 10px; }
.rec-item {
  display: flex; align-items: center; gap: 12px;
  padding: 15px 18px; border-radius: 14px; background: #fff;
  border: 1px solid #eceef1; cursor: pointer; transition: all 0.22s;
}
.rec-item:hover { border-color: rgba(59,130,246,0.3); box-shadow: 0 6px 18px rgba(17,24,39,0.05); transform: translateY(-2px); }
.rec-tag { font-size: 11px; padding: 3px 10px; border-radius: 999px; background: rgba(59,130,246,0.08); color: #3b82f6; flex-shrink: 0; }
.rec-tag-live {
  background: rgba(16,185,129,0.10); color: #059669;
  font-weight: 600;
  border: 1px solid rgba(16,185,129,0.25);
}
.rec-title { flex: 1; font-size: 14px; color: #1f2937; font-weight: 500; }
.rec-time { font-size: 12px; color: #9ca3af; flex-shrink: 0; }
.rec-empty { padding: 24px; text-align: center; color: #9ca3af; font-size: 13px; background: #fff; border-radius: 14px; border: 1px dashed #e5e7eb; }

/* ═══════════════════════════
   Trend
   ═══════════════════════════ */
.trend-section { margin-top: 48px; }

.sec-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 16px;
}

.trend-note {
  font-size: 12px;
  color: #94a3b8;
}

.trend-chart {
  padding: 18px 20px 14px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(18px);
  border: 1px solid rgba(226, 232, 240, 0.7);
  box-shadow: 0 12px 34px rgba(47, 111, 219, 0.07);
}

.trend-empty {
  padding: 34px 20px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.6);
  border: 1px dashed rgba(147, 197, 253, 0.5);
  text-align: center;
  font-size: 13px;
  color: #94a3b8;
}

.trend-plot {
  position: relative;
  height: 180px;
  display: flex;
  align-items: flex-end;
  gap: 14px;
}

.trend-col {
  flex: 1;
  height: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.trend-track {
  width: 100%;
  max-width: 40px;
  height: 100%;
  display: flex;
  align-items: flex-end;
}

.trend-bar {
  position: relative;
  width: 100%;
  min-height: 12px;
  border-radius: 10px 10px 4px 4px;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 5px;
  box-shadow: 0 8px 18px rgba(59, 130, 246, 0.16);
  transform-origin: bottom;
  animation: trendGrow 0.7s ease both;
  transition: filter 0.2s;
}

.trend-col:hover .trend-bar {
  filter: brightness(1.06);
}

@keyframes trendGrow {
  from {
    transform: scaleY(0);
  }
  to {
    transform: scaleY(1);
  }
}

.trend-val {
  font-size: 12px;
  font-weight: 700;
  color: #ffffff;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.18);
}

/* 均值虚线 */
.avg-line {
  position: absolute;
  left: 0;
  right: 0;
  border-top: 1.5px dashed rgba(100, 116, 139, 0.45);
  z-index: 0;
  pointer-events: none;
}

.avg-label {
  position: absolute;
  right: 0;
  top: -19px;
  font-size: 11px;
  color: #64748b;
  background: rgba(255, 255, 255, 0.88);
  padding: 2px 9px;
  border-radius: 999px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  box-shadow: 0 4px 12px rgba(17, 24, 39, 0.06);
}

.trend-days {
  display: flex;
  gap: 14px;
  margin-top: 10px;
}

.trend-day {
  flex: 1;
  text-align: center;
  font-size: 11px;
  color: #94a3b8;
}

.trend-day.high {
  color: #059669;
  font-weight: 600;
}

.trend-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 12px;
  padding: 10px 16px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.55);
  border: 1px solid rgba(226, 232, 240, 0.7);
  font-size: 11.5px;
  color: #64748b;
}

.trend-legend .dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 5px;
}

/* ═══════════════════════════
   Recent + Insight
   ═══════════════════════════ */
.recent-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 18px;
}
.recent-more {
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 13px; font-weight: 500; color: #3b82f6; text-decoration: none;
  transition: all 0.2s;
}
.recent-more:hover { color: #2563eb; gap: 6px; }
.recent-loading {
  display: flex; justify-content: center; align-items: center;
  min-height: 160px; color: #94a3b8; font-size: 22px;
}
.recent-empty {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  min-height: 180px; padding: 24px; text-align: center;
  border-radius: 18px; background: #fff; border: 1px dashed #e2e8f0;
}
.recent-empty-icon { font-size: 36px; margin-bottom: 10px; }
.recent-empty-title { font-size: 15px; font-weight: 600; color: #374151; margin: 0 0 4px; }
.recent-empty-desc { font-size: 13px; color: #94a3b8; margin: 0 0 16px; }
.recent-empty-btn {
  padding: 8px 18px; border-radius: 999px;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  color: #fff; font-size: 13px; font-weight: 500;
  text-decoration: none; transition: all 0.2s;
  box-shadow: 0 4px 12px rgba(59,130,246,0.22);
}
.recent-empty-btn:hover { transform: translateY(-1px); box-shadow: 0 6px 16px rgba(59,130,246,0.28); }
.recent-list { display: flex; flex-direction: column; gap: 10px; }
.recent-item {
  padding: 16px 18px; border-radius: 14px; background: #fff;
  border: 1px solid #eceef1; cursor: pointer; transition: all 0.22s;
}
.recent-item:hover { border-color: rgba(59,130,246,0.3); box-shadow: 0 6px 18px rgba(17,24,39,0.05); transform: translateY(-2px); }
.recent-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 6px; }
.recent-title-wrap { display: flex; align-items: center; gap: 8px; flex: 1; min-width: 0; }
.recent-name { font-size: 14px; font-weight: 600; color: #111827; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.recent-status {
  flex-shrink: 0; padding: 1px 7px; border-radius: 999px;
  font-size: 10px; font-weight: 500; color: #059669;
  background: rgba(5,150,105,0.08);
}
.recent-status.archived { color: #6b7280; background: rgba(107,114,128,0.08); }
.recent-time { font-size: 12px; color: #9ca3af; flex-shrink: 0; }
.recent-excerpt { margin: 0 0 8px; font-size: 13px; color: #64748b; line-height: 1.5; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.recent-meta { display: flex; align-items: center; gap: 10px; }
.recent-count { font-size: 11px; color: #94a3b8; }

.insight-card {
  padding: 20px 22px; border-radius: 18px; background: linear-gradient(135deg, rgba(99,102,241,0.06), rgba(59,130,246,0.04));
  border: 1px solid rgba(147,197,253,0.3);
}
.insight-text { margin: 0 0 14px; font-size: 14px; line-height: 1.75; color: #374151; }
.insight-empty { color: #9ca3af; font-style: italic; }
.insight-tags { display: flex; flex-wrap: wrap; gap: 8px; }
.insight-tag {
  font-size: 12px; padding: 4px 12px; border-radius: 999px;
  background: rgba(99,102,241,0.1); color: #6366f1; font-weight: 600;
}

/* ═══════════════════════════
   Quote
   ═══════════════════════════ */
.quote-section { margin-top: 52px; }
.quote-card {
  padding: 28px 32px; border-radius: 22px;
  background: linear-gradient(135deg, #eef4ff 0%, #f8faff 60%, #fdfdff 100%);
  border: 1px solid rgba(147,197,253,0.25);
  text-align: center;
}
.quote-body {
  transition: opacity 0.3s ease, transform 0.3s ease;
}
.quote-body.changing { opacity: 0.25; transform: translateY(4px); }
.quote-zh {
  font-family: 'Songti SC', serif; font-size: 20px; font-weight: 400;
  color: #1e293b; line-height: 1.6; letter-spacing: 2px; margin: 0 0 12px;
}
.quote-en {
  font-family: 'Georgia', serif; font-style: italic; font-size: 14px;
  color: #94a3b8; margin: 0 0 10px;
}
.quote-author { font-size: 13px; color: #6366f1; margin: 0; }
.quote-source { color: #94a3b8; font-size: 12px; margin-left: 4px; }
.quote-hint {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  margin-top: 18px;
}
.quote-refresh-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 16px; border-radius: 999px; border: 1px solid rgba(147,197,253,0.4);
  background: #fff; color: #3b82f6; font-size: 13px; font-weight: 500;
  cursor: pointer; transition: all 0.2s;
}
.quote-refresh-btn:hover {
  background: #3b82f6; color: #fff; border-color: #3b82f6;
  box-shadow: 0 4px 12px rgba(59,130,246,0.22);
}
.quote-refresh-btn .el-icon { font-size: 14px; }
.quote-status {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 4px 12px; border-radius: 999px;
  background: rgba(255,255,255,0.6); border: 1px solid rgba(147,197,253,0.2);
}
.quote-dot {
  width: 6px; height: 6px; border-radius: 50%; background: #cbd5e1;
  transition: background 0.3s;
}
.quote-dot.active { background: #34d399; }
.quote-timer { font-size: 11px; color: #94a3b8; }

/* ═══════════════════════════
   Responsive
   ═══════════════════════════ */
@media (max-width: 1024px) {
  .dual-section { grid-template-columns: 1fr; gap: 32px; }
  .overview-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 960px) {
  .hero { grid-template-columns: 1fr; padding: 52px 36px; gap: 40px; }
  .hero-greet { font-size: 32px; }
  .trend-chart { height: 140px; }
}
@media (max-width: 768px) {
  .home { padding: 0 16px 48px; }
  .hero { padding: 40px 24px; border-radius: 24px; }
  .hero-greet { font-size: 26px; }
  .hero-sub { font-size: 15px; }
  .hero-actions { flex-direction: column; }
  .quick-grid { grid-template-columns: 1fr 1fr; }
  .overview-grid { grid-template-columns: 1fr 1fr; }
  .trend-chart { height: 120px; gap: 10px; }
  .trend-bar { max-width: 30px; }
  .mock-chip { display: none; }
  .quote-card { padding: 24px 20px; }
  .quote-zh { font-size: 17px; }
  .quote-refresh-btn { padding: 6px 12px; font-size: 12px; }
}
/* iPhone 窄屏：压缩留白与卡片，UI 不变仅防错乱 */
@media (max-width: 520px) {
  .home {
    padding: 84px 12px 48px;
  }

  .hero {
    padding: 40px 18px;
    border-radius: 20px;
    gap: 28px;
  }

  .hero-title {
    font-size: 23px;
  }

  .hero-actions {
    flex-direction: column;
    gap: 10px;
  }

  .hero-btn {
    width: 100%;
  }

  .hero-trust {
    gap: 10px;
  }

  .hero-trust span {
    font-size: 12px;
  }

  .feature-cards {
    gap: 12px;
  }

  .feature-card {
    padding: 24px 18px;
  }

  .article-item {
    padding: 14px;
    gap: 10px;
  }

  .article-meta {
    display: none;
  }

  .trend-chart {
    padding: 14px 10px 10px;
  }

  .trend-plot {
    height: 150px;
    gap: 8px;
  }

  .trend-legend {
    gap: 10px;
    padding: 8px 12px;
    font-size: 11px;
  }
}
</style>
