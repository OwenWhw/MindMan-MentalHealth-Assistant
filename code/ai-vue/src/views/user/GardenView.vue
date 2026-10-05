<script setup>
import { ref, computed, onMounted, onUnmounted, reactive, watch } from 'vue'
import { useRouter } from 'vue-router'
import { gsap } from 'gsap'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Activity,
  BadgeCheck,
  BatteryLow,
  Check,
  CircleDot,
  CircleHelp,
  Cloud,
  CloudRain,
  EyeOff,
  Flame,
  Flower2,
  HandHeart,
  Heart,
  HeartCrack,
  LoaderCircle,
  Meh,
  Pencil,
  Smile,
  Sparkles,
  Sprout,
  Trash2,
  UserRound,
  Wind,
  X,
  Zap
} from 'lucide-vue-next'
import {
  getGarden,
  analyzeGardenNote,
  plantFlower,
  updateFlower,
  deleteFlower
} from '@/api/emotion'
import { useEmotionStore } from '@/stores/emotion'
import AppNavBar from '@/components/AppNavBar.vue'
import UserDropdown from '@/components/UserDropdown.vue'
import { USER_NAV_ACTIONS } from '@/constants/userNavigation'

const router = useRouter()
const emotionStore = useEmotionStore()

const flowers = ref([])
const gardenRoot = ref(null)
const loading = ref(true)
const planting = ref(false)
const todayPlanted = ref(false)
const currentTime = ref(new Date())

// 空花园也保留几朵轻盈的装饰花，让首次进入时的场景更完整。
// 这些只是纯视觉元素，不会进入情绪数量或统计。
const emptyGardenBlooms = [
  { id: 1, x: '7%', stem: '48px', size: '27px', color: '#e8a99e', delay: '-1.4s', shape: 'round' },
  { id: 2, x: '18%', stem: '72px', size: '34px', color: '#e7c86f', delay: '-.6s', shape: 'star' },
  { id: 3, x: '30%', stem: '55px', size: '29px', color: '#b8a4cb', delay: '-2s', shape: 'soft' },
  { id: 4, x: '68%', stem: '63px', size: '31px', color: '#e99da7', delay: '-1s', shape: 'star' },
  { id: 5, x: '81%', stem: '78px', size: '36px', color: '#e4c577', delay: '-2.6s', shape: 'round' },
  { id: 6, x: '92%', stem: '47px', size: '27px', color: '#b5a0c7', delay: '-.2s', shape: 'soft' }
]

// 定时刷新时间与页面内容进场动画；导航栏不参与动画。
let timeTimer = 0
let entranceContext
onMounted(() => {
  timeTimer = setInterval(() => currentTime.value = new Date(), 60000)
  loadGarden()

  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  entranceContext = gsap.context(() => {
    gsap.from('.garden-page-heading', { y: 16, opacity: 0, duration: 0.65, ease: 'power2.out' })
    gsap.from('.stat-card', { y: 13, opacity: 0, duration: 0.55, stagger: 0.07, delay: 0.08, ease: 'power2.out' })
    gsap.from('.garden-scene', { y: 16, opacity: 0, duration: 0.75, delay: 0.18, ease: 'power2.out' })
    gsap.from('.plant-bar', { y: 10, opacity: 0, duration: 0.5, delay: 0.3, ease: 'power2.out' })
  }, gardenRoot.value)
})
onUnmounted(() => {
  clearInterval(timeTimer)
  entranceContext?.revert()
})

// 18 种情绪使用同一套线性图标，保留原有颜色与分组语义。
const EMOTION_META = {
  // ===== 积极（5）=====
  开心:    { color: '#c68c32', bg: '#f8eed6', icon: Smile, label: '开心', group: 'positive' },
  感恩:    { color: '#be8193', bg: '#f5e8eb', icon: HandHeart, label: '感恩', group: 'positive' },
  期待:    { color: '#7c9db8', bg: '#e8f0f4', icon: Sparkles, label: '期待', group: 'positive' },
  欣慰:    { color: '#6e9a79', bg: '#e8f0e7', icon: Heart, label: '欣慰', group: 'positive' },
  自豪:    { color: '#b39a50', bg: '#f3efd9', icon: BadgeCheck, label: '自豪', group: 'positive' },

  // ===== 平稳（3）=====
  平静:    { color: '#73957c', bg: '#e8f0e7', icon: CircleDot, label: '平静', group: 'neutral' },
  放松:    { color: '#739b9a', bg: '#e6f0ed', icon: Wind, label: '放松', group: 'neutral' },
  无聊:    { color: '#999985', bg: '#efefe7', icon: Meh, label: '无聊', group: 'neutral' },

  // ===== 消极（10）=====
  疲惫:    { color: '#8f9990', bg: '#eceee8', icon: BatteryLow, label: '疲惫', group: 'negative' },
  焦虑:    { color: '#c18e61', bg: '#f4ece2', icon: Activity, label: '焦虑', group: 'negative' },
  担心:    { color: '#9788a6', bg: '#efebf1', icon: Cloud, label: '担心', group: 'negative' },
  孤独:    { color: '#858b87', bg: '#edeeea', icon: UserRound, label: '孤独', group: 'negative' },
  生气:    { color: '#b77263', bg: '#f3e8e2', icon: Flame, label: '生气', group: 'negative' },
  低落:    { color: '#7c8da5', bg: '#e9edf1', icon: CloudRain, label: '低落', group: 'negative' },
  困惑:    { color: '#9487a2', bg: '#efeaf1', icon: CircleHelp, label: '困惑', group: 'negative' },
  烦躁:    { color: '#be7b73', bg: '#f4e9e6', icon: Zap, label: '烦躁', group: 'negative' },
  委屈:    { color: '#b9808c', bg: '#f4e9eb', icon: HeartCrack, label: '委屈', group: 'negative' },
  自卑:    { color: '#8a898b', bg: '#efeeec', icon: EyeOff, label: '自卑', group: 'negative' }
}
const moodOptions = Object.keys(EMOTION_META)
const MOOD_GROUPS = [
  { key: 'positive', label: '积极', icon: Sparkles },
  { key: 'neutral', label: '平稳', icon: Sprout },
  { key: 'negative', label: '消极', icon: CloudRain }
]

// 情绪触发因素选项（与后台情绪日志列表对齐）
const TRIGGER_OPTIONS = [
  { label: '工作', value: '工作' },
  { label: '学习', value: '学习' },
  { label: '人际关系', value: '人际关系' },
  { label: '家庭', value: '家庭' },
  { label: '健康', value: '健康' },
  { label: '经济', value: '经济' },
  { label: '睡眠', value: '睡眠' },
  { label: '其他', value: '其他' }
]

// 今日进度
const todayProgress = computed(() => {
  if (todayPlanted.value) return 100
  const now = currentTime.value
  const hours = now.getHours()
  const minutes = now.getMinutes()
  return Math.min(95, Math.round(((hours * 60 + minutes) / (24 * 60)) * 100))
})

const progressLabel = computed(() => {
  if (todayPlanted.value) return '今日已种'
  if (todayProgress.value > 80) return '快到午夜了，抓紧种花'
  if (todayProgress.value > 50) return '今天已过半，心情如何？'
  if (todayProgress.value > 30) return '上午的时光，种朵花吧'
  return '新的一天，用心情种花'
})

function todayStr() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
function isToday(dateStr) { return dateStr === todayStr() }

const stats = computed(() => {
  const list = flowers.value
  const today = todayStr()
  const weekAgoDate = new Date(Date.now() - 6 * 864e5)
  const p = (n) => String(n).padStart(2, '0')
  const weekAgo = `${weekAgoDate.getFullYear()}-${p(weekAgoDate.getMonth() + 1)}-${p(weekAgoDate.getDate())}`
  const weekNew = list.filter((f) => f.date >= weekAgo && f.date <= today).length
  const verifiedRatings = list.filter((flower) => flower.ratingSource === 'self_reported')
  const emotionScores = verifiedRatings.map((flower) => Number(flower.emotionScore)).filter((score) => score >= 1 && score <= 5)
  const avgEmotion = emotionScores.length
    ? (emotionScores.reduce((sum, score) => sum + score, 0) / emotionScores.length).toFixed(1)
    : '暂无'

  // 总分布
  const dist = {}
  list.forEach((f) => {
    dist[f.emotion] = (dist[f.emotion] || 0) + 1
  })

  // 按情绪组（积极/平稳/消极）聚合
  const groups = { positive: 0, neutral: 0, negative: 0 }
  list.forEach((f) => {
    const group = EMOTION_META[f.emotion]?.group || 'neutral'
    groups[group]++
  })
  const total = list.length || 1

  return {
    total: list.length,
    weekNew,
    avgEmotion,
    ratedCount: emotionScores.length,
    dist,
    max: Math.max(1, ...Object.values(dist)),
    groups,
    groupPct: {
      positive: Math.round((groups.positive / total) * 100),
      neutral: Math.round((groups.neutral / total) * 100),
      negative: Math.round((groups.negative / total) * 100)
    }
  }
})

function flowerStage(flower) {
  const planted = new Date(flower.date)
  const now = new Date()
  const days = Math.floor((now - planted) / 864e5)
  if (days <= 2) return 'seed'
  if (days <= 7) return 'bud'
  return 'bloom'
}

function flowerPetalCount(slot) {
  return 5 + (slot.id % 3)
}

function flowerPetalStyle(index, total) {
  return {
    '--petal-angle': `${(360 / total) * index}deg`,
    '--petal-delay': `${index * 45}ms`
  }
}

const flowerSlots = computed(() => {
  return flowers.value.slice(0, 24).map((flower, idx) => {
    const meta = flower ? (EMOTION_META[flower.emotion] || EMOTION_META['平静']) : null
    const stage = flower ? flowerStage(flower) : 'empty'
    return { id: idx, flower, meta, stage }
  })
})
const flowerGridRows = computed(() => Math.max(1, Math.ceil(Math.min(flowers.value.length, 24) / 3)))

async function loadGarden() {
  loading.value = true
  try {
    const list = await getGarden()
    flowers.value = list || []
    todayPlanted.value = flowers.value.some((f) => f.date === todayStr())
  } catch (e) {
    if (!e?.handled) ElMessage.error(e.message || '加载情绪花园失败')
  } finally {
    loading.value = false
  }
}

// ===== 种花表单 =====
const formVisible = ref(false)
const legacyRatingNotice = ref(false)
const formMode = ref('create') // 'create' | 'edit'
const submitting = ref(false)
const formRef = ref(null)
const formInsight = ref(null)
const formInsightLoading = ref(false)
const detailInsight = ref(null)
const detailInsightLoading = ref(false)
let formInsightRequestId = 0
let detailInsightRequestId = 0

const form = reactive({
  flowerId: null,
  emotion: '',
  content: '',
  emotionScore: 0,
  sleepScore: 0,
  stressScore: 0,
  trigger: ''
})

watch(() => [form.emotion, form.content, form.trigger], () => {
  formInsightRequestId++
  formInsight.value = null
  formInsightLoading.value = false
})

watch(formVisible, (visible) => {
  if (!visible) {
    formInsightRequestId++
    formInsight.value = null
    formInsightLoading.value = false
  }
})

async function reviewFormNote() {
  if (!form.emotion || !form.content.trim()) {
    ElMessage.info('先选一种心情，再写下今天发生了什么')
    return
  }
  const requestId = ++formInsightRequestId
  const payload = { emotion: form.emotion, content: form.content.trim(), trigger: form.trigger }
  formInsightLoading.value = true
  formInsight.value = null
  try {
    const insight = await analyzeGardenNote(payload)
    if (requestId === formInsightRequestId && formVisible.value) formInsight.value = insight
  } catch (error) {
    if (requestId === formInsightRequestId && formVisible.value) formInsight.value = { source: 'unavailable' }
  } finally {
    if (requestId === formInsightRequestId) formInsightLoading.value = false
  }
}

async function reviewDetailNote() {
  const flower = detailFlower.value
  if (!flower?.content?.trim()) {
    ElMessage.info('这朵花还没有文字记录，可以先编辑补充')
    return
  }
  const requestId = ++detailInsightRequestId
  detailInsightLoading.value = true
  detailInsight.value = null
  try {
    const insight = await analyzeGardenNote({ emotion: flower.emotion, content: flower.content.trim(), trigger: flower.trigger || '' })
    if (requestId === detailInsightRequestId && detailVisible.value) detailInsight.value = insight
  } catch (error) {
    if (requestId === detailInsightRequestId && detailVisible.value) detailInsight.value = { source: 'unavailable' }
  } finally {
    if (requestId === detailInsightRequestId) detailInsightLoading.value = false
  }
}

function insightScores(insight) {
  return [
    { label: '情绪', value: insight?.emotionScore },
    { label: '睡眠', value: insight?.sleepScore },
    { label: '压力', value: insight?.stressScore }
  ]
}

function validateSelfRating(_rule, value, callback) {
  if (Number(value) >= 1 && Number(value) <= 5) callback()
  else callback(new Error('请按自己的感受选择评分'))
}

const formRules = {
  emotion: [{ required: true, message: '请选择一种心情', trigger: 'change' }],
  content: [{ required: true, message: '请写一点今天的心情', trigger: 'blur' }],
  emotionScore: [{ validator: validateSelfRating, trigger: 'change' }],
  sleepScore: [{ validator: validateSelfRating, trigger: 'change' }],
  stressScore: [{ validator: validateSelfRating, trigger: 'change' }]
}

function openPlantForm() {
  if (todayPlanted.value) {
    ElMessage.info('今天已经种过花了，明天再来吧')
    return
  }
  formMode.value = 'create'
  legacyRatingNotice.value = false
  const latest = emotionStore.latest
  form.flowerId = null
  form.emotion = moodOptions.includes(latest?.emotion) ? latest.emotion : ''
  form.content = ''
  form.emotionScore = 0
  form.sleepScore = 0
  form.stressScore = 0
  form.trigger = latest?.trigger || ''
  formVisible.value = true
}

function openEditForm(flower) {
  formMode.value = 'edit'
  legacyRatingNotice.value = flower.ratingSource !== 'self_reported'
    && [flower.emotionScore, flower.sleepScore, flower.stressScore].some((score) => Number(score) >= 1 && Number(score) <= 5)
  form.flowerId = flower.flowerId
  form.emotion = flower.emotion
  form.content = flower.content || ''
  form.emotionScore = flower.emotionScore ?? 0
  form.sleepScore = flower.sleepScore ?? 0
  form.stressScore = flower.stressScore ?? 0
  form.trigger = flower.trigger || ''
  formVisible.value = true
}

async function submitForm() {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch (e) { return }
  submitting.value = true
  try {
    if (formMode.value === 'create') {
      await plantFlower({ ...form })
      ElMessage.success(`已种下一朵心情之花`)
    } else {
      await updateFlower(form.flowerId, { ...form })
      ElMessage.success('修改成功')
    }
    formVisible.value = false
    await loadGarden()
  } catch (e) {
    if (!e?.handled) ElMessage.error(e.message || '操作失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

async function removeFlower(flower) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${flower.date}」的「${flower.emotion}」记录吗？`,
      '删除花朵',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteFlower(flower.flowerId)
    ElMessage.success('已删除')
    detailVisible.value = false
    await loadGarden()
  } catch (e) {
    if (e !== 'cancel' && !e?.handled) ElMessage.error('删除失败')
  }
}

// ===== 花朵详情 =====
const detailVisible = ref(false)
const detailFlower = ref(null)
function openDetail(flower) {
  detailInsightRequestId++
  detailInsight.value = null
  detailFlower.value = flower
  detailVisible.value = true
}
function editFromDetail() {
  const flower = detailFlower.value
  detailVisible.value = false
  openEditForm(flower)
}

function dateLabel(dateStr) {
  if (!dateStr) return ''
  if (isToday(dateStr)) return '今天'
  const d = new Date(dateStr)
  const now = new Date()
  const diff = Math.floor((now - d) / 864e5)
  if (diff === 1) return '昨天'
  if (diff <= 3) return `${diff}天前`
  return dateStr.slice(5)
}

</script>

<template>
  <div ref="gardenRoot" class="garden">
    <!-- 顶部导航 -->
    <AppNavBar
      :actions="USER_NAV_ACTIONS"
      :current-path="'/garden'"
    >
      <template #extra>
        <!-- 今日进度条 -->
        <div class="nav-progress">
          <div class="progress-info">
            <span class="progress-label">{{ progressLabel }}</span>
            <span class="progress-pct">{{ todayProgress }}%</span>
          </div>
          <div class="progress-track">
            <div class="progress-fill" :style="{ width: todayProgress + '%' }"></div>
          </div>
        </div>
      </template>
      <template #actions-after>
        <UserDropdown />
      </template>
    </AppNavBar>

    <section class="garden-page-heading" aria-labelledby="garden-heading">
      <div><span class="garden-heading-kicker">记录心情，看见变化</span><h1 id="garden-heading">情绪花园 <em>慢慢生长。</em></h1><p class="garden-whisper" aria-hidden="true">grow at your own pace.</p></div>
      <div class="garden-heading-actions">
        <p>每一种心情，都值得被记录。</p>
        <button type="button" class="garden-ai-review" @click="router.push({ path: '/consult', query: { gardenReview: '1' } })">
          <Sparkles :size="15" :stroke-width="1.8" aria-hidden="true" />
          <span>和 AI 回看近况</span>
        </button>
      </div>
    </section>

    <!-- 统计 -->
    <section class="garden-stats">
      <div class="stat-card">
        <span class="stat-num">{{ stats.total }}</span>
        <span class="stat-label">花朵总数</span>
      </div>
      <div class="stat-card">
        <span class="stat-num">{{ stats.avgEmotion }}</span>
        <span class="stat-label">自评均分 · {{ stats.ratedCount }} 条</span>
      </div>
      <div class="stat-card">
        <span class="stat-num">+{{ stats.weekNew }}</span>
        <span class="stat-label">本周新开</span>
      </div>
      <div class="stat-card stat-dist">
        <div class="dist-head">
          <span class="stat-label">心情分布</span>
          <span class="dist-total">{{ stats.total }} 朵</span>
        </div>

        <!-- 三类情绪占比（堆叠条） -->
        <div class="dist-stack">
          <div class="stack-seg positive" :style="{ flex: stats.groups.positive }" :title="`积极 ${stats.groupPct.positive}%`"></div>
          <div class="stack-seg neutral"  :style="{ flex: stats.groups.neutral }"  :title="`平稳 ${stats.groupPct.neutral}%`"></div>
          <div class="stack-seg negative" :style="{ flex: stats.groups.negative }" :title="`消极 ${stats.groupPct.negative}%`"></div>
        </div>
        <div class="dist-legend">
          <span class="lg-item positive">
            <span class="lg-dot"></span>积极 {{ stats.groupPct.positive }}%
          </span>
          <span class="lg-item neutral">
            <span class="lg-dot"></span>平稳 {{ stats.groupPct.neutral }}%
          </span>
          <span class="lg-item negative">
            <span class="lg-dot"></span>消极 {{ stats.groupPct.negative }}%
          </span>
        </div>

        <!-- 三列详细气泡 -->
        <div class="dist-columns">
          <div v-for="group in MOOD_GROUPS" :key="group.key" class="dist-col">
            <div class="col-head">
              <component :is="group.icon" class="col-icon" :size="13" aria-hidden="true" />
              <span class="col-name">{{ group.label }}</span>
              <span class="col-count">{{ stats.groups[group.key] }}</span>
            </div>
            <div class="bubbles">
              <span
                v-for="e in moodOptions.filter(k => EMOTION_META[k].group === group.key && stats.dist[k])"
                :key="e"
                class="bubble"
                :style="{ '--bg': EMOTION_META[e].bg, '--c': EMOTION_META[e].color, '--size': 24 + Math.min(20, (stats.dist[e] / Math.max(1, stats.groups[group.key])) * 50) + 'px' }"
              >
                <component :is="EMOTION_META[e].icon" class="bubble-icon" :size="13" :stroke-width="1.8" aria-hidden="true" />
                <span class="bubble-count">{{ stats.dist[e] }}</span>
              </span>
              <span v-if="!moodOptions.some(k => EMOTION_META[k].group === group.key && stats.dist[k])" class="bubble-empty">0</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 花园场景 -->
    <main class="garden-scene" :style="{ '--garden-rows': flowerGridRows }">
      <div class="sky-sun"></div>
      <div class="sky-cloud cloud-1"></div>
      <div class="sky-cloud cloud-2"></div>
      <div class="ground-layer ground-back"></div>
      <div class="ground-layer ground-front"></div>

      <div v-if="!loading && !flowers.length" class="empty-blooms" aria-hidden="true">
        <div
          v-for="bloom in emptyGardenBlooms"
          :key="bloom.id"
          class="empty-bloom"
          :style="{
            '--flower-x': bloom.x,
            '--stem-height': bloom.stem,
            '--flower-size': bloom.size,
            '--flower-color': bloom.color,
            '--bloom-delay': bloom.delay
          }"
        >
          <span class="empty-stem"></span>
          <span class="empty-leaf leaf-left"></span>
          <span class="empty-leaf leaf-right"></span>
          <span class="empty-flower-head" :class="`empty-shape-${bloom.shape}`">
            <i v-for="petal in 6" :key="petal" class="empty-petal" :style="flowerPetalStyle(petal - 1, 6)"></i>
            <i class="empty-core"></i>
          </span>
        </div>
      </div>

      <div v-if="loading" class="garden-loading">
        <LoaderCircle class="garden-loading-icon" :size="24" aria-hidden="true" />
      </div>
      <p v-else-if="!flowers.length" class="garden-empty">
        <span>土地还空着，种下今天的心情吧</span>
        <small>给今天一份温柔的记录</small>
      </p>

      <div v-else class="flower-grid">
        <div
          v-for="slot in flowerSlots"
          :key="slot.id"
          class="flower-cell clickable"
          @click="openDetail(slot.flower)"
        >
          <div
            class="flower-head"
            :class="[slot.stage, `flower-shape-${slot.id % 3}`]"
            :style="{ '--petal': slot.meta?.color }"
          >
            <div
              v-for="petal in flowerPetalCount(slot)"
              :key="petal"
              class="petal"
              :style="flowerPetalStyle(petal - 1, flowerPetalCount(slot))"
            ></div>
            <div class="core"></div>
          </div>
          <div class="stem" :class="slot.stage"></div>
          <div class="dirt"></div>
          <div class="leaf" :class="slot.stage"></div>
          <div class="leaf leaf-secondary" :class="slot.stage"></div>
          <!-- 标签：显示日期 + 情绪小图标 -->
          <div class="flower-tag">
            <component :is="slot.meta?.icon || Flower2" class="tag-mood-icon" :size="12" :stroke-width="1.8" aria-hidden="true" />
            <span class="tag-date">{{ dateLabel(slot.flower.date) }}</span>
            <span class="tag-score" v-if="slot.flower.ratingSource === 'self_reported' && slot.flower.emotionScore">{{ slot.flower.emotionScore }}</span>
          </div>
        </div>
      </div>
    </main>

    <!-- 底部：种花按钮 -->
    <footer class="garden-plant">
      <div class="plant-bar">
        <div class="plant-bar-info">
          <span class="plant-bar-title">今日种花</span>
          <span class="plant-bar-sub">
            <span v-if="todayPlanted" class="plant-complete-copy"><Check :size="13" aria-hidden="true" />今天已经种下啦，明天继续</span>
            <template v-else-if="emotionStore.latest">可参考 AI 识别的心情；评分由你自己填写</template>
            <template v-else>每种心情都会长成一朵独特的花</template>
          </span>
        </div>
        <button
          class="plant-cta"
          :disabled="todayPlanted"
          @click="openPlantForm"
        >
          <Flower2 :size="16" />
          <span>{{ todayPlanted ? '今日已种' : '种下今日心情' }}</span>
        </button>
      </div>
    </footer>

    <!-- 花朵详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      width="440px"
      :show-close="false"
      class="flower-dialog"
    >
      <template #header>
        <div class="dialog-head">
          <span class="dialog-icon-mark" :style="{ '--mood-color': EMOTION_META[detailFlower?.emotion]?.color }">
            <component :is="EMOTION_META[detailFlower?.emotion]?.icon || Flower2" :size="20" :stroke-width="1.7" />
          </span>
          <div class="dialog-heading-copy">
            <span class="dialog-kicker">这份心情的记录</span>
            <div class="dialog-title">{{ detailFlower?.emotion }} · {{ detailFlower?.date }}</div>
            <div class="dialog-sub">{{ dateLabel(detailFlower?.date) }}种下的花</div>
          </div>
          <button type="button" class="dialog-close" aria-label="关闭详情" @click="detailVisible = false"><X :size="18" /></button>
        </div>
      </template>
      <div v-if="detailFlower" class="dialog-body">
        <div class="dialog-content">{{ detailFlower.content || '（未填写日记内容）' }}</div>
        <div v-if="detailFlower.trigger" class="dialog-trigger">
          <span class="dt-label">触发因素</span>
          <span class="dt-tag">{{ detailFlower.trigger }}</span>
        </div>
        <section class="garden-insight" aria-label="这条记录的 AI 回看">
          <div class="garden-insight-head">
            <span class="garden-insight-title"><Sparkles :size="16" aria-hidden="true" />AI 回看</span>
            <button type="button" class="garden-insight-action" :disabled="detailInsightLoading" @click="reviewDetailNote">
              <LoaderCircle v-if="detailInsightLoading" class="insight-spinner" :size="14" aria-hidden="true" />
              {{ detailInsightLoading ? '正在回看…' : detailInsight ? '重新回看' : '回看这条记录' }}
            </button>
          </div>
          <div v-if="detailInsight?.source === 'agent'" class="garden-insight-result" aria-live="polite">
            <p class="insight-evidence">来自你的记录：“{{ detailInsight.evidence }}”</p>
            <p>{{ detailInsight.observation }}</p>
            <div class="insight-scores">
              <div v-for="item in insightScores(detailInsight)" :key="item.label" class="insight-score">
                <span class="insight-score-label">{{ item.label }}</span>
                <strong>{{ item.value ? `${item.value.score}/5` : '依据不足' }}</strong>
                <small v-if="item.value">{{ item.value.reason }} · “{{ item.value.evidence }}”</small>
              </div>
            </div>
            <p class="insight-question">留给自己的问题：{{ detailInsight.question }}</p>
          </div>
          <p v-else-if="detailInsight" class="insight-unavailable" aria-live="polite">{{ detailInsight.observation || 'AI 暂时无法分析这条记录，请稍后重试。' }}</p>
          <p v-else class="insight-help">点击后会将这条文字发送给当前 AI 服务；分析不会改动你的评分。</p>
        </section>
        <p v-if="detailFlower.ratingSource !== 'self_reported' && [detailFlower.emotionScore, detailFlower.sleepScore, detailFlower.stressScore].some(score => Number(score) >= 1 && Number(score) <= 5)" class="legacy-rating-note">
          这条历史评分没有来源标记，不纳入自评均分或 AI 趋势分析。编辑并确认评分后，可保存为自评记录。
        </p>
        <div class="dialog-stars">
          <div class="dialog-star-row">
            <span class="ds-label">情绪评分</span>
            <el-rate :model-value="detailFlower.emotionScore || 0" disabled :colors="['#c69f54','#c69f54','#c69f54']" />
            <span class="ds-val">{{ detailFlower.emotionScore || 0 }}/5</span>
          </div>
          <div class="dialog-star-row">
            <span class="ds-label">睡眠质量</span>
            <el-rate :model-value="detailFlower.sleepScore || 0" disabled :colors="['#c69f54','#c69f54','#c69f54']" />
            <span class="ds-val">{{ detailFlower.sleepScore || 0 }}/5</span>
          </div>
          <div class="dialog-star-row">
            <span class="ds-label">压力水平</span>
            <el-rate :model-value="detailFlower.stressScore || 0" disabled :colors="['#c69f54','#c69f54','#c69f54']" />
            <span class="ds-val">{{ detailFlower.stressScore || 0 }}/5</span>
          </div>
        </div>
      </div>
      <template #footer>
        <div class="dialog-foot">
          <button class="dialog-btn ghost" @click="removeFlower(detailFlower)">
            <Trash2 :size="15" aria-hidden="true" />
            <span>删除</span>
          </button>
          <button class="dialog-btn primary" @click="editFromDetail">
            <Pencil :size="15" aria-hidden="true" />
            <span>编辑</span>
          </button>
        </div>
      </template>
    </el-dialog>

    <!-- 种花/编辑弹窗 -->
    <el-dialog
      v-model="formVisible"
      :title="formMode === 'create' ? '种下今日心情' : '编辑这朵花'"
      width="560px"
      :show-close="false"
      class="flower-dialog flower-form-dialog"
    >
      <template #header>
        <div class="form-dialog-head">
          <span class="dialog-icon-mark form-icon-mark"><Flower2 :size="20" :stroke-width="1.7" /></span>
          <div class="dialog-heading-copy">
            <span class="dialog-kicker">{{ formMode === 'create' ? '给今天留一朵花' : '让这份记录更完整' }}</span>
            <h2 class="dialog-title">{{ formMode === 'create' ? '种下今天的心情' : '编辑这朵花' }}</h2>
            <p class="dialog-sub">一两句话，也足以照看好此刻的自己。</p>
          </div>
          <button type="button" class="dialog-close" aria-label="关闭表单" @click="formVisible = false"><X :size="18" /></button>
        </div>
      </template>
      <el-form ref="formRef" class="garden-form" :model="form" :rules="formRules" label-position="top">
        <p v-if="legacyRatingNotice" class="legacy-rating-note form-legacy-rating-note">
          这条旧记录的评分来源未记录。请按现在的记忆重新确认或调整评分，保存后才会计入自评统计。
        </p>
        <el-form-item label="今天的心情" prop="emotion">
          <div class="form-chips">
            <button
              v-for="mood in moodOptions"
              :key="mood"
              type="button"
              class="form-chip"
              :class="{ active: form.emotion === mood }"
              :style="{ '--chip-color': EMOTION_META[mood]?.color }"
              @click="form.emotion = mood"
            >
              <component :is="EMOTION_META[mood]?.icon" class="mood-chip-icon" :size="15" :stroke-width="1.8" aria-hidden="true" />
              <span class="mood-chip-label">{{ EMOTION_META[mood]?.label }}</span>
            </button>
          </div>
        </el-form-item>

        <el-form-item label="写一句话记录今天" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="3"
            maxlength="120"
            show-word-limit
            placeholder="比如：和朋友聚餐聊得很开心 / 加班有点累但坚持下来了…"
          />
        </el-form-item>

        <el-form-item label="是什么触发了这种情绪？">
          <el-select v-model="form.trigger" placeholder="选择触发因素（可选）" clearable style="width: 100%">
            <el-option
              v-for="opt in TRIGGER_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <section class="garden-insight" aria-label="当前记录的 AI 回看">
          <div class="garden-insight-head">
            <span class="garden-insight-title"><Sparkles :size="16" aria-hidden="true" />AI 回看这一天</span>
            <button type="button" class="garden-insight-action" :disabled="formInsightLoading" @click="reviewFormNote">
              <LoaderCircle v-if="formInsightLoading" class="insight-spinner" :size="14" aria-hidden="true" />
              {{ formInsightLoading ? '正在回看…' : formInsight ? '重新回看' : '分析这条记录' }}
            </button>
          </div>
          <div v-if="formInsight?.source === 'agent'" class="garden-insight-result" aria-live="polite">
            <p class="insight-evidence">来自你的记录：“{{ formInsight.evidence }}”</p>
            <p>{{ formInsight.observation }}</p>
            <div class="insight-scores">
              <div v-for="item in insightScores(formInsight)" :key="item.label" class="insight-score">
                <span class="insight-score-label">{{ item.label }}</span>
                <strong>{{ item.value ? `${item.value.score}/5` : '依据不足' }}</strong>
                <small v-if="item.value">{{ item.value.reason }} · “{{ item.value.evidence }}”</small>
              </div>
            </div>
            <p class="insight-question">留给自己的问题：{{ formInsight.question }}</p>
          </div>
          <p v-else-if="formInsight" class="insight-unavailable" aria-live="polite">{{ formInsight.observation || 'AI 暂时无法分析这条记录。你仍可正常保存，稍后在花朵详情里重试。' }}</p>
          <p v-else class="insight-help">先写下具体的一刻。点击分析后，本条文字会发送给当前 AI 服务。</p>
        </section>

        <div class="form-rates">
          <p class="rating-scale">自己评分 · 情绪 1 低落至 5 愉快，睡眠 1 差至 5 好，压力 1 轻至 5 重</p>
          <el-form-item class="rate-form-item" prop="emotionScore">
          <div class="rate-block">
            <span class="rate-icon mood-rate-icon"><Sparkles :size="15" /></span>
            <span class="rate-label">情绪评分</span>
            <el-rate v-model="form.emotionScore" :colors="['#c69f54','#c69f54','#c69f54']" />
          </div>
          </el-form-item>
          <el-form-item class="rate-form-item" prop="sleepScore">
          <div class="rate-block">
            <span class="rate-icon sleep-rate-icon"><Cloud :size="15" /></span>
            <span class="rate-label">睡眠质量</span>
            <el-rate v-model="form.sleepScore" :colors="['#c69f54','#c69f54','#c69f54']" />
          </div>
          </el-form-item>
          <el-form-item class="rate-form-item" prop="stressScore">
          <div class="rate-block">
            <span class="rate-icon stress-rate-icon"><Activity :size="15" /></span>
            <span class="rate-label">压力水平</span>
            <el-rate v-model="form.stressScore" :colors="['#c69f54','#c69f54','#c69f54']" />
          </div>
          </el-form-item>
          <p class="rating-note">AI 参考分不会自动保存，请按实际感受填写。</p>
        </div>
      </el-form>
      <template #footer>
        <div class="dialog-foot">
          <button class="dialog-btn ghost" @click="formVisible = false">
            <span>取消</span>
          </button>
          <button class="dialog-btn primary" :disabled="submitting" @click="submitForm">
            <span>{{ formMode === 'create' ? '种下这朵花' : '保存修改' }}</span>
            <Flower2 v-if="!submitting" :size="16" />
          </button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.garden {
  position: relative;
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #f8f7f2;
}

.garden-page-heading,
.garden-stats,
.garden-scene,
.garden-plant {
  font-family: 'Inter', 'Noto Sans SC', sans-serif;
}

.nav-progress { flex: 1; min-width: 120px; max-width: 300px; }
.progress-info { display: flex; justify-content: space-between; margin-bottom: 5px; }
.progress-label { font-size: 11px; color: #64748b; }
.progress-pct { font-size: 11px; font-weight: 700; color: #059669; }
.progress-track { height: 6px; border-radius: 999px; background: rgba(52, 211, 153, 0.15); overflow: hidden; }
.progress-fill { height: 100%; border-radius: 999px; background: linear-gradient(90deg, #34d399, #059669); transition: width 0.6s ease; }

/* ===== 统计 ===== */
.garden-stats {
  position: relative; z-index: 4;
  display: grid; grid-template-columns: 96px 96px 96px 1fr;
  gap: 10px; max-width: 1100px; width: calc(100% - 48px); margin: 16px auto 0;
}
.garden-page-heading { display:flex; align-items:end; justify-content:space-between; gap:16px; width:calc(100% - 48px); max-width:1100px; margin:98px auto 0; padding-bottom:3px; }
.garden-page-heading .garden-heading-kicker { color:#789277; font-size:12px; font-weight:600; letter-spacing:.07em; }
.garden-page-heading h1 { margin:8px 0 0; color:#2d4936; font:500 clamp(26px,3vw,36px)/1.25 'Noto Serif SC',Georgia,serif; letter-spacing:-.05em; }
.garden-page-heading h1 em { color:#829b78; font-style:normal; }
.garden-page-heading p { margin:0 0 4px; color:#849385; font-size:12px; }
.garden-heading-actions { display:flex; flex-direction:column; align-items:flex-end; gap:12px; }
.garden-ai-review { display:inline-flex; align-items:center; gap:7px; min-height:38px; padding:0 13px; border:1px solid #dbe5d7; border-radius:12px; background:#fffdf9; color:#55785a; font:600 11px/1.2 'Inter','Noto Sans SC',sans-serif; cursor:pointer; transition:transform .2s ease,background .2s ease,border-color .2s ease; }
.garden-ai-review:hover { transform:translateY(-2px); background:#edf3e9; border-color:#b8cdb3; }
.garden-page-heading p.garden-whisper { margin:11px 0 0 5px; padding-bottom:.08em; color:#a5bb9f; font:italic 500 clamp(20px,2vw,24px)/1.15 Georgia,'Noto Serif SC',serif; letter-spacing:-.065em; text-transform:lowercase; transform:rotate(-7deg); }
.stat-card {
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 3px;
  padding: 12px 14px; border-radius: 16px;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(18px);
  border: 1px solid #e7eadf;
  box-shadow: 0 9px 26px rgba(57, 76, 52, 0.055);
}
.stat-num { font: 700 22px/1.2 'Inter', 'Noto Sans SC', sans-serif !important; color: #557b59; font-variant-numeric: tabular-nums; }
.stat-label { font-size: 11px; color: #7a897b; letter-spacing: .035em; }

/* ===== 心情分布（详细） ===== */
.stat-dist { align-items: stretch; gap: 8px; }
.dist-head { display: flex; align-items: baseline; justify-content: space-between; }
.dist-total { font-size: 10px; color: #9aa596; font-variant-numeric: tabular-nums; }

.dist-stack {
  display: flex;
  height: 8px;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(148, 163, 184, 0.15);
}
.stack-seg { transition: flex 0.4s ease; min-width: 0; }
.stack-seg.positive { background: linear-gradient(90deg, #d8b968, #c89b49); }
.stack-seg.neutral  { background: linear-gradient(90deg, #93b292, #6f9672); }
.stack-seg.negative { background: linear-gradient(90deg, #b7bdaf, #8d9b8d); }

.dist-legend { display: flex; gap: 10px; font-size: 10.5px; color: #6e7d71; }
.lg-item { display: inline-flex; align-items: center; gap: 4px; }
.lg-dot { width: 7px; height: 7px; border-radius: 50%; }
.lg-item.positive .lg-dot { background: #c99f4f; }
.lg-item.neutral  .lg-dot { background: #7e9e7d; }
.lg-item.negative .lg-dot { background: #929d90; }

.dist-columns {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 6px;
  margin-top: 2px;
}
.dist-col {
  padding: 6px 8px;
  border-radius: 12px;
  background: rgba(250, 250, 245, 0.72);
  border: 1px solid rgba(230, 234, 222, 0.9);
}
.col-head {
  display: flex;
  align-items: center;
  gap: 3px;
  margin-bottom: 6px;
  font-size: 10.5px;
  color: #475569;
}
.col-icon { color: #829780; }
.col-name  { font-weight: 600; }
.col-count {
  margin-left: auto;
  font-size: 9px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
}
.bubbles { display: flex; flex-wrap: wrap; gap: 3px; min-height: 28px; align-items: center; }
.bubble {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px 5px;
  min-height: var(--size, 25px);
  border-radius: 999px;
  background: color-mix(in srgb, var(--bg) 88%, white);
  border: 1px solid color-mix(in srgb, var(--c) 30%, transparent);
  font-size: 9.5px;
  color: var(--c);
  font-weight: 600;
}
.bubble-icon { flex: none; }
.bubble-empty { font-size: 11px; color: #adb6aa; }

/* ===== 花园场景 ===== */
.garden-scene {
  position: relative; z-index: 2; flex: 1; min-height: 0;
  margin: 14px auto 0; width: calc(100% - 48px); max-width: 1100px;
  border-radius: 28px; overflow: hidden;
  isolation: isolate;
  background:
    radial-gradient(ellipse at 78% 16%, rgba(255, 249, 218, .64), transparent 26%),
    linear-gradient(180deg, #f1f0e5 0%, #e9eee2 43%, #e1ebdc 74%, #d8e5d1 100%);
  border: 1px solid rgba(255, 255, 255, 0.82);
  box-shadow: 0 22px 62px rgba(75, 103, 75, 0.11), inset 0 1px 0 rgba(255,255,255,.72);
}
.garden-scene::before {
  content: ''; position: absolute; inset: 0; z-index: 0; pointer-events: none;
  background:
    radial-gradient(ellipse at 15% 29%, rgba(255,255,255,.52), transparent 29%),
    radial-gradient(ellipse at 57% 4%, rgba(255,255,255,.34), transparent 34%);
}
.sky-sun {
  position: absolute; top: 28px; right: 56px;
  width: 58px; height: 58px; border-radius: 50%;
  background: radial-gradient(circle at 38% 35%, #fff9df, #edcf8e 76%);
  box-shadow: 0 0 48px rgba(224, 191, 116, 0.28);
  animation: sunFloat 7s ease-in-out infinite;
  z-index: 1;
}
@keyframes sunFloat { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(-6px); } }
.sky-cloud { position: absolute; height: 22px; border-radius: 999px; background: rgba(255, 255, 255, 0.54); filter: blur(.2px); z-index: 1; animation: cloud-drift 18s ease-in-out infinite alternate; }
.sky-cloud::before, .sky-cloud::after { content: ''; position: absolute; bottom: 0; border-radius: 50%; background: inherit; }
.sky-cloud::before { width: 34px; height: 34px; left: 18px; }
.sky-cloud::after { width: 25px; height: 25px; right: 15px; }
.cloud-1 { width: 112px; top: 51px; left: 11%; }
.cloud-2 { width: 86px; top: 104px; left: 54%; opacity: 0.53; animation-delay: -8s; }
@keyframes cloud-drift { from { translate: -5px 0; } to { translate: 12px 2px; } }
.ground-layer { position: absolute; left: 0; right: 0; z-index: 2; }
.ground-back { bottom: 0; height: 32%; background: linear-gradient(180deg, #c4d4b6 0%, #a8bf99 53%, #92ad83 100%); border-radius: 55% 55% 0 0 / 12% 12% 0 0; }
.ground-back::before, .ground-back::after { content: ''; position: absolute; bottom: 22%; width: 140px; height: 24px; border-top: 1px solid rgba(246,246,223,.35); border-radius: 50%; }
.ground-back::before { left: 14%; transform: rotate(-7deg); }
.ground-back::after { right: 12%; transform: rotate(6deg); }
.ground-front { bottom: 0; height: 17%; background: linear-gradient(180deg, rgba(219, 230, 200, 0) 0%, rgba(109, 140, 93, .54) 100%); border-radius: 60% 60% 0 0 / 16% 16% 0 0; }
.garden-loading { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; color: #648a68; z-index: 3; }
.garden-loading-icon { animation: garden-spin 1.1s linear infinite; }
@keyframes garden-spin { to { transform: rotate(360deg); } }
.garden-empty { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; padding: 24px; text-align: center; font: 500 14px/1.7 'Inter', 'Noto Sans SC', sans-serif; color: #596f56; letter-spacing: .035em; z-index: 3; pointer-events: none; }
.garden-empty small { color: #7e9279; font: 400 12px/1.5 'Noto Sans SC', sans-serif; letter-spacing: .04em; }

/* 无记录时的装饰花簇：独立于真实情绪数据与统计。 */
.empty-blooms { position: absolute; inset: 0; z-index: 2; pointer-events: none; }
.empty-bloom { position: absolute; left: var(--flower-x); bottom: 10%; width: 54px; height: calc(var(--stem-height) + var(--flower-size)); transform-origin: 50% 100%; animation: garden-sway 5.5s ease-in-out var(--bloom-delay) infinite; }
.empty-stem { position: absolute; bottom: 0; left: 50%; width: 2px; height: var(--stem-height); border-radius: 5px; background: linear-gradient(180deg, #8da77d, #668660); transform: translateX(-50%); transform-origin: bottom center; animation: stem-rise .8s cubic-bezier(.2,.7,.2,1) both; }
.empty-leaf { position: absolute; bottom: calc(var(--stem-height) * .43); left: 50%; width: 18px; height: 9px; border-radius: 100% 0 100% 0; background: #8ca982; opacity: .84; }
.empty-leaf.leaf-left { transform: translateX(-95%) rotate(12deg); }
.empty-leaf.leaf-right { bottom: calc(var(--stem-height) * .68); transform: translateX(-1px) rotate(83deg) scale(.78); background: #a9b998; }
.empty-flower-head { position: absolute; left: 50%; bottom: calc(var(--stem-height) - 11px); width: var(--flower-size); height: var(--flower-size); transform: translateX(-50%); }
.empty-petal { position: absolute; top: 50%; left: 50%; width: 42%; height: 54%; border-radius: 80% 80% 58% 58%; background: radial-gradient(circle at 35% 72%, rgba(255,255,255,.9), var(--flower-color) 78%); transform: translate(-50%, -50%) rotate(var(--petal-angle)) translateY(-35%); scale: .35; opacity: 0; animation: petal-unfurl .52s ease-out var(--petal-delay) both; }
.empty-core { position: absolute; top: 50%; left: 50%; width: 22%; height: 22%; border-radius: 50%; background: radial-gradient(circle at 35% 30%, #fff4c6, #d4a95c); transform: translate(-50%,-50%); box-shadow: 0 0 8px rgba(206,173,100,.22); }
.empty-shape-star .empty-petal { height: 64%; border-radius: 50% 50% 65% 65%; }
.empty-shape-soft .empty-petal { width: 48%; height: 48%; border-radius: 58% 78% 58% 78%; }
@keyframes garden-sway { 0%, 100% { rotate: -2deg; } 50% { rotate: 2deg; } }
@keyframes stem-rise { from { scale: 1 .08; } to { scale: 1 1; } }
@keyframes petal-unfurl { from { scale: .32; opacity: 0; } to { scale: 1; opacity: 1; } }

/* ===== 花朵网格 ===== */
.flower-grid {
  position: absolute; bottom: 6%; left: 0; right: 0;
  display: flex; flex-wrap: wrap; align-content: flex-end; justify-content: center;
  gap: 0; padding: 0 4%; z-index: 3;
}
.flower-cell {
  position: relative; flex: 0 0 16.6667%; height: 142px;
  display: flex; flex-direction: column; align-items: center; justify-content: flex-end;
}
.flower-cell.clickable:not(.empty) { cursor: pointer; }
.flower-cell.clickable:not(.empty) { border-radius: 20px; transition: transform .25s ease; }
.flower-cell.clickable:not(.empty):hover { transform: translateY(-4px); }

.dirt { width: 44px; height: 14px; border-radius: 50%; background: radial-gradient(ellipse at center, #a99172, #8f8064); margin-top: -7px; position: relative; z-index: 1; box-shadow: 0 3px 7px rgba(82, 91, 57, .13); }

.stem { width: 4px; height: 0; border-radius: 4px; background: linear-gradient(180deg, #a9bd91, #64845e); position: relative; z-index: 0; transform-origin: bottom center; animation: stem-rise .75s cubic-bezier(.2,.7,.2,1) both; transition: height 0.8s ease; }
.stem.seed  { height: 46px; }
.stem.bud   { height: 66px; }
.stem.bloom { height: 84px; }

.flower-head { position: relative; width: 48px; height: 48px; margin-bottom: -9px; z-index: 2; transform-origin: bottom center; rotate: -1deg; animation: garden-sway 5s ease-in-out infinite; transition: all 0.5s ease; }
.flower-head.seed  { transform: scale(.78); opacity: .86; }
.flower-head.bud   { transform: scale(.94); opacity: .96; }
.flower-head.bloom { transform: scale(1.06); opacity: 1; }

.petal { position: absolute; top: 50%; left: 50%; width: 43%; height: 57%; border-radius: 78% 78% 58% 58%; background: radial-gradient(circle at 35% 70%, #ffffff, var(--petal) 80%); transform: translate(-50%, -50%) rotate(var(--petal-angle)) translateY(-34%); scale: .32; opacity: 0; animation: petal-unfurl .48s ease-out var(--petal-delay) both; filter: drop-shadow(0 1px 1px rgba(73, 84, 57, .12)); }
.flower-shape-1 .petal { height: 62%; border-radius: 52% 52% 68% 68%; }
.flower-shape-2 .petal { width: 47%; height: 47%; border-radius: 55% 78% 55% 78%; }
.core { position: absolute; top: 50%; left: 50%; width: 12px; height: 12px; border: 2px solid rgba(255, 255, 255, .68); border-radius: 50%; background: radial-gradient(circle at 35% 35%, #fff2c1, #d9a74d); transform: translate(-50%, -50%); box-shadow: 0 1px 7px rgba(164, 119, 40, .28); }

.leaf { position: absolute; bottom: 44px; left: calc(50% + 2px); width: 14px; height: 7px; background: #88a178; border-radius: 100% 0 100% 0; transform: rotate(-15deg); transform-origin: 0 100%; transition: all 0.5s ease; }
.leaf.seed  { bottom: 24px; width: 12px; height: 6px; }
.leaf.bud   { bottom: 35px; width: 16px; height: 8px; }
.leaf.bloom { bottom: 49px; width: 20px; height: 10px; }
.leaf-secondary { left: auto; right: calc(50% + 2px); bottom: 56px; transform: rotate(105deg); background: #a5b794; }
.leaf-secondary.seed { bottom: 35px; width: 10px; height: 5px; }
.leaf-secondary.bud { bottom: 49px; width: 13px; height: 7px; }
.leaf-secondary.bloom { bottom: 62px; width: 16px; height: 8px; }

.flower-tag {
  position: absolute; bottom: -18px; left: 50%; transform: translateX(-50%);
  display: flex; align-items: center; gap: 3px;
  padding: 2px 7px; border-radius: 999px;
  background: rgba(255, 255, 255, 0.7); backdrop-filter: blur(6px);
  border: 1px solid rgba(255, 255, 255, 0.85);
  font-size: 9px; white-space: nowrap;
  opacity: 0; transition: opacity 0.2s; pointer-events: none;
}
.flower-cell:hover .flower-tag { opacity: 1; }
.tag-mood-icon { color: #789278; flex: none; }
.tag-date  { color: #647568; }
.tag-score { color: #a9833d; font-weight: 700; }

/* ===== 底部 CTA ===== */
.garden-plant { position: relative; z-index: 5; padding: 14px 24px 20px; }
.plant-bar {
  max-width: 960px; margin: 0 auto;
  display: flex; align-items: center; justify-content: space-between; gap: 14px;
  padding: 12px 18px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.88); backdrop-filter: blur(22px);
  border: 1px solid #e7eadf;
  box-shadow: 0 12px 32px rgba(57, 76, 52, 0.07);
}
.plant-bar-info { display: flex; flex-direction: column; gap: 2px; }
.plant-bar-title { font-size: 13px; font-weight: 650; color: #354b39; }
.plant-bar-sub   { font-size: 11px; color: #879587; }
.plant-complete-copy { display: inline-flex; align-items: center; gap: 4px; color: #678669; }

.plant-cta {
  display: inline-flex; align-items: center; gap: 7px;
  height: 38px; padding: 0 18px;
  border-radius: 12px; border: none;
  background: #426b4b;
  color: #ffffff; font-size: 13px; font-weight: 700; letter-spacing: 1px;
  cursor: pointer;
  box-shadow: 0 7px 18px rgba(66, 107, 75, 0.19);
  transition: all 0.2s;
}
.plant-cta:hover:not(:disabled) { transform: translateY(-2px); background: #31563b; box-shadow: 0 10px 22px rgba(66, 107, 75, 0.25); }
.plant-cta:disabled { background: #b7c4b4; color: rgba(255,255,255,0.94); cursor: not-allowed; box-shadow: none; }

/* ===== 弹窗 ===== */
.flower-dialog :deep(.el-dialog) {
  display: flex;
  flex-direction: column;
  max-height: calc(100dvh - 32px);
  border-radius: 24px !important;
  overflow: hidden;
  background: #fbfaf6;
  border: 1px solid #e4e9dc;
  box-shadow: 0 26px 76px rgba(42, 62, 43, 0.2);
}
.flower-dialog :deep(.el-dialog__header) { flex: none; padding: 22px 24px 12px; }
.flower-dialog :deep(.el-dialog__body) { min-height: 0; overflow-y: auto; padding: 8px 24px 12px; }
.flower-dialog :deep(.el-dialog__footer) { flex: none; padding: 12px 24px 22px; }
.dialog-head,
.form-dialog-head { display: flex; align-items: center; gap: 13px; }
.form-dialog-head { align-items: flex-start; }
.dialog-icon-mark {
  display: grid; place-items: center; flex: none;
  width: 44px; height: 44px; border-radius: 15px;
  color: var(--mood-color, #567c5c);
  background: color-mix(in srgb, var(--mood-color, #567c5c) 11%, white);
  border: 1px solid color-mix(in srgb, var(--mood-color, #567c5c) 16%, white);
}
.form-icon-mark { color: #587b5b; background: #eaf1e6; border-color: #dce7d7; }
.dialog-heading-copy { min-width: 0; flex: 1; }
.dialog-kicker { display: block; margin-bottom: 4px; color: #82927f; font-size: 10px; font-weight: 650; letter-spacing: .12em; }
.dialog-title { margin: 0; color: #2e4534; font-size: 17px; line-height: 1.45; font-weight: 650; letter-spacing: .01em; }
.dialog-sub { margin: 3px 0 0; color: #879487; font-size: 11px; line-height: 1.5; }
.dialog-close {
  display: grid; place-items: center; flex: none;
  width: 34px; height: 34px; margin-left: auto; padding: 0;
  color: #758575; background: #f0f2ec; border: 1px solid #e6e9df; border-radius: 50%;
  cursor: pointer; transition: background .2s ease, color .2s ease, transform .2s ease;
}
.dialog-close:hover { color: #365d3c; background: #e7efe3; transform: rotate(5deg); }

.dialog-body { padding-top: 4px; font-family: 'Inter', 'Noto Sans SC', sans-serif; }
.dialog-content {
  min-height: 74px; padding: 15px 16px; border-radius: 16px;
  background: linear-gradient(145deg, #f3f5ec, #f8f7f0);
  border: 1px solid #e9ecdf;
  font-size: 13px; line-height: 1.85; color: #485b4b;
  white-space: pre-wrap; word-break: break-word;
}
.dialog-stars { margin-top: 14px; padding: 12px 14px; border-radius: 16px; background: #f5f6ef; border: 1px solid #e9ecdf; display: flex; flex-direction: column; gap: 9px; }
.dialog-trigger {
  margin-top: 11px; padding: 0 2px;
  display: flex; align-items: center; gap: 8px;
  font-size: 12px; color: #67776a;
}
.dialog-trigger .dt-label { width: 60px; flex-shrink: 0; color: #89968a; }
.dialog-trigger .dt-tag {
  padding: 4px 10px; border-radius: 999px;
  background: #e8efe4;
  color: #547359; font-weight: 550;
}
.legacy-rating-note {
  margin: 12px 0 0; padding: 10px 12px; border: 1px solid #eadfc9; border-radius: 12px;
  background: #faf6ec; color: #826f4c; font-size: 11px; line-height: 1.65;
}
.form-legacy-rating-note { margin: 0 0 13px; }
.garden-insight {
  margin: 2px 0 16px; padding: 15px 16px; border: 1px solid #dfe8da;
  border-radius: 17px; background: linear-gradient(135deg, #eef4e9, #f8f7f0 72%);
  color: #435d49;
}
.garden-insight-head { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.garden-insight-title { display: inline-flex; align-items: center; gap: 7px; font-size: 13px; font-weight: 650; }
.garden-insight-title svg { color: #678f66; }
.garden-insight-action {
  display: inline-flex; align-items: center; justify-content: center; gap: 5px;
  min-height: 32px; padding: 5px 11px; border: 1px solid #bfd3bd; border-radius: 999px;
  color: #376245; background: #fff; font-size: 11px; font-weight: 600; cursor: pointer;
}
.garden-insight-action:hover { background: #e7f0e4; }
.garden-insight-action:disabled { cursor: wait; opacity: .65; }
.insight-spinner { animation: insight-spin 1.2s linear infinite; }
@keyframes insight-spin { to { transform: rotate(360deg); } }
.insight-help, .insight-unavailable { margin: 10px 0 0; font-size: 11px; line-height: 1.7; color: #708274; }
.garden-insight-result { margin-top: 12px; font-size: 12px; line-height: 1.75; }
.garden-insight-result p { margin: 0 0 9px; }
.garden-insight-result p:last-child { margin-bottom: 0; }
.insight-evidence { color: #6b806e; font-size: 11px; }
.insight-scores { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 7px; margin: 10px 0; }
.insight-score { min-width: 0; padding: 9px; border: 1px solid #e0e9dc; border-radius: 11px; background: rgba(255,255,255,.8); }
.insight-score-label { display: block; color: #718474; font-size: 10px; }
.insight-score strong { display: block; margin-top: 2px; font-size: 14px; color: #355940; }
.insight-score small { display: block; margin-top: 5px; color: #728574; font-size: 10px; line-height: 1.5; overflow-wrap: anywhere; }
.insight-question { padding-top: 9px; border-top: 1px solid #dfe8da; font-weight: 550; }
@media (max-width: 520px) { .insight-scores { grid-template-columns: 1fr; } }
@media (prefers-reduced-motion: reduce) { .insight-spinner { animation: none; } }
.dialog-star-row { display: flex; align-items: center; gap: 10px; font-size: 12px; color: #647367; }
.ds-label { width: 68px; flex-shrink: 0; }
.dialog-star-row :deep(.el-rate) { flex: 1; min-width: 0; }
.ds-val { font-size: 11px; color: #89968a; font-variant-numeric: tabular-nums; }
.flower-dialog :deep(.el-rate__icon) { margin-right: 2px; }
.flower-dialog :deep(.el-rate__icon.is-active) { color: #c69f54 !important; }

.dialog-foot { display: flex; justify-content: flex-end; gap: 9px; }
.dialog-btn {
  display: inline-flex; align-items: center; gap: 5px;
  min-height: 40px; padding: 8px 16px; border-radius: 13px;
  font: 600 12px/1.2 'Inter', 'Noto Sans SC', sans-serif; cursor: pointer;
  transition: transform .2s ease, background .2s ease, border-color .2s ease;
}
.dialog-btn.primary {
  border: 1px solid #426b4b; background: #426b4b; color: #fff;
  box-shadow: 0 6px 16px rgba(66, 107, 75, 0.19);
}
.dialog-btn.primary:hover { transform: translateY(-1px); background: #31563b; }
.dialog-btn.primary:disabled { opacity: 0.5; cursor: not-allowed; transform: none; }
.dialog-btn.ghost { border: 1px solid #e1e7dc; background: #fff; color: #738274; }
.dialog-btn.ghost:hover { color: #a8685c; border-color: #dfc3b8; background: #fcf6f2; }

/* ===== 表单 ===== */
.garden-form { font-family: 'Inter', 'Noto Sans SC', sans-serif; }
.garden-form :deep(.el-form-item) { margin-bottom: 16px; }
.garden-form :deep(.el-form-item__label) { margin-bottom: 7px; padding: 0; color: #526657; font-size: 12px; line-height: 1.5; font-weight: 600; }
.garden-form :deep(.el-form-item__error) { padding-top: 4px; color: #b56f61; font-size: 11px; }
.form-chips { display: grid; width: 100%; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 7px; }
.form-chip {
  display: flex; align-items: center; justify-content: center; gap: 5px;
  min-width: 0; min-height: 38px; padding: 6px 7px; border-radius: 12px;
  border: 1px solid #e6e9df;
  background: #fff;
  font: 500 11px/1.2 'Inter', 'Noto Sans SC', sans-serif; color: #68766b; cursor: pointer;
  transition: transform .18s ease, background .18s ease, border-color .18s ease, color .18s ease;
}
.form-chip:hover { border-color: color-mix(in srgb, var(--chip-color) 55%, white); color: var(--chip-color); transform: translateY(-1px); }
.form-chip.active {
  border-color: color-mix(in srgb, var(--chip-color) 65%, white); color: var(--chip-color);
  background: color-mix(in srgb, var(--chip-color) 12%, white);
  box-shadow: 0 4px 11px color-mix(in srgb, var(--chip-color) 13%, transparent);
}
.mood-chip-icon { flex: none; }
.mood-chip-label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.garden-form :deep(.el-textarea__inner),
.garden-form :deep(.el-input__wrapper),
.garden-form :deep(.el-select__wrapper) {
  border-radius: 13px !important; border: 1px solid #e3e8dd !important;
  background: #fff !important; box-shadow: 0 1px 2px rgba(48, 67, 49, .025) !important;
  color: #405644; font: 400 12px/1.7 'Inter', 'Noto Sans SC', sans-serif;
}
.garden-form :deep(.el-textarea__inner) { min-height: 88px !important; padding: 11px 13px; resize: vertical; }
.garden-form :deep(.el-input__wrapper),
.garden-form :deep(.el-select__wrapper) { min-height: 42px; padding: 2px 13px; }
.garden-form :deep(.el-textarea__inner::placeholder),
.garden-form :deep(.el-input__inner::placeholder) { color: #a0aa9e; }
.garden-form :deep(.el-textarea__inner:focus),
.garden-form :deep(.el-input__wrapper.is-focus),
.garden-form :deep(.el-select__wrapper.is-focused) { border-color: #91ad8f !important; box-shadow: 0 0 0 3px rgba(112, 151, 111, .11) !important; }
.garden-form :deep(.el-input__count) { color: #9ba698; background: transparent; font-size: 10px; }

.form-rates {
  margin-top: 1px; padding: 10px 14px; border-radius: 15px;
  background: #f4f6ef; border: 1px solid #e6eade;
  display: flex; flex-direction: column; gap: 0;
}
.rating-scale { margin: 1px 0 8px; color: #6d7f70; font-size: 10px; line-height: 1.6; }
.rate-form-item { margin-bottom: 0; }
.rate-form-item :deep(.el-form-item__content) { display: block; line-height: normal; }
.rate-form-item :deep(.el-form-item__error) { position: static; padding-top: 1px; color: #a36f5c; font-size: 9px; }
.rate-form-item:last-of-type .rate-block { border-bottom: 0; }
.rating-note { margin: 7px 0 0; color: #899789; font-size: 9px; line-height: 1.5; }
.rate-block { display: flex; align-items: center; gap: 8px; min-height: 42px; border-bottom: 1px solid #e6eade; }
.rate-block:last-child { border-bottom: 0; }
.rate-icon { display: grid; place-items: center; width: 25px; height: 25px; flex: none; border-radius: 9px; background: #fff; color: #8a9f7e; }
.sleep-rate-icon { color: #7e9b9a; }
.stress-rate-icon { color: #b78275; }
.rate-label { font-size: 11px; color: #647568; width: 132px; flex: none; }
.rate-block :deep(.el-rate) { flex: 1; min-width: 0; }
.rate-block :deep(.el-rate__icon) { margin-right: 2px; font-size: 17px; }
.rate-block :deep(.el-rate__icon.is-active) { color: #c69f54 !important; }
.rate-hint { font-size: 9px; color: #94a092; white-space: nowrap; }

/* ===== 响应式 ===== */
@media (max-width: 960px) {
  .garden-page-heading { margin-top:92px; }
  .garden-stats { grid-template-columns: 1fr 1fr; width: calc(100% - 28px); }
  .stat-dist { grid-column: 1 / -1; }
  .garden-scene { width: calc(100% - 28px); }
  .flower-grid { padding: 0 3%; }
  .flower-cell { flex-basis: 25%; }
  .nav-progress { display: none; }
  .nav-sub { display: none; }
}
@media (max-width: 640px) {
  .flower-grid { padding: 0 3%; }
  .flower-cell { flex-basis: 33.3333%; }
  .garden-nav { padding: 12px 14px; }
}
/* iPhone 窄屏：压缩导航与种植面板，UI 不变仅防错乱 */
@media (max-width: 520px) {
  .garden {
    height: auto;
    min-height: 100dvh;
    overflow-y: auto;
  }

  .garden-page-heading { display:flex; flex-direction:column; align-items:stretch; width:calc(100% - 28px); margin-top:82px; }
  .garden-heading-actions { flex-direction:row; align-items:center; justify-content:space-between; gap:10px; margin-top:9px; }
  .garden-heading-actions p { margin:0; }
  .garden-ai-review { min-height:35px; padding:0 10px; white-space:nowrap; font-size:10px; }
  .garden-page-heading p { margin-top:7px; }

  .garden-nav {
    padding: 12px 14px;
    gap: 8px;
  }

  .garden-logo {
    width: 36px;
    height: 36px;
    border-radius: 12px;
    font-size: 17px;
  }

  .nav-name {
    font-size: 13px;
  }

  .nav-btn {
    padding: 7px 12px;
    font-size: 12px;
  }

  .garden-stats {
    width: calc(100% - 24px);
    grid-template-columns: 1fr 1fr;
    gap: 10px;
    margin-top: 12px;
  }

  .stat-dist {
    grid-column: 1 / -1;
  }

  .garden-scene {
    flex:none;
    height:max(340px, calc(var(--garden-rows, 1) * 150px + 116px));
    width: calc(100% - 24px);
    margin-top: 12px;
    border-radius: 22px;
  }

  .flower-grid { bottom: 7%; gap: 0; padding: 0 2%; }
  .flower-cell { height: 148px; }
  .empty-bloom:nth-child(1) { left: 4% !important; }
  .empty-bloom:nth-child(2) { left: 15% !important; }
  .empty-bloom:nth-child(3) { left: 27% !important; }
  .empty-bloom:nth-child(4) { left: 66% !important; }
  .empty-bloom:nth-child(5) { left: 79% !important; }
  .empty-bloom:nth-child(6) { left: 91% !important; }
  .garden-empty { font-size: 14px; letter-spacing: .04em; }

  .sky-sun {
    width: 52px;
    height: 52px;
    right: 28px;
    top: 22px;
  }

  .garden-plant {
    padding: 10px 12px 14px;
  }

  .plant-panel {
    padding: 12px 14px;
    border-radius: 18px;
  }

  .plant-chip {
    padding: 7px 12px;
    font-size: 12px;
  }

  .flower-dialog :deep(.el-dialog) {
    width: calc(100vw - 24px) !important;
    max-width: calc(100vw - 24px);
    max-height: calc(100dvh - 24px);
    border-radius: 20px;
  }

  .flower-dialog :deep(.el-dialog__header) { padding: 18px 18px 10px; }
  .flower-dialog :deep(.el-dialog__body) { padding: 8px 18px 10px; }
  .flower-dialog :deep(.el-dialog__footer) { padding: 10px 18px 18px; }
  .dialog-icon-mark { width: 39px; height: 39px; border-radius: 13px; }
  .dialog-title { font-size: 15px; }
  .dialog-sub { font-size: 10.5px; }
  .form-chips { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 6px; }
  .form-chip { min-height: 38px; }
  .rate-block { flex-wrap: wrap; gap: 7px; padding: 7px 0; }
  .rate-label { width: 106px; }
  .rate-block :deep(.el-rate) { flex: 1 0 96px; }
  .rate-hint { margin-left: 32px; }
  .dialog-star-row { gap: 7px; }
  .dialog-star-row .ds-label { width: 62px; }
  .dialog-foot { gap: 7px; }
  .dialog-btn { min-height: 38px; padding: 8px 13px; }
}

@media (prefers-reduced-motion: reduce) {
  .garden-scene *,
  .garden-scene *::before,
  .garden-scene *::after,
  .garden-page-heading,
  .stat-card,
  .plant-bar {
    animation-duration: .01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: .01ms !important;
    scroll-behavior: auto !important;
  }
}
</style>
