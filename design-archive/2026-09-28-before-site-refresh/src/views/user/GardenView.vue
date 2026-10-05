<script setup>
import { ref, computed, onMounted, onUnmounted, reactive, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getGarden,
  plantFlower,
  updateFlower,
  deleteFlower
} from '@/api/emotion'
import { useEmotionStore } from '@/stores/emotion'
import AppNavBar from '@/components/AppNavBar.vue'
import UserDropdown from '@/components/UserDropdown.vue'

const router = useRouter()
const emotionStore = useEmotionStore()

const navActions = [
  { key: 'consult',  title: 'AI 咨询',  icon: 'ChatDotRound', path: '/consult' },
  { key: 'articles', title: '知识文章', icon: 'Collection',   path: '/home/articles' },
  { key: 'relax',    title: '白噪音空间', icon: 'WindPower',   path: '/relax' },
  { key: 'home',     title: '回到主页', icon: 'HomeFilled',   path: '/home' }
]

const flowers = ref([])
const loading = ref(true)
const planting = ref(false)
const todayPlanted = ref(false)
const currentTime = ref(new Date())

// 定时刷新时间
let timeTimer = 0
onMounted(() => { timeTimer = setInterval(() => currentTime.value = new Date(), 60000) })
onUnmounted(() => clearInterval(timeTimer))

// 18 种情绪（情绪内部用 emoji 保持直观，外部按钮用 Element/FlowerIcon）
const EMOTION_META = {
  // ===== 积极（5）=====
  开心:    { color: '#f59e0b', bg: '#fef3c7', icon: '😄', label: '开心', group: 'positive' },
  感恩:    { color: '#f472b6', bg: '#fce7f3', icon: '🙏', label: '感恩', group: 'positive' },
  期待:    { color: '#60a5fa', bg: '#dbeafe', icon: '🤩', label: '期待', group: 'positive' },
  欣慰:    { color: '#10b981', bg: '#d1fae5', icon: '😊', label: '欣慰', group: 'positive' },
  自豪:    { color: '#facc15', bg: '#fef9c3', icon: '🥳', label: '自豪', group: 'positive' },

  // ===== 平稳（3）=====
  平静:    { color: '#34d399', bg: '#d1fae5', icon: '😌', label: '平静', group: 'neutral' },
  放松:    { color: '#22d3ee', bg: '#cffafe', icon: '🧘', label: '放松', group: 'neutral' },
  无聊:    { color: '#9ca3af', bg: '#f3f4f6', icon: '😐', label: '无聊', group: 'neutral' },

  // ===== 消极（10）=====
  疲惫:    { color: '#94a3b8', bg: '#e2e8f0', icon: '😮‍💨', label: '疲惫', group: 'negative' },
  焦虑:    { color: '#fb923c', bg: '#fed7aa', icon: '😰', label: '焦虑', group: 'negative' },
  担心:    { color: '#c084fc', bg: '#e9d5ff', icon: '😟', label: '担心', group: 'negative' },
  孤独:    { color: '#6b7280', bg: '#e5e7eb', icon: '🥺', label: '孤独', group: 'negative' },
  生气:    { color: '#ef4444', bg: '#fecaca', icon: '😤', label: '生气', group: 'negative' },
  低落:    { color: '#818cf8', bg: '#e0e7ff', icon: '🌥️', label: '低落', group: 'negative' },
  困惑:    { color: '#a78bfa', bg: '#ede9fe', icon: '😕', label: '困惑', group: 'negative' },
  烦躁:    { color: '#fb7185', bg: '#ffe4e6', icon: '😖', label: '烦躁', group: 'negative' },
  委屈:    { color: '#fda4af', bg: '#fff1f2', icon: '🥹', label: '委屈', group: 'negative' },
  自卑:    { color: '#71717a', bg: '#f4f4f5', icon: '😔', label: '自卑', group: 'negative' }
}
const moodOptions = Object.keys(EMOTION_META)

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
  if (todayPlanted.value) return '今日已种 ✓'
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
  const avgEmotion = list.length ? (list.reduce((s, f) => s + (f.emotionScore || 3), 0) / list.length).toFixed(1) : '—'

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

const GRID_COLS = 6
const flowerSlots = computed(() => {
  const slots = []
  for (let row = 0; row < 4; row++) {
    for (let col = 0; col < GRID_COLS; col++) {
      slots.push({ row, col, id: row * GRID_COLS + col })
    }
  }
  return slots.map((slot, idx) => {
    const flower = flowers.value[idx] || null
    const meta = flower ? (EMOTION_META[flower.emotion] || EMOTION_META['平静']) : null
    const stage = flower ? flowerStage(flower) : 'empty'
    return { ...slot, flower, meta, stage, isEmpty: !flower }
  })
})

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
const formMode = ref('create') // 'create' | 'edit'
const submitting = ref(false)
const formRef = ref(null)

const form = reactive({
  flowerId: null,
  emotion: '开心',
  content: '',
  emotionScore: 3,
  sleepScore: 3,
  stressScore: 3,
  trigger: ''
})

const formRules = {
  emotion: [{ required: true, message: '请选择一种心情', trigger: 'change' }],
  content: [{ required: true, message: '请写一点今天的心情', trigger: 'blur' }]
}

// 智能推荐：根据所选情绪给一个默认评分
function autoEmotionScore(emotion) {
  const map = {
    开心: 5, 感恩: 5, 期待: 4, 放松: 4, 平静: 3, 自豪: 5, 欣慰: 5,
    低落: 2, 焦虑: 2, 担心: 2, 孤独: 2, 疲惫: 2, 生气: 1, 无聊: 3,
    困惑: 3, 烦躁: 1, 委屈: 2, 自卑: 1
  }
  return map[emotion] || 3
}

function openPlantForm() {
  if (todayPlanted.value) {
    ElMessage.info('今天已经种过花了，明天再来吧')
    return
  }
  formMode.value = 'create'
  const latest = emotionStore.latest
  form.flowerId = null
  form.emotion = latest?.emotion || '平静'
  form.content = ''
  form.emotionScore = latest?.emotionScore || 3
  form.sleepScore = latest?.sleepScore || 3
  form.stressScore = latest?.stressScore || 3
  form.trigger = latest?.trigger || ''
  formVisible.value = true
}

function openEditForm(flower) {
  formMode.value = 'edit'
  form.flowerId = flower.flowerId
  form.emotion = flower.emotion
  form.content = flower.content || ''
  form.emotionScore = flower.emotionScore || 3
  form.sleepScore = flower.sleepScore || 3
  form.stressScore = flower.stressScore || 3
  form.trigger = flower.trigger || ''
  formVisible.value = true
}

watch(() => form.emotion, (v) => {
  if (formMode.value === 'create') {
    form.emotionScore = autoEmotionScore(v)
  }
})

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
      ElMessage.success('修改成功 ✓')
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
      `确定删除「${flower.date}」的 ${EMOTION_META[flower.emotion]?.icon || ''} ${flower.emotion} 吗？`,
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

onMounted(loadGarden)
</script>

<template>
  <div class="garden">
    <!-- 顶部导航 -->
    <AppNavBar
      :actions="navActions"
      :current-path="'/garden'"
    >
      <template #brand>
        <router-link to="/garden" class="garden-brand">
          <span class="garden-name">情绪花园</span>
          <span class="garden-sub">每一种心情，都值得被记录</span>
        </router-link>
      </template>
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

    <!-- 统计 -->
    <section class="garden-stats">
      <div class="stat-card">
        <span class="stat-num">{{ stats.total }}</span>
        <span class="stat-label">花朵总数</span>
      </div>
      <div class="stat-card">
        <span class="stat-num">{{ stats.avgEmotion }}</span>
        <span class="stat-label">平均情绪</span>
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
          <div v-for="group in ['positive', 'neutral', 'negative']" :key="group" class="dist-col">
            <div class="col-head">
              <span class="col-emoji">{{ group === 'positive' ? '🌟' : group === 'neutral' ? '🍃' : '🌧️' }}</span>
              <span class="col-name">{{ group === 'positive' ? '积极' : group === 'neutral' ? '平稳' : '消极' }}</span>
              <span class="col-count">{{ stats.groups[group] }}</span>
            </div>
            <div class="bubbles">
              <span
                v-for="e in moodOptions.filter(k => EMOTION_META[k].group === group && stats.dist[k])"
                :key="e"
                class="bubble"
                :style="{ '--bg': EMOTION_META[e].bg, '--c': EMOTION_META[e].color, '--size': 24 + Math.min(20, (stats.dist[e] / Math.max(1, stats.groups[group])) * 50) + 'px' }"
              >
                <span class="bubble-icon">{{ EMOTION_META[e].icon }}</span>
                <span class="bubble-count">{{ stats.dist[e] }}</span>
              </span>
              <span v-if="!moodOptions.some(k => EMOTION_META[k].group === group && stats.dist[k])" class="bubble-empty">—</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 花园场景 -->
    <main class="garden-scene">
      <div class="sky-sun"></div>
      <div class="sky-cloud cloud-1"></div>
      <div class="sky-cloud cloud-2"></div>
      <div class="ground-layer ground-back"></div>
      <div class="ground-layer ground-front"></div>

      <div v-if="loading" class="garden-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
      </div>
      <p v-else-if="!flowers.length" class="garden-empty">
        土地还空着，种下今天的心情吧
      </p>

      <div v-else class="flower-grid">
        <div
          v-for="slot in flowerSlots"
          :key="slot.id"
          class="flower-cell"
          :class="{ empty: slot.isEmpty, clickable: !slot.isEmpty }"
          @click="!slot.isEmpty && openDetail(slot.flower)"
        >
          <template v-if="!slot.isEmpty">
            <div class="dirt"></div>
            <div class="stem" :class="slot.stage"></div>
            <div class="flower-head" :class="slot.stage" :style="{ '--petal': slot.meta?.color }">
              <div class="petal p1"></div>
              <div class="petal p2"></div>
              <div class="petal p3"></div>
              <div class="petal p4"></div>
              <div class="petal p5"></div>
              <div class="core"></div>
            </div>
            <div class="leaf" :class="slot.stage"></div>
            <!-- 标签：显示日期 + 情绪小图标 -->
            <div class="flower-tag">
              <span class="tag-emoji">{{ slot.meta?.icon }}</span>
              <span class="tag-date">{{ dateLabel(slot.flower.date) }}</span>
              <span class="tag-score" v-if="slot.flower.emotionScore">{{ slot.flower.emotionScore }}</span>
            </div>
          </template>
          <template v-else>
            <div class="empty-soil"></div>
            <div class="empty-mark">+</div>
          </template>
        </div>
      </div>
    </main>

    <!-- 底部：种花按钮 -->
    <footer class="garden-plant">
      <div class="plant-bar">
        <div class="plant-bar-info">
          <span class="plant-bar-title">今日种花</span>
          <span class="plant-bar-sub">
            <template v-if="todayPlanted">✓ 今天已经种下啦，明天继续 ✨</template>
            <template v-else-if="emotionStore.latest">最近 AI 分析已就绪，可一键带入</template>
            <template v-else>每种心情都会长成一朵独特的花</template>
          </span>
        </div>
        <button
          class="plant-cta"
          :disabled="todayPlanted"
          @click="openPlantForm"
        >
          <el-icon><Cherry /></el-icon>
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
          <el-icon :size="32"><Cherry /></el-icon>
          <div>
            <div class="dialog-title">{{ detailFlower?.emotion }} · {{ detailFlower?.date }}</div>
            <div class="dialog-sub">{{ dateLabel(detailFlower?.date) }}种下的花</div>
          </div>
        </div>
      </template>
      <div v-if="detailFlower" class="dialog-body">
        <div class="dialog-content">{{ detailFlower.content || '（未填写日记内容）' }}</div>
        <div v-if="detailFlower.trigger" class="dialog-trigger">
          <span class="dt-label">触发因素</span>
          <span class="dt-tag">{{ detailFlower.trigger }}</span>
        </div>
        <div class="dialog-stars">
          <div class="dialog-star-row">
            <span class="ds-label">情绪评分</span>
            <el-rate :model-value="detailFlower.emotionScore || 0" disabled :colors="['#fbbf24','#fbbf24','#fbbf24']" />
            <span class="ds-val">{{ detailFlower.emotionScore || 0 }}/5</span>
          </div>
          <div class="dialog-star-row">
            <span class="ds-label">睡眠质量</span>
            <el-rate :model-value="detailFlower.sleepScore || 0" disabled :colors="['#22d3ee','#22d3ee','#22d3ee']" />
            <span class="ds-val">{{ detailFlower.sleepScore || 0 }}/5</span>
          </div>
          <div class="dialog-star-row">
            <span class="ds-label">压力水平</span>
            <el-rate :model-value="detailFlower.stressScore || 0" disabled :colors="['#ef4444','#ef4444','#ef4444']" />
            <span class="ds-val">{{ detailFlower.stressScore || 0 }}/5</span>
          </div>
        </div>
      </div>
      <template #footer>
        <div class="dialog-foot">
          <button class="dialog-btn ghost" @click="removeFlower(detailFlower)">
            <el-icon><Delete /></el-icon>
            <span>删除</span>
          </button>
          <button class="dialog-btn primary" @click="editFromDetail">
            <el-icon><Edit /></el-icon>
            <span>编辑</span>
          </button>
        </div>
      </template>
    </el-dialog>

    <!-- 种花/编辑弹窗 -->
    <el-dialog
      v-model="formVisible"
      :title="formMode === 'create' ? '种下今日心情' : '编辑这朵花'"
      width="460px"
      :show-close="false"
      class="flower-dialog"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-position="top">
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
              <span>{{ EMOTION_META[mood]?.icon }}</span>
              <span>{{ EMOTION_META[mood]?.label }}</span>
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

        <div class="form-rates">
          <div class="rate-block">
            <span class="rate-label">情绪评分</span>
            <el-rate v-model="form.emotionScore" :colors="['#fbbf24','#fbbf24','#fbbf24']" />
          </div>
          <div class="rate-block">
            <span class="rate-label">睡眠质量（AI 自动）</span>
            <el-rate v-model="form.sleepScore" :colors="['#22d3ee','#22d3ee','#22d3ee']" />
            <span class="rate-hint" v-if="emotionStore.latest">来自最近 AI 分析</span>
          </div>
          <div class="rate-block">
            <span class="rate-label">压力水平（AI 自动）</span>
            <el-rate v-model="form.stressScore" :colors="['#ef4444','#ef4444','#ef4444']" />
            <span class="rate-hint" v-if="emotionStore.latest">来自最近 AI 分析</span>
          </div>
        </div>
      </el-form>
      <template #footer>
        <div class="dialog-foot">
          <button class="dialog-btn ghost" @click="formVisible = false">
            <span>取消</span>
          </button>
          <button class="dialog-btn primary" :disabled="submitting" @click="submitForm">
            <span>{{ formMode === 'create' ? '种下这朵花' : '保存修改' }}</span>
            <el-icon v-if="!submitting" :size="16"><Cherry /></el-icon>
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
  background: linear-gradient(180deg, #f0f7ff 0%, #eaf4ff 55%, #f6fdf9 100%);
}

/* ===== 花园品牌（覆盖 AppNavBar 默认品牌） ===== */
.garden-brand {
  display: flex;
  flex-direction: column;
  gap: 2px;
  text-decoration: none;
}
.garden-name { font-size: 18px; font-weight: 800; color: #111827; letter-spacing: 1px; }
.garden-sub  { font-size: 11px; color: #94a3b8; letter-spacing: 0.5px; }

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
  gap: 10px; max-width: 1100px; width: calc(100% - 48px); margin: 96px auto 0;
}
.stat-card {
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 3px;
  padding: 12px 14px; border-radius: 16px;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(22px);
  border: 1px solid rgba(255, 255, 255, 0.9);
  box-shadow: 0 8px 24px rgba(16, 185, 129, 0.06);
}
.stat-num { font-size: 22px; font-weight: 800; background: linear-gradient(135deg, #34d399, #059669); -webkit-background-clip: text; background-clip: text; color: transparent; }
.stat-label { font-size: 11px; color: #64748b; letter-spacing: 1px; }

/* ===== 心情分布（详细） ===== */
.stat-dist { align-items: stretch; gap: 8px; }
.dist-head { display: flex; align-items: baseline; justify-content: space-between; }
.dist-total { font-size: 10px; color: #94a3b8; font-variant-numeric: tabular-nums; }

.dist-stack {
  display: flex;
  height: 8px;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(148, 163, 184, 0.15);
}
.stack-seg { transition: flex 0.4s ease; min-width: 0; }
.stack-seg.positive { background: linear-gradient(90deg, #facc15, #f59e0b); }
.stack-seg.neutral  { background: linear-gradient(90deg, #34d399, #10b981); }
.stack-seg.negative { background: linear-gradient(90deg, #94a3b8, #64748b); }

.dist-legend { display: flex; gap: 10px; font-size: 10.5px; color: #475569; }
.lg-item { display: inline-flex; align-items: center; gap: 4px; }
.lg-dot { width: 7px; height: 7px; border-radius: 50%; }
.lg-item.positive .lg-dot { background: linear-gradient(135deg, #facc15, #f59e0b); }
.lg-item.neutral  .lg-dot { background: linear-gradient(135deg, #34d399, #10b981); }
.lg-item.negative .lg-dot { background: linear-gradient(135deg, #94a3b8, #64748b); }

.dist-columns {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 6px;
  margin-top: 2px;
}
.dist-col {
  padding: 6px 8px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.85);
}
.col-head {
  display: flex;
  align-items: center;
  gap: 3px;
  margin-bottom: 6px;
  font-size: 10.5px;
  color: #475569;
}
.col-emoji { font-size: 11px; }
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
  border-radius: 999px;
  background: var(--bg);
  border: 1px solid color-mix(in srgb, var(--c) 30%, transparent);
  font-size: 9.5px;
  color: var(--c);
  font-weight: 600;
}
.bubble-icon { font-size: 10px; }
.bubble-empty { font-size: 11px; color: #cbd5e1; }

/* ===== 花园场景 ===== */
.garden-scene {
  position: relative; z-index: 2; flex: 1; min-height: 0;
  margin: 14px auto 0; width: calc(100% - 48px); max-width: 1100px;
  border-radius: 28px; overflow: hidden;
  background: linear-gradient(180deg, #dbeafe 0%, #eef6ff 42%, #e8f8f0 85%, #dcfce7 100%);
  border: 1px solid rgba(255, 255, 255, 0.9);
  box-shadow: 0 20px 60px rgba(16, 185, 129, 0.08);
}
.sky-sun {
  position: absolute; top: 28px; right: 56px;
  width: 64px; height: 64px; border-radius: 50%;
  background: radial-gradient(circle at 38% 35%, #fef3c7, #fbbf24 70%);
  box-shadow: 0 0 40px rgba(251, 191, 36, 0.35);
  animation: sunFloat 7s ease-in-out infinite;
  z-index: 1;
}
@keyframes sunFloat { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(-6px); } }
.sky-cloud { position: absolute; height: 30px; border-radius: 999px; background: rgba(255, 255, 255, 0.75); backdrop-filter: blur(8px); z-index: 1; }
.cloud-1 { width: 110px; top: 40px; left: 10%; }
.cloud-2 { width: 85px; top: 90px; left: 55%; opacity: 0.65; }
.ground-layer { position: absolute; left: 0; right: 0; z-index: 2; }
.ground-back { bottom: 0; height: 30%; background: linear-gradient(180deg, #b7e4c7 0%, #95d5b2 50%, #74c69d 100%); border-radius: 55% 55% 0 0 / 12% 12% 0 0; }
.ground-front { bottom: 0; height: 18%; background: linear-gradient(180deg, rgba(255,255,255,0) 0%, #52b788 100%); border-radius: 60% 60% 0 0 / 16% 16% 0 0; }
.garden-loading { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; font-size: 22px; color: #059669; z-index: 3; }
.garden-empty { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; font-size: 14px; color: #94a3b8; letter-spacing: 2px; z-index: 3; }

/* ===== 花朵网格 ===== */
.flower-grid {
  position: absolute; bottom: 8%; left: 0; right: 0;
  display: grid; grid-template-columns: repeat(6, 1fr);
  gap: 6px 0; padding: 0 8%; z-index: 3;
}
.flower-cell {
  position: relative; height: 110px;
  display: flex; flex-direction: column; align-items: center; justify-content: flex-end;
}
.flower-cell.empty { opacity: 0.45; }
.flower-cell.clickable:not(.empty) { cursor: pointer; }
.flower-cell.clickable:not(.empty):hover { transform: translateY(-4px); transition: transform 0.2s; }

.dirt { width: 38px; height: 14px; border-radius: 50%; background: radial-gradient(ellipse at center, #b7a58a, #9c8b6e); margin-bottom: -7px; position: relative; z-index: 1; }
.empty-soil { width: 34px; height: 12px; border-radius: 50%; background: rgba(156, 139, 110, 0.25); margin-bottom: -5px; }
.empty-mark { position: absolute; bottom: 20px; font-size: 22px; color: rgba(148, 163, 184, 0.35); font-weight: 300; }

.stem { width: 3px; height: 0; border-radius: 3px; background: linear-gradient(180deg, #6ee7b7, #059669); position: relative; z-index: 0; transition: height 0.8s ease; }
.stem.seed  { height: 22px; }
.stem.bud   { height: 38px; }
.stem.bloom { height: 52px; }

.flower-head { position: relative; width: 28px; height: 28px; margin-top: -8px; z-index: 2; transform-origin: bottom center; transition: all 0.5s ease; }
.flower-head.seed  { transform: scale(0.4); opacity: 0.5; }
.flower-head.bud   { transform: scale(0.75); opacity: 0.8; }
.flower-head.bloom { transform: scale(1); opacity: 1; animation: sway 4.2s ease-in-out infinite; }
@keyframes sway { 0%, 100% { transform: scale(1) rotate(-2deg); } 50% { transform: scale(1) rotate(2deg); } }

.petal { position: absolute; width: 13px; height: 13px; border-radius: 50%; background: radial-gradient(circle at 65% 65%, #ffffff, var(--petal)); }
.p1 { top: 0;    left: 50%; transform: translateX(-50%); }
.p2 { top: 25%;  left: 0%;   }
.p3 { top: 25%;  right: 0%;  }
.p4 { bottom: 0; left: 25%;  }
.p5 { bottom: 0; right: 25%; }
.core { position: absolute; top: 50%; left: 50%; width: 9px; height: 9px; border-radius: 50%; background: radial-gradient(circle at 35% 35%, #fde68a, #f59e0b); transform: translate(-50%, -50%); box-shadow: 0 0 6px rgba(251, 191, 36, 0.4); }

.leaf { position: absolute; bottom: 36px; left: calc(50% + 2px); width: 0; height: 0; background: #4ade80; border-radius: 100% 0 100% 0; transform: rotate(-15deg); transition: all 0.5s ease; }
.leaf.seed  { width: 0;  height: 0; }
.leaf.bud   { width: 10px; height: 5px; }
.leaf.bloom { width: 14px; height: 7px; }

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
.tag-emoji { font-size: 11px; }
.tag-date  { color: #64748b; }
.tag-score { color: #f59e0b; font-weight: 700; }

/* ===== 底部 CTA ===== */
.garden-plant { position: relative; z-index: 5; padding: 14px 24px 20px; }
.plant-bar {
  max-width: 960px; margin: 0 auto;
  display: flex; align-items: center; justify-content: space-between; gap: 14px;
  padding: 12px 18px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.6); backdrop-filter: blur(26px);
  border: 1px solid rgba(255, 255, 255, 0.9);
  box-shadow: 0 14px 40px rgba(16, 185, 129, 0.1);
}
.plant-bar-info { display: flex; flex-direction: column; gap: 2px; }
.plant-bar-title { font-size: 13px; font-weight: 700; color: #111827; }
.plant-bar-sub   { font-size: 11px; color: #94a3b8; }

.plant-cta {
  display: inline-flex; align-items: center; gap: 7px;
  height: 38px; padding: 0 18px;
  border-radius: 12px; border: none;
  background: linear-gradient(135deg, #34d399, #059669);
  color: #ffffff; font-size: 13px; font-weight: 700; letter-spacing: 1px;
  cursor: pointer;
  box-shadow: 0 8px 22px rgba(16, 185, 129, 0.32);
  transition: all 0.2s;
}
.plant-cta:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 12px 28px rgba(16, 185, 129, 0.42); }
.plant-cta:disabled { background: rgba(148, 163, 184, 0.4); color: rgba(255,255,255,0.8); cursor: not-allowed; box-shadow: none; }

/* ===== 弹窗 ===== */
.flower-dialog :deep(.el-dialog) {
  border-radius: 22px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(26px);
  border: 1px solid rgba(255, 255, 255, 0.9);
  box-shadow: 0 24px 70px rgba(15, 118, 110, 0.18);
}
.flower-dialog :deep(.el-dialog__header) { padding: 16px 18px 8px; }
.flower-dialog :deep(.el-dialog__body) { padding: 6px 18px 4px; }
.flower-dialog :deep(.el-dialog__footer) { padding: 8px 18px 16px; }

.dialog-head { display: flex; align-items: center; gap: 10px; }
.dialog-title { font-size: 15px; font-weight: 700; color: #111827; }
.dialog-sub { font-size: 11px; color: #94a3b8; margin-top: 2px; }

.dialog-body { padding-top: 6px; }
.dialog-content {
  padding: 11px 13px; border-radius: 14px;
  background: linear-gradient(135deg, rgba(52, 211, 153, 0.07), rgba(59, 130, 246, 0.05));
  border: 1px solid rgba(99, 102, 241, 0.12);
  font-size: 13px; line-height: 1.7; color: #334155;
  white-space: pre-wrap; word-break: break-word;
}
.dialog-stars { margin-top: 12px; display: flex; flex-direction: column; gap: 8px; }
.dialog-trigger {
  margin-top: 10px;
  display: flex; align-items: center; gap: 8px;
  font-size: 12px; color: #475569;
}
.dialog-trigger .dt-label { width: 60px; flex-shrink: 0; color: #64748b; }
.dialog-trigger .dt-tag {
  padding: 2px 10px; border-radius: 999px;
  background: rgba(99, 102, 241, 0.10);
  color: #4f46e5; font-weight: 500;
}
.dialog-star-row { display: flex; align-items: center; gap: 10px; font-size: 12px; color: #475569; }
.ds-label { width: 68px; flex-shrink: 0; }
.dialog-star-row :deep(.el-rate) { flex: 1; min-width: 0; }
.ds-val { font-size: 11px; color: #94a3b8; font-variant-numeric: tabular-nums; }

.dialog-foot { display: flex; justify-content: flex-end; gap: 10px; }
.dialog-btn {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 8px 16px; border-radius: 12px;
  font-size: 13px; font-weight: 600; cursor: pointer;
  transition: all 0.2s;
}
.dialog-btn.primary {
  border: none; background: linear-gradient(135deg, #34d399, #059669); color: #ffffff;
  box-shadow: 0 6px 18px rgba(16, 185, 129, 0.3);
}
.dialog-btn.primary:hover { transform: translateY(-1px); box-shadow: 0 10px 24px rgba(16, 185, 129, 0.4); }
.dialog-btn.primary:disabled { opacity: 0.5; cursor: not-allowed; transform: none; }
.dialog-btn.ghost { border: 1px solid rgba(226, 232, 240, 0.9); background: rgba(255, 255, 255, 0.6); color: #64748b; }
.dialog-btn.ghost:hover { color: #ef4444; border-color: #fca5a5; }

/* ===== 表单 ===== */
.form-chips { display: flex; flex-wrap: wrap; gap: 6px; }
.form-chip {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 6px 12px; border-radius: 12px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.6);
  font-size: 12.5px; color: #475569; cursor: pointer;
  transition: all 0.2s;
}
.form-chip:hover { border-color: var(--chip-color); color: var(--chip-color); transform: translateY(-1px); }
.form-chip.active {
  border-color: var(--chip-color); color: #ffffff;
  background: var(--chip-color);
  box-shadow: 0 6px 16px color-mix(in srgb, var(--chip-color) 30%, transparent);
}

.form-rates {
  margin-top: 4px; padding: 12px 14px; border-radius: 14px;
  background: rgba(255, 255, 255, 0.6); border: 1px solid rgba(226, 232, 240, 0.85);
  display: flex; flex-direction: column; gap: 10px;
}
.rate-block { display: flex; align-items: center; gap: 10px; }
.rate-label { font-size: 12px; color: #64748b; width: 130px; flex-shrink: 0; }
.rate-block :deep(.el-rate) { flex: 1; min-width: 0; }
.rate-hint { font-size: 10.5px; color: #94a3b8; }

/* ===== 响应式 ===== */
@media (max-width: 960px) {
  .garden-stats { grid-template-columns: 1fr 1fr; width: calc(100% - 28px); }
  .stat-dist { grid-column: 1 / -1; }
  .garden-scene { width: calc(100% - 28px); }
  .flower-grid { grid-template-columns: repeat(4, 1fr); padding: 0 5%; }
  .nav-progress { display: none; }
  .nav-sub { display: none; }
}
@media (max-width: 640px) {
  .flower-grid { grid-template-columns: repeat(3, 1fr); padding: 0 3%; }
  .garden-nav { padding: 12px 14px; }
}
/* iPhone 窄屏：压缩导航与种植面板，UI 不变仅防错乱 */
@media (max-width: 520px) {
  .garden {
    height: 100dvh;
  }

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
    width: calc(100% - 24px);
    margin-top: 12px;
    border-radius: 22px;
  }

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
}
</style>
