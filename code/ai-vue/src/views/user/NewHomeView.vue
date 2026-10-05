<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, ArrowUpRight, BookOpen, ChevronRight, Flower2, Headphones, Heart, Leaf, MessageCircle, Moon, RefreshCw, Sparkles, Sprout } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { getGarden, getInsightThisWeek } from '@/api/emotion'
import { getMySessions } from '@/api/consult'
import { getArticlePage, getRecommendArticles } from '@/api/knowledge'
import { getRandomQuote } from '@/api/quote'
import AppNavBar from '@/components/AppNavBar.vue'
import UserDropdown from '@/components/UserDropdown.vue'
import { USER_NAV_ACTIONS } from '@/constants/userNavigation'

defineOptions({ name: 'NewHomeView' })

const router = useRouter()
const authStore = useAuthStore()
const pageRef = ref(null)
let motionContext
const name = computed(() => authStore.userInfo?.nickname || authStore.userInfo?.username || '朋友')
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 11) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})
const dateLabel = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(new Date())

const flowers = ref([])
const sessions = ref([])
const sessionTotal = ref(0)
const articles = ref([])
const insight = ref(null)
const quote = ref({ content: '慢一点，也是在认真生活。', author: '' })
const quoteLoading = ref(false)
let quoteTimer

const todayKey = new Date().toLocaleDateString('sv-SE')
const recordedToday = computed(() => flowers.value.some((flower) =>
  String(flower.date || flower.createdAt || '').slice(0, 10) === todayKey
))
const averageMood = computed(() => {
  const scores = flowers.value.map((flower) => Number(flower.emotionScore)).filter((score) => score > 0)
  return scores.length ? (scores.reduce((sum, score) => sum + score, 0) / scores.length).toFixed(1) : '暂无'
})
const streak = computed(() => {
  const dates = new Set(flowers.value.map((flower) => String(flower.date || flower.createdAt || '').slice(0, 10)))
  const cursor = new Date()
  if (!dates.has(localDate(cursor))) cursor.setDate(cursor.getDate() - 1)
  let days = 0
  while (dates.has(localDate(cursor))) {
    days++
    cursor.setDate(cursor.getDate() - 1)
  }
  return days
})

function localDate(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

const week = computed(() => {
  const byDate = new Map()
  for (const flower of flowers.value) {
    const date = String(flower.date || flower.createdAt || '').slice(0, 10)
    const score = Number(flower.emotionScore)
    if (!date || !score) continue
    const entry = byDate.get(date) || { sum: 0, count: 0 }
    entry.sum += score
    entry.count++
    byDate.set(date, entry)
  }
  return Array.from({ length: 7 }, (_, index) => {
    const day = new Date()
    day.setDate(day.getDate() - (6 - index))
    const entry = byDate.get(localDate(day))
    return { label: ['日', '一', '二', '三', '四', '五', '六'][day.getDay()], value: entry ? entry.sum / entry.count : null, today: index === 6 }
  })
})

const insightText = computed(() => insight.value?.summary || '每一次记录，都是更了解自己的开始。')
const firstArticle = computed(() => articles.value[0] || null)
const moreArticles = computed(() => articles.value.slice(1, 4))

async function loadDashboard() {
  const [gardenResult, sessionResult, recommendedResult, pageResult, insightResult] = await Promise.allSettled([
    getGarden(), getMySessions({ page: 1, pageSize: 20 }), getRecommendArticles(4),
    getArticlePage({ page: 1, pageSize: 4 }), getInsightThisWeek()
  ])
  if (gardenResult.status === 'fulfilled') flowers.value = Array.isArray(gardenResult.value) ? gardenResult.value : []
  if (sessionResult.status === 'fulfilled') {
    const result = sessionResult.value
    sessions.value = Array.isArray(result) ? result : (result?.list || [])
    sessionTotal.value = Number(result?.total ?? sessions.value.length)
  }
  const recommended = recommendedResult.status === 'fulfilled' && Array.isArray(recommendedResult.value) ? recommendedResult.value : []
  const page = pageResult.status === 'fulfilled' ? (pageResult.value?.list || []) : []
  articles.value = recommended.length ? recommended : page
  if (insightResult.status === 'fulfilled') insight.value = insightResult.value
}

async function refreshQuote() {
  if (quoteLoading.value) return
  quoteLoading.value = true
  try {
    const result = await getRandomQuote()
    if (result?.content) quote.value = result
  } catch {
    // Keep the quiet local fallback when the quote service is unavailable.
  } finally {
    quoteLoading.value = false
  }
}

function articleId(article) { return article?.articleId || article?.id }
function articleCategory(article) { return article?.categoryName || article?.category || '心理阅读' }
function openArticle(article) {
  const id = articleId(article)
  router.push(id ? `/home/articles/${id}` : '/home/articles')
}
function openSession(session) {
  router.push(session?.id ? { path: '/consult', query: { session: session.id } } : '/consult')
}
function sessionTime(value) {
  if (!value) return '最近'
  const date = new Date(String(value).replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? '最近' : new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric' }).format(date)
}

onMounted(() => {
  loadDashboard()
  refreshQuote()
  quoteTimer = setInterval(refreshQuote, 5 * 60 * 1000)
  if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    Promise.all([import('gsap'), import('gsap/ScrollTrigger')]).then(([{ gsap }, { ScrollTrigger }]) => {
      if (!pageRef.value) return
      gsap.registerPlugin(ScrollTrigger)
      motionContext = gsap.context(() => {
        const opening = gsap.timeline({ defaults: { ease: 'power3.out' } })
        opening.from('.hero-kicker', { y: 12, autoAlpha: 0, duration: 0.5 })
          .from('.hero-title-line', { yPercent: 110, autoAlpha: 0, duration: 0.95, stagger: 0.13, clearProps: 'all' }, '-=0.2')
          .from('.hero-description, .hero-actions', { y: 16, autoAlpha: 0, duration: 0.65, stagger: 0.1, clearProps: 'all' }, '-=0.55')
          .from('.hero-art', { x: 26, autoAlpha: 0, duration: 1.05, clearProps: 'all' }, 0.08)
          .from('.poster-word, .poster-index', { y: 24, autoAlpha: 0, duration: 0.8, stagger: 0.12, clearProps: 'all' }, 0.46)

        gsap.utils.toArray('.reveal-section').forEach((section) => {
          gsap.from(section, {
            y: 32, autoAlpha: 0, duration: 0.9, ease: 'power2.out', clearProps: 'all',
            scrollTrigger: { trigger: section, start: 'top 88%', once: true }
          })
        })
        gsap.from('.feature-card', {
          y: 28, autoAlpha: 0, duration: 0.7, stagger: 0.12, ease: 'power2.out', clearProps: 'all',
          scrollTrigger: { trigger: '.feature-grid', start: 'top 82%', once: true }
        })
      }, pageRef.value)
    })
  }
})
onUnmounted(() => {
  clearInterval(quoteTimer)
  motionContext?.revert()
})
</script>

<template>
  <div ref="pageRef" class="new-home">
    <AppNavBar :actions="USER_NAV_ACTIONS" current-path="/home">
      <template #actions-after>
        <UserDropdown />
      </template>
    </AppNavBar>

    <main class="page-shell">
      <div class="eyebrow"><span class="eyebrow-dot"></span> 给自己一点空间 <span class="eyebrow-date">{{ dateLabel }}</span></div>

      <section class="hero" aria-labelledby="hero-title">
        <div class="hero-copy">
          <span class="section-kicker hero-kicker">慢下来，听听自己</span>
          <h1 id="hero-title"><span class="hero-title-mask"><span class="hero-title-line">{{ greeting }}，{{ name }}。</span></span><span class="hero-title-mask"><em class="hero-title-line">今天，<i>慢慢来。</i></em></span></h1>
          <p class="hero-description">心情不需要标准答案。<br>来这里说说话、写下感受，或只是安静待一会儿。</p>
          <div class="hero-actions">
            <router-link class="btn btn-dark" to="/consult">找人聊聊 <ArrowUpRight :size="17" /></router-link>
            <router-link class="btn btn-outline" to="/garden">记录此刻心情 <ArrowRight :size="17" /></router-link>
          </div>
          <div class="hero-foot"><span class="pulse-dot"></span> 留一点时间，给真实的自己</div>
        </div>
        <div class="hero-art" aria-hidden="true">
          <svg class="home-scene" viewBox="0 0 660 560" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
            <defs>
              <linearGradient id="reading-arch" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#dce8d7" /><stop offset="1" stop-color="#c8d8c8" /></linearGradient>
              <linearGradient id="reading-page-left" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#fffdf5" /><stop offset="1" stop-color="#eae5d7" /></linearGradient>
              <linearGradient id="reading-page-right" x1="1" y1="0" x2="0" y2="1"><stop stop-color="#fffdf5" /><stop offset="1" stop-color="#e7e2d4" /></linearGradient>
              <linearGradient id="reading-cup" x1="0" y1="0" x2="1" y2="0"><stop stop-color="#c28e6f" /><stop offset=".55" stop-color="#dec1a0" /><stop offset="1" stop-color="#ad765b" /></linearGradient>
              <filter id="reading-shadow" x="-.2" y="-.3" width="1.4" height="1.7"><feGaussianBlur in="SourceAlpha" stdDeviation="9" /><feOffset dy="10" /><feComponentTransfer><feFuncA type="linear" slope=".22" /></feComponentTransfer><feMerge><feMergeNode /><feMergeNode in="SourceGraphic" /></feMerge></filter>
            </defs>
            <rect width="660" height="560" fill="#e8eee6" />
            <path d="M218 560V257c0-111 90-201 201-201s201 90 201 201v303z" fill="url(#reading-arch)" />
            <circle cx="478" cy="158" r="66" fill="#f7e8c4" />
            <path d="M420 57v302M218 258h402" fill="none" stroke="#cad9c9" stroke-width="7" opacity=".62" />
            <path d="M0 385c114-30 205-26 294 5s223 47 366 7v163H0z" fill="#c5d4c1" />
            <path d="M0 423c157-28 251-1 352 27s206 25 308-5v115H0z" fill="#b9cbb8" opacity=".72" />
            <ellipse cx="453" cy="482" rx="203" ry="28" fill="#6c806b" opacity=".24" />
            <g filter="url(#reading-shadow)">
              <path d="M274 350c59-26 116-5 181 39l-2 117c-62-43-119-61-179-42z" fill="#758b70" />
              <path d="M455 389c64-44 119-65 179-39l-2 114c-60-19-117-1-179 42z" fill="#667f68" />
              <path d="M284 345c55-21 109-2 171 40v110c-59-38-112-54-171-38z" fill="url(#reading-page-left)" stroke="#d7d2c2" stroke-width="2" />
              <path d="M455 385c62-42 116-61 171-40v112c-59-16-112 0-171 38z" fill="url(#reading-page-right)" stroke="#d7d2c2" stroke-width="2" />
              <path d="M302 371c39-8 79 6 129 34M300 390c39-8 80 8 131 35M300 410c39-7 79 8 130 35M477 405c51-29 91-42 129-34M477 425c50-27 91-43 130-35M477 445c51-27 91-42 130-35" fill="none" stroke="#b7c2b2" stroke-width="2" stroke-linecap="round" opacity=".5" />
              <path d="M455 386v109" fill="none" stroke="#b2ad9f" stroke-width="3" opacity=".65" />
            </g>
            <ellipse cx="550" cy="359" rx="51" ry="13" fill="#a9866f" opacity=".3" />
            <ellipse cx="548" cy="347" rx="45" ry="13" fill="#e2d4bd" />
            <path d="M516 299h63l-7 47c-2 14-47 14-49 0z" fill="url(#reading-cup)" />
            <path d="M578 310c24-3 29 29 3 34" fill="none" stroke="#b78464" stroke-width="8" stroke-linecap="round" />
            <ellipse cx="547.5" cy="300" rx="31.5" ry="9" fill="#916549" />
            <ellipse cx="547.5" cy="300" rx="25" ry="5.7" fill="#73533d" />
            <ellipse cx="538" cy="299" rx="13" ry="2" fill="#bc9270" opacity=".55" />
          </svg>
          <div class="poster-index">MINDMAN <span>✳</span> A QUIET MOMENT</div>
          <div class="poster-word">a moment<span>for yourself.</span></div>
          <div class="art-caption">there is no rush here.</div>
        </div>
      </section>

      <section class="checkin-row reveal-section" aria-label="今日状态">
        <div class="checkin-intro"><span class="round-icon"><Heart :size="19" /></span><div><strong>你的今日心情</strong><small>{{ recordedToday ? '今天已经留下了一份心情记录' : '花一分钟，听听自己的心' }}</small></div></div>
        <div class="checkin-choice"><span>{{ recordedToday ? '已记录' : '还没有记录' }}</span><router-link to="/garden">{{ recordedToday ? '查看花园' : '去记录' }} <ArrowRight :size="15" /></router-link></div>
      </section>

      <section class="section-block" aria-labelledby="start-title">
        <div class="section-heading"><div><span class="section-kicker">今天可以这样开始</span><h2 id="start-title">从这里，开始今天的照顾</h2></div><span class="section-sub">每一种需要，都有它的位置</span></div>
        <div class="feature-grid">
          <router-link class="feature-card feature-chat" to="/consult"><span class="feature-icon"><MessageCircle :size="25" :stroke-width="1.6" /></span><span class="feature-number">01 / TALK</span><h3>想找人聊聊</h3><p>说出心里的事，获得耐心的倾听和回应。</p><span class="feature-bottom">进入倾听空间 <ArrowUpRight :size="18" /></span><span class="feature-deco"><MessageCircle :size="100" :stroke-width=".65" /></span></router-link>
          <router-link class="feature-card feature-garden" to="/garden"><span class="feature-icon"><Sprout :size="25" :stroke-width="1.6" /></span><span class="feature-number">02 / FEEL</span><h3>记录当下感受</h3><p>为心情种下一朵花，看见自己的变化。</p><span class="feature-bottom">走进情绪花园 <ArrowUpRight :size="18" /></span><span class="feature-deco"><Flower2 :size="100" :stroke-width=".65" /></span></router-link>
          <router-link class="feature-card feature-relax" to="/relax"><span class="feature-icon"><Headphones :size="25" :stroke-width="1.6" /></span><span class="feature-number">03 / REST</span><h3>给大脑放个假</h3><p>选一段声音，让紧绷的时刻慢慢松开。</p><span class="feature-bottom">去放松片刻 <ArrowUpRight :size="18" /></span><span class="feature-deco"><Moon :size="100" :stroke-width=".65" /></span></router-link>
        </div>
      </section>

      <section class="middle-grid reveal-section" aria-label="你的成长足迹">
        <div class="journey-panel"><div class="panel-top"><div><span class="section-kicker">成长足迹</span><h2>一点一滴，都是成长</h2></div><router-link to="/garden" class="text-link">查看花园 <ArrowUpRight :size="16" /></router-link></div>
          <div class="stats"><div><span>情绪平均分</span><strong>{{ averageMood }}<small v-if="averageMood !== '暂无'"> / 5</small></strong></div><div><span>连续记录</span><strong>{{ streak }}<small> 天</small></strong></div><div><span>倾听次数</span><strong>{{ sessionTotal }}<small> 次</small></strong></div></div>
          <div class="week-head"><span>近七天的心情</span><small>每一个点，都是真实的你</small></div>
          <div class="week-chart"><div v-for="(day, index) in week" :key="index" class="week-day"><div class="week-track"><div v-if="day.value !== null" class="week-bar" :class="{ today: day.today }" :style="{ height: `${Math.max(12, day.value / 5 * 100)}%` }" :title="`${day.value.toFixed(1)} / 5`"></div><span v-else class="week-empty"></span></div><small>{{ day.label }}</small></div></div>
          <p v-if="week.every(day => day.value === null)" class="chart-note">还没有记录，去情绪花园种下第一朵花吧。</p>
        </div>
        <div class="insight-panel"><div class="insight-mark"><Sparkles :size="22" :stroke-width="1.7" /></div><span class="section-kicker">来自情绪记录</span><h2>给你的本周小提示</h2><p>“{{ insightText }}”</p><div class="insight-bottom"><span><Leaf :size="15" /> 温柔地观察自己</span><router-link to="/garden" aria-label="进入情绪花园"><ArrowUpRight :size="20" /></router-link></div></div>
      </section>

      <section class="bottom-grid reveal-section" aria-label="更多陪伴内容">
        <div class="reading-panel"><div class="panel-top"><div><span class="section-kicker">心理阅读</span><h2>读一点，懂自己多一点</h2></div><router-link to="/home/articles" class="text-link">全部文章 <ArrowUpRight :size="16" /></router-link></div>
          <button v-if="firstArticle" class="featured-article" @click="openArticle(firstArticle)"><span class="article-visual"><BookOpen :size="31" :stroke-width="1.4" /></span><span class="article-copy"><small>{{ articleCategory(firstArticle) }}</small><strong>{{ firstArticle.title }}</strong><span>{{ firstArticle.summary || '给自己一段安静阅读的时间。' }}</span><em>开始阅读 <ChevronRight :size="14" /></em></span></button>
          <div v-else class="empty-content">暂时没有可阅读的文章，稍后再来看看。</div>
          <button v-for="article in moreArticles" :key="articleId(article) || article.title" class="article-line" @click="openArticle(article)"><span>{{ article.title }}</span><ArrowUpRight :size="16" /></button>
        </div>
        <div class="side-stack"><div class="quote-panel"><span class="section-kicker">今日一句</span><span class="quote-mark">“</span><p>{{ quote.content }}</p><div class="quote-bottom"><span>{{ quote.author || '每日一句' }}</span><button type="button" :disabled="quoteLoading" aria-label="换一句" @click="refreshQuote"><RefreshCw :size="16" :class="{ spinning: quoteLoading }" /></button></div></div>
          <div class="recent-panel"><div class="recent-top"><span>最近的对话</span><router-link to="/consult">查看全部 <ArrowRight :size="14" /></router-link></div><button v-if="sessions.length" class="recent-session" @click="openSession(sessions[0])"><span class="session-icon"><MessageCircle :size="18" /></span><span><strong>{{ sessions[0].title || '一段属于你的对话' }}</strong><small>{{ sessionTime(sessions[0].updatedAt) }} · 继续聊聊</small></span><ChevronRight :size="16" /></button><p v-else class="recent-empty">还没有对话。想说的时候，我们在这里。</p></div></div>
      </section>

      <footer class="page-footer"><span><Flower2 :size="18" /> MindMan · 陪你与自己好好相处</span><span>每一种感受，都值得被认真对待</span></footer>
    </main>

  </div>
</template>

<style scoped>
.new-home{min-height:100vh;background:#f8f7f2;color:#23342d;font-family:'Inter','Noto Sans SC',sans-serif}.new-home :is(h1,h2,h3){color:inherit}.site-header{height:82px;max-width:1340px;margin:auto;padding:0 42px;display:flex;align-items:center;justify-content:space-between;gap:24px}.brand{display:flex;align-items:center;gap:11px;color:#23342d;flex:none}.brand-mark{width:39px;height:39px;border-radius:13px;background:#dfe9d9;display:grid;place-items:center;color:#3b654e}.brand strong{display:block;font-family:Georgia,serif;font-size:20px;letter-spacing:-.8px;line-height:1.1}.brand small{display:block;color:#879087;font-size:10px;letter-spacing:1.2px;margin-top:3px}.desktop-nav{display:flex;gap:34px;align-items:center}.desktop-nav a{font-size:13px;color:#69766d;font-weight:600;transition:color .2s}.desktop-nav a:hover,.desktop-nav a.active{color:#274b38}.desktop-nav a.active:after{content:'';display:block;width:16px;height:2px;background:#829e77;margin:7px auto -9px;border-radius:2px}.header-end{display:flex;align-items:center;gap:20px}.classic-link{display:inline-flex;align-items:center;gap:3px;font-size:12px;color:#7d8b7d;white-space:nowrap}.page-shell{max-width:1256px;padding:0 20px;margin:0 auto}.eyebrow{display:flex;align-items:center;gap:8px;color:#8e9b87;font-size:10px;font-weight:700;letter-spacing:2px;padding:20px 2px 17px}.eyebrow-dot,.pulse-dot{width:7px;height:7px;border-radius:50%;background:#96b08b;display:inline-block}.eyebrow-date{margin-left:auto;color:#8f978d;letter-spacing:.5px;font-size:12px;font-weight:500}.hero{min-height:465px;border-radius:24px;overflow:hidden;display:grid;grid-template-columns:1.07fr .93fr;background:#e8eee5;position:relative}.hero-copy{padding:66px 45px 42px 69px;position:relative;z-index:2}.section-kicker{font-size:11px;color:#66816a;font-weight:700;letter-spacing:1.8px}.hero h1{font-family:'Noto Serif SC',Georgia,serif;font-weight:600;font-size:clamp(30px,3.25vw,45px);line-height:1.48;letter-spacing:-1.6px;margin:16px 0 16px}.hero h1 em{font-style:normal;color:#607d60}.hero-copy>p{font-size:14px;color:#708075;line-height:1.95;max-width:520px}.hero-actions{display:flex;gap:12px;margin-top:31px}.btn{display:inline-flex;align-items:center;justify-content:center;gap:26px;border-radius:7px;padding:13px 20px;font-size:13px;font-weight:650;transition:transform .2s,background .2s}.btn:hover{transform:translateY(-2px)}.btn-dark{background:#2c4938;color:#fff}.btn-dark:hover{background:#1c3828;color:#fff}.btn-outline{border:1px solid #aebfaf;color:#355443;background:rgba(255,255,255,.35)}.hero-foot{display:flex;align-items:center;gap:9px;margin-top:38px;font-size:11px;color:#829186}.hero-foot-line{height:1px;width:25px;background:#bacbbc;margin:0 6px}.hero-art{position:relative;min-height:440px;background:radial-gradient(circle at 70% 35%,#f6f2de 0,#e4e9d9 48%,#d9e4d9 100%);overflow:hidden}.art-glow{position:absolute;width:390px;height:390px;background:#fff7e7;filter:blur(75px);opacity:.65;top:-170px;right:-30px}.art-arch{position:absolute;width:330px;height:440px;border-radius:180px 180px 0 0;right:14%;bottom:0;background:linear-gradient(120deg,#d1dfcf,#e9e9d8 70%);box-shadow:inset 18px 0 35px #cbd8ca,25px 2px 40px #b8c8b166}.art-sun{position:absolute;width:135px;height:135px;border-radius:50%;background:#f8ecd1;right:31%;top:13%;filter:blur(1px);box-shadow:0 0 45px #f7ebcb}.art-shelf{position:absolute;right:0;left:9%;bottom:36px;height:17px;background:#b8c7ae;box-shadow:0 15px 20px #849a7a55;transform:skewY(-2deg)}.art-vase{position:absolute;width:142px;height:177px;right:29%;bottom:48px;background:linear-gradient(120deg,#dbcbb7,#bfae97 50%,#e6d8c6);border-radius:44% 44% 46% 46% / 15% 15% 62% 62%;box-shadow:23px 21px 30px #879b7d66;z-index:3}.art-vase:before{content:'';position:absolute;width:66px;height:20px;left:38px;top:-10px;border-radius:50%;background:#9a8977;box-shadow:inset 0 4px 4px #665e53}.art-vase i{position:absolute;left:15px;top:24px;bottom:13px;width:35px;border-radius:50%;background:#ffffff24;filter:blur(8px)}.art-stem{position:absolute;width:3px;background:#607c57;transform-origin:bottom;z-index:2;right:41%;bottom:190px;border-radius:3px}.stem-one{height:198px;transform:rotate(-17deg)}.stem-two{height:219px;transform:rotate(13deg)}.stem-three{height:177px;transform:rotate(38deg)}.art-leaf{position:absolute;width:58px;height:23px;background:#749269;border-radius:100% 0 100% 0;z-index:3;box-shadow:inset 8px 5px 9px #a9c29a66}.leaf-one{right:45%;bottom:310px;transform:rotate(28deg)}.leaf-two{right:33%;bottom:332px;transform:rotate(-28deg)}.leaf-three{right:47%;bottom:253px;transform:rotate(40deg)}.leaf-four{right:28%;bottom:258px;transform:rotate(-36deg)}.art-flower{position:absolute;width:74px;height:74px;right:24%;bottom:354px;z-index:4;filter:drop-shadow(2px 8px 8px #b0a08755)}.art-flower:before,.art-flower:after,.art-flower span{content:'';position:absolute;width:37px;height:52px;background:#f1c6a5;border-radius:60% 60% 45% 45%;left:19px;top:2px;transform-origin:50% 85%}.art-flower:before{transform:rotate(-65deg)}.art-flower:after{transform:rotate(65deg)}.art-flower span{z-index:2;background:#eebd9a}.art-caption{position:absolute;right:30px;bottom:19px;font:italic 13px Georgia,serif;color:#637b64;z-index:4}.checkin-row{margin-top:17px;background:#fff;border:1px solid #eeeae1;border-radius:13px;padding:20px 24px;display:flex;justify-content:space-between;align-items:center;box-shadow:0 8px 28px #303d2710}.checkin-intro{display:flex;align-items:center;gap:14px}.round-icon{width:39px;height:39px;border-radius:50%;background:#f6e9e1;color:#b87866;display:grid;place-items:center}.checkin-intro strong,.checkin-intro small{display:block}.checkin-intro strong{font-size:14px}.checkin-intro small{color:#9a9c94;font-size:12px;margin-top:3px}.checkin-choice{display:flex;align-items:center;gap:25px;font-size:12px;color:#9a9c94}.checkin-choice a{display:flex;align-items:center;gap:6px;color:#49684f;font-weight:700}.section-block{margin-top:71px}.section-heading,.panel-top{display:flex;justify-content:space-between;align-items:end;gap:15px}.section-heading h2,.panel-top h2{font-family:'Noto Serif SC',serif;font-size:25px;font-weight:600;margin-top:10px;letter-spacing:-.7px}.section-sub{color:#a0a59d;font-size:12px;padding-bottom:3px}.feature-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:17px;margin-top:25px}.feature-card{min-height:238px;position:relative;overflow:hidden;border-radius:14px;padding:25px 27px;color:#263b2f;transition:transform .22s,box-shadow .22s}.feature-card:hover{transform:translateY(-4px);box-shadow:0 15px 30px #33453718}.feature-chat{background:#e8efec}.feature-garden{background:#eff1df}.feature-relax{background:#f6ede6}.feature-icon{width:44px;height:44px;border:1px solid #82988978;border-radius:50%;display:grid;place-items:center;color:#476d57}.feature-number{position:absolute;top:33px;right:27px;font-size:9px;letter-spacing:1.3px;color:#8b9c8d}.feature-card h3{font-family:'Noto Serif SC',serif;font-size:22px;font-weight:600;margin:26px 0 7px}.feature-card p{font-size:12px;color:#7b897e;max-width:235px;line-height:1.7}.feature-bottom{display:flex;align-items:center;gap:8px;margin-top:22px;font-size:12px;font-weight:700;color:#43654d}.feature-deco{position:absolute;right:-11px;bottom:-19px;opacity:.1;transform:rotate(-17deg)}.middle-grid{display:grid;grid-template-columns:1.45fr 1fr;gap:18px;margin-top:70px}.journey-panel,.reading-panel,.recent-panel{background:#fff;border:1px solid #eeeae1;border-radius:15px}.journey-panel{padding:30px 32px 22px}.panel-top h2{font-size:22px}.text-link{display:inline-flex;align-items:center;gap:4px;color:#6c856e;font-size:12px;white-space:nowrap}.stats{display:grid;grid-template-columns:repeat(3,1fr);border-bottom:1px solid #f0eee8;margin-top:30px;padding-bottom:20px}.stats>div:not(:first-child){padding-left:25px;border-left:1px solid #eeeae3}.stats span{display:block;color:#9da59c;font-size:11px}.stats strong{display:block;font:600 31px Georgia,serif;color:#2d4b38;margin-top:5px}.stats strong small{font:12px 'Noto Sans SC',sans-serif;color:#a2aaa0}.week-head{display:flex;justify-content:space-between;align-items:center;margin:22px 0 9px;font-size:12px;font-weight:700}.week-head small{font-size:11px;color:#adb4aa;font-weight:400}.week-chart{display:grid;grid-template-columns:repeat(7,1fr);height:120px;gap:13px}.week-day{text-align:center;display:flex;flex-direction:column;min-width:0}.week-track{height:92px;display:flex;align-items:end;justify-content:center;border-bottom:1px solid #e4e7dc}.week-bar{width:min(100%,28px);background:#bed0b8;border-radius:5px 5px 0 0;transition:height .4s}.week-bar.today{background:#628764}.week-empty{height:3px;width:20px;background:#e8ebe4;border-radius:3px}.week-day small{font-size:10px;color:#a1aaa1;margin-top:7px}.chart-note{font-size:11px;color:#a6aca4;margin-top:7px}.insight-panel{background:#e9eee0;border-radius:15px;padding:30px 34px;display:flex;flex-direction:column;min-height:339px;position:relative;overflow:hidden}.insight-panel:after{content:'';position:absolute;width:270px;height:270px;border-radius:50%;border:1px solid #d5dfcd;right:-80px;bottom:-150px;box-shadow:0 0 0 38px #ffffff12,0 0 0 76px #ffffff12}.insight-mark{width:43px;height:43px;border-radius:50%;background:#d4e2d1;color:#4d7050;display:grid;place-items:center;margin-bottom:28px}.insight-panel h2{font-family:'Noto Serif SC',serif;font-size:23px;font-weight:600;margin:11px 0}.insight-panel p{font-family:'Noto Serif SC',serif;font-size:17px;line-height:1.75;color:#506650;max-width:370px;position:relative;z-index:1;display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden}.insight-bottom{display:flex;align-items:center;justify-content:space-between;margin-top:auto;position:relative;z-index:1}.insight-bottom span{display:flex;align-items:center;gap:5px;color:#8ba18a;font-size:11px}.insight-bottom a{width:36px;height:36px;border-radius:50%;background:#fff9;display:grid;place-items:center;color:#53745a}.bottom-grid{display:grid;grid-template-columns:1.45fr 1fr;gap:18px;margin-top:18px}.reading-panel{padding:30px 32px}.featured-article,.article-line,.recent-session{border:0;background:transparent;text-align:left;cursor:pointer;color:inherit;font:inherit}.featured-article{display:flex;width:100%;gap:20px;margin-top:26px;padding:0 0 20px;border-bottom:1px solid #eeeae3}.article-visual{flex:none;width:138px;height:126px;border-radius:10px;display:grid;place-items:center;color:#637e69;background:radial-gradient(circle at 70% 30%,#f3ead4 0 12%,transparent 13%),linear-gradient(130deg,#e4ead9,#cbdac9)}.article-copy{display:flex;flex-direction:column;align-items:flex-start;min-width:0}.article-copy small{font-size:10px;color:#78927c}.article-copy strong{font-family:'Noto Serif SC',serif;font-size:18px;line-height:1.4;margin:8px 0}.article-copy>span{font-size:11px;color:#9aa399;overflow:hidden;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical}.article-copy em{font-style:normal;color:#54745b;display:flex;align-items:center;margin-top:auto;gap:4px;font-size:11px;font-weight:700}.article-line{width:100%;padding:14px 0;border-bottom:1px solid #f1eee8;display:flex;align-items:center;justify-content:space-between;font-size:12px;color:#57675b;gap:15px}.article-line:last-child{border-bottom:0;padding-bottom:0}.article-line:hover,.recent-session:hover{color:#2c6e41}.empty-content{padding:30px 0;color:#a5aba4;font-size:12px}.side-stack{display:grid;grid-template-rows:1fr auto;gap:18px}.quote-panel{min-height:235px;background:#f2e9df;border-radius:15px;padding:27px 32px;position:relative}.quote-panel .section-kicker{color:#a88672}.quote-mark{position:absolute;right:28px;top:22px;font:68px Georgia,serif;color:#e0c8b5;line-height:1}.quote-panel p{font-family:'Noto Serif SC',serif;color:#6c5f53;font-size:19px;line-height:1.7;margin:25px 0 22px;max-width:360px}.quote-bottom{display:flex;justify-content:space-between;align-items:center;color:#ad9786;font-size:11px}.quote-bottom button{background:#ffffff80;border:0;width:30px;height:30px;display:grid;place-items:center;border-radius:50%;cursor:pointer;color:#9d806f}.quote-bottom button:disabled{cursor:wait}.spinning{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}.recent-panel{padding:22px 26px}.recent-top{display:flex;align-items:center;justify-content:space-between;font-size:13px;font-weight:700}.recent-top a{display:flex;align-items:center;gap:4px;color:#7c977d;font-size:11px;font-weight:500}.recent-session{display:flex;align-items:center;gap:12px;width:100%;padding:18px 0 0}.session-icon{background:#e9f0e7;color:#6e8d72;border-radius:50%;width:37px;height:37px;display:grid;place-items:center;flex:none}.recent-session>span:nth-child(2){flex:1;min-width:0}.recent-session strong,.recent-session small{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.recent-session strong{font-size:12px}.recent-session small{font-size:10px;color:#a2aca2;margin-top:4px}.recent-empty{font-size:11px;color:#9aa59b;margin-top:17px}.page-footer{display:flex;align-items:center;justify-content:space-between;gap:12px;margin:58px 0 28px;padding-top:23px;border-top:1px solid #e6e9df;color:#9aab9b;font-size:11px}.page-footer span,.page-footer a{display:flex;align-items:center;gap:6px}.page-footer a{color:#8a9d89}.mobile-nav{display:none}
@media(max-width:1050px){.desktop-nav{gap:15px}.site-header{padding:0 23px}.hero-copy{padding:55px 25px 35px 40px}.hero h1{font-size:35px}.art-arch{right:4%}.art-vase{right:25%}.art-stem{right:37%}}
@media(max-width:760px){.site-header{height:70px;padding:0 17px}.desktop-nav,.classic-link{display:none}.page-shell{padding:0 13px}.eyebrow{padding-top:9px}.hero{grid-template-columns:1fr}.hero-copy{padding:42px 28px 28px}.hero h1{font-size:32px;letter-spacing:-1px}.hero-art{min-height:255px}.art-arch{height:290px;width:260px;right:16%}.art-shelf{bottom:25px}.art-vase{transform:scale(.72);transform-origin:bottom right;bottom:35px;right:32%}.art-stem,.art-leaf,.art-flower{transform:scale(.72);display:none}.art-caption{right:18px;bottom:9px}.section-block,.middle-grid{margin-top:49px}.feature-grid{grid-template-columns:1fr;gap:11px}.feature-card{min-height:190px}.feature-card h3{margin-top:15px}.middle-grid,.bottom-grid{grid-template-columns:1fr}.section-sub{display:none}.mobile-nav{display:flex;position:sticky;bottom:0;z-index:20;background:#fbfaf6ee;backdrop-filter:blur(14px);border-top:1px solid #e9e9df;justify-content:space-around;padding:8px 7px max(8px,env(safe-area-inset-bottom))}.mobile-nav a{display:flex;flex-direction:column;align-items:center;gap:3px;font-size:10px;color:#6e806f}.mobile-nav a.router-link-active{color:#315b3c}.page-footer{margin-bottom:25px}}
@media(max-width:470px){.hero h1{font-size:28px}.hero-copy>p{font-size:12px}.hero-actions{flex-direction:column}.hero-actions .btn{justify-content:space-between}.hero-foot{margin-top:24px}.checkin-row{padding:16px;gap:10px}.checkin-choice{gap:8px}.checkin-choice>span{display:none}.section-heading h2{font-size:22px}.journey-panel,.reading-panel{padding:24px 20px}.stats>div:not(:first-child){padding-left:13px}.stats strong{font-size:25px}.insight-panel{padding:27px}.featured-article{gap:12px}.article-visual{width:90px;height:110px}.article-copy strong{font-size:15px}.page-footer{font-size:9px}}
@media(max-width:760px){.art-stem,.art-leaf,.art-flower{display:block}.art-stem{right:46%;bottom:149px}.stem-one{height:86px;transform:rotate(-18deg)}.stem-two{height:104px;transform:rotate(13deg)}.stem-three{height:80px;transform:rotate(39deg)}.art-leaf{width:37px;height:15px}.leaf-one{right:49%;bottom:210px}.leaf-two{right:34%;bottom:224px}.leaf-three{right:50%;bottom:183px}.leaf-four{right:31%;bottom:187px}.art-flower{width:54px;height:54px;right:29%;bottom:209px;transform:scale(.68);transform-origin:center bottom}}

/* Editorial layer: stronger type, asymmetry and a quiet reading composition. */
.new-home { background: #faf9f5; }
.site-header { position: sticky; top: 0; z-index: 100; background: #faf9f5ed; backdrop-filter: blur(16px); border-bottom: 1px solid #e7e9df; }
.header-seal{width:40px;height:40px;flex:none;display:block;border-radius:13px;box-shadow:0 6px 17px #2d513c1b;transition:transform .28s,box-shadow .28s}.header-seal img{width:100%;height:100%;display:block}.header-seal:hover{transform:rotate(-6deg) translateY(-2px);box-shadow:0 10px 20px #2d513c30}.header-seal:focus-visible{outline:2px solid #315b3d;outline-offset:4px}
.brand strong { font-size: 23px; letter-spacing: -1.1px; }
.desktop-nav a { font-size: 12px; letter-spacing: .025em; }
.page-shell { max-width: 1320px; }
.eyebrow { padding: 27px 4px 20px; font-size: 10px; letter-spacing: .21em; }
.hero { grid-template-columns: 1.09fr .91fr; min-height: 510px; border-radius: 5px; background: #e9eee6; }
.hero-copy { padding: 76px 38px 48px 71px; display: flex; flex-direction: column; align-items: flex-start; }
.hero-kicker { display: flex; align-items: center; gap: 11px; color: #42654e; font-size: 10px; letter-spacing: .16em; }
.hero-kicker span { color: #bd8775; }
.hero h1 { width: 100%; margin: 25px 0 16px; font-size: clamp(40px, 4.35vw, 68px); font-weight: 500; line-height: 1.27; letter-spacing: -.065em; }
.hero-title-mask { display: block; overflow: hidden; padding-bottom: .11em; margin-bottom: -.11em; }
.hero-title-line { display: block; white-space: nowrap; }
.hero h1 em { color: #69896b; font-style: normal; }
.hero h1 i { color: #3b6247; font-weight: 600; font-style: normal; }
.hero-description { max-width: 430px !important; color: #64766a !important; font-size: 14px !important; line-height: 1.9 !important; text-wrap: pretty; }
.hero-actions { margin-top: 27px; }
.btn { border-radius: 3px; padding: 14px 23px; transition: transform .25s, background .25s, box-shadow .25s; }
.btn:hover { transform: translateY(-3px); box-shadow: 0 9px 20px #2c49381b; }
.btn:active { transform: translateY(1px); }
.hero-foot { margin-top: auto; padding-top: 35px; letter-spacing: .055em; }
.hero-art { min-height: 510px; background: radial-gradient(circle at 67% 36%,#fbf5e6 0,#e9ecdc 43%,#d6e1d3 100%); }
.hero-art::after { content: ''; position: absolute; inset: 0; z-index: 5; pointer-events: none; opacity: .11; background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 160 160' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.75' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='.16'/%3E%3C/svg%3E"); }
.art-arch { height: 455px; right: 10%; }
.art-vase { right: 26%; bottom: 64px; }
.art-shelf { bottom: 51px; }
.art-stem { bottom: 207px; right: 39%; }
.art-leaf { z-index: 4; }
.art-caption { bottom: 19px; font-size: 12px; }
.poster-index { position: absolute; z-index: 6; top: 32px; left: 32px; color: #506b55; font-size: 10px; font-weight: 700; letter-spacing: .17em; }
.poster-index span { padding: 0 12px; color: #c08c77; }
.poster-word { position: absolute; z-index: 6; left: 23px; bottom: 59px; color: #fffdf5; font: italic 500 clamp(51px,6vw,91px)/1 Georgia,serif; letter-spacing: -.085em; text-shadow: 0 3px 24px #365b485c; white-space: nowrap; }
.poster-word span { display: block; padding-left: 57px; font-size: .37em; letter-spacing: -.02em; line-height: 1.1; }
.checkin-row { border-radius: 3px; margin-top: 13px; box-shadow: none; border: 1px solid #e8ebe1; }
.section-block { margin-top: 88px; }
.section-heading { border-top: 1px solid #cbd6c9; padding-top: 22px; }
.section-heading h2,.panel-top h2 { font-size: clamp(26px, 2.5vw, 36px); font-weight: 500; line-height: 1.35; letter-spacing: -.045em; text-wrap: balance; }
.section-sub { font-family: 'Noto Serif SC',serif; font-size: 13px; color: #7e8e7d; }
.feature-grid { grid-template-columns: 1.2fr .95fr .95fr; gap: 12px; margin-top: 24px; }
.feature-card { min-height: 278px; border-radius: 3px; padding: 25px 27px; display: flex; flex-direction: column; box-shadow: none; }
.feature-chat { background: #315440; color: #f7f8ee; }
.feature-chat :is(.feature-icon,.feature-number,.feature-bottom,p) { color: #d8e4d4; }
.feature-chat .feature-icon { border-color: #d8e4d478; }
.feature-chat .feature-deco { opacity: .08; }
.feature-garden { background: #ecefdf; }
.feature-relax { background: #f3eae4; }
.feature-card h3 { font-size: 25px; font-weight: 500; letter-spacing: -.04em; margin: 27px 0 8px; }
.feature-card p { font-size: 13px; line-height: 1.8; }
.feature-card .feature-bottom { margin-top: auto; padding-top: 24px; }
.middle-grid { margin-top: 88px; }
.journey-panel,.reading-panel,.recent-panel,.insight-panel,.quote-panel { border-radius: 4px; }
.journey-panel,.reading-panel { padding: 35px 37px; }
.stats strong { font-size: 37px; font-variant-numeric: tabular-nums; }
.insight-panel p { font-size: 20px; line-height: 1.75; }
.quote-panel p { font-size: 22px; line-height: 1.65; }
.featured-article:hover .article-copy strong,.article-line:hover { color: #315e3d; }
.text-link,.feature-bottom,.checkin-choice a,.recent-top a { transition: gap .25s, color .25s; }
.text-link:hover,.feature-card:hover .feature-bottom,.checkin-choice a:hover,.recent-top a:hover { gap: 11px; }
.new-home :is(a,button):focus-visible { outline: 2px solid #477454; outline-offset: 4px; }
@media (max-width: 1050px) {
  .hero h1 { font-size: clamp(37px,4vw,51px); }
  .hero-copy { padding-left: 45px; }
  .poster-word { font-size: 64px; }
}
@media (max-width: 760px) {
  .header-seal{width:34px;height:34px;border-radius:10px}
  .hero { grid-template-columns: 1fr; min-height: 0; }
  .hero-copy { padding: 43px 26px 28px; }
  .hero h1 { font-size: clamp(33px,7vw,48px); }
  .hero-foot { margin-top: 4px; padding-top: 25px; }
  .hero-art { min-height: 292px; }
  .poster-index { top: 20px; left: 21px; font-size: 9px; }
  .poster-word { font-size: clamp(53px,11vw,73px); bottom: 40px; }
  .art-arch { height: 290px; right: 16%; }
  .art-vase { right: 32%; bottom: 35px; }
  .art-shelf { bottom: 25px; }
  .art-stem { right: 46%; bottom: 149px; }
  .feature-grid { grid-template-columns: 1fr; }
  .feature-card { min-height: 225px; }
  .section-block,.middle-grid { margin-top: 63px; }
}
@media (max-width: 470px) {
  .hero h1 { font-size: clamp(29px,8vw,38px); }
  .hero-kicker { font-size: 9px; letter-spacing: .07em; }
  .hero-description { font-size: 13px !important; }
  .poster-word { font-size: 52px; }
  .feature-card { min-height: 212px; }
  .journey-panel,.reading-panel { padding: 26px 22px; }
}
.site-header { padding-right: 32px; padding-left: 32px; }
.brand { gap: 10px; }
.brand-mark { overflow: hidden; box-shadow: 0 5px 14px #2d513c1b; }
.brand-mark img { display: block; width: 100%; height: 100%; }
.brand-copy { display: block; }
.desktop-nav { margin-left: auto; justify-content: flex-end; }
.header-end { flex: none; }
@media (max-width: 1050px) { .site-header { padding-right: 18px; padding-left: 18px; } }
@media (max-width: 760px) { .site-header { padding-right: 16px; padding-left: 16px; } .brand small { display: none; } .brand-mark { width: 36px; height: 36px; } }
.new-home { padding-top: 82px; box-sizing: border-box; }
@media (max-width: 900px) { .new-home { padding-top: 74px; } }
@media (max-width: 620px) { .new-home { padding-top: 70px; } }
.new-home .eyebrow { font-size: 12px; letter-spacing: .08em; }
.new-home .section-kicker { font-size: 12px; letter-spacing: .07em; }
.new-home .hero-kicker { font-size: 12px; letter-spacing: .06em; }
.new-home .poster-index { font-size: 11px; letter-spacing: .07em; }
.new-home .poster-word { font-family: 'Noto Serif SC', Georgia, serif; }
.new-home .poster-word span { font-size: .34em; letter-spacing: .04em; }
.new-home .art-caption { font: 500 12px/1.5 'Noto Serif SC', Georgia, serif; letter-spacing: .06em; }
.new-home .hero-art { isolation: isolate; background: #344b3c; }
.new-home .hero-art::before { content: ''; position: absolute; inset: 0; z-index: 4; pointer-events: none; background: linear-gradient(0deg, rgb(16 30 22 / .58), rgb(16 30 22 / .16) 35%, transparent 68%), linear-gradient(90deg, rgb(16 30 22 / .16), transparent 72%); }
.new-home .home-scene { position:absolute; inset:0; z-index:0; display:block; width:100%; height:100%; }
.new-home .poster-index { top: 29px; left: 29px; color: #304b39; font: 600 10px/1.4 'Noto Sans SC', sans-serif; letter-spacing: .14em; }
.new-home .poster-index span { padding: 0 8px; color: #a96550; }
.new-home .poster-word { left: 28px; bottom: 53px; color: #fffdf5; font: italic 500 clamp(54px, 6.5vw, 91px)/1.14 Georgia, serif; letter-spacing: -.075em; text-shadow: 0 3px 24px rgb(12 26 19 / .42); white-space: normal; padding-bottom: .04em; }
.new-home .poster-word span { padding-left: .54em; font-size: .76em; letter-spacing: -.055em; line-height: 1.14; }
.new-home .art-caption { right: 27px; bottom: 21px; z-index: 6; color: #fffdf5; font: italic 500 12px/1.5 Georgia, serif; letter-spacing: .025em; text-shadow: 0 2px 14px rgb(12 26 19 / .55); }
@media (max-width: 760px) {
  .new-home .poster-index { top: 19px; left: 19px; font-size: 9px; }
  .new-home .poster-word { left: 19px; bottom: 34px; font-size: clamp(47px, 13vw, 64px); }
  .new-home .art-caption { right: 16px; bottom: 12px; font-size: 10px; }
}
</style>
