<script setup>
import { ref, computed, nextTick, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useEmotionStore } from '@/stores/emotion'
import { logout as logoutApi } from '@/api/auth'
import {
  createSession,
  getMySessions,
  getMessageList,
  archiveSession,
  deleteSession,
  streamChatMessage,
  analyzeEmotion,
  getAvailableModels
} from '@/api/consult'
import AnalysisRing from '@/components/AnalysisRing.vue'
import AppNavBar from '@/components/AppNavBar.vue'
import UserDropdown from '@/components/UserDropdown.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const displayName = computed(() => authStore.userInfo?.nickname || '用户')
const roleText = computed(() => (authStore.userInfo?.role === 'admin' ? '管理员' : '普通用户'))
const emotionStore = useEmotionStore()

const sessionId = ref(null)
const messages = ref([])
const input = ref('')
const sending = ref(false)
const creating = ref(true)
const loadingMessages = ref(false)

// AI 模型切换
const availableModels = ref({})
const currentModel = ref(localStorage.getItem('mha_model') || 'qwen3.8-max')
const modelMenuVisible = ref(false)
function selectModel(id) {
  currentModel.value = id
  localStorage.setItem('mha_model', id)
  modelMenuVisible.value = false // 选中后自动关闭弹窗
  ElMessage.success('已切换为 ' + (modelLabel(id) || id))
}
function modelLabel(id) {
  for (const [k, v] of Object.entries(availableModels.value)) {
    if (v === id) return k
  }
  return ''
}

const sessions = ref([])
const loadingSessions = ref(false)
const historyVisible = ref(false)
const historyTab = ref('all')
const selectMode = ref(false)
const selected = ref(new Set())

const moodPanel = ref(false)
const currentMood = ref('平静')
const listRef = ref()

// ===== AI 情绪分析 =====
const analysisVisible = ref(true)
const analyzing = ref(false)
const emotion = ref(null)
const lastAnalysisTime = ref('')
// 默认折叠，若系统设置开启「自动展开情绪分析」则展开
const sideCollapsed = ref(localStorage.getItem('mha_analysis_auto_open') === '1' ? false : true)

// 分析反馈文案
const analysisSummary = computed(() => {
  const d = emotion.value
  if (!d) return ''
  const max = Math.max(d.stress, d.anxiety, d.sleepRisk)
  if (max >= 65) return `你的${d.emotion}情绪较为明显，建议给自己一些时间放松，需要时随时来找我聊聊`
  if (max >= 40) return `整体情绪在可接受范围，${d.emotion}维度稍有波动，保持规律作息会帮你更平稳`
  return '当前情绪状态良好，继续保持积极心态，每一天都值得好好度过'
})

// 反馈
const feedbackGiven = ref('')
function giveFeedback(type) {
  feedbackGiven.value = type
  ElMessage.success(type === 'helpful' ? '感谢你的反馈 ❤️' : type === 'not-helpful' ? '已收到，我们会改进 🙏' : '太开心能帮到你 ✨')
  setTimeout(() => { feedbackGiven.value = '' }, 2500)
}

const WELCOME =
  '您好，我是 MindMan，您的 AI 心理健康助手。今天感觉怎么样？可以慢慢告诉我，我会一直在这里陪着你。'

const moodOptions = ['很平静', '还不错', '有点低落', '很糟糕']
const moodIcons = {
  平静: 'Sunny',
  很平静: 'Sunrise',
  还不错: 'PartlyCloudy',
  有点低落: 'Cloudy',
  很糟糕: 'Drizzling'
}

const moodIconName = computed(() => moodIcons[currentMood.value] || 'Sunny')

const userInitial = computed(
  () =>
    authStore.userInfo?.nickname?.charAt(0) ||
    authStore.userInfo?.username?.charAt(0) ||
    '我'
)

// 回到主页
function goHome() {
  if (route.path === '/home') return
  router.push('/home')
}

// 发送消息后自动分析情绪
async function runAnalysis(content) {
  analyzing.value = true
  emotion.value = null
  try {
    const data = await analyzeEmotion(content)
    // 稍作停顿，让扫描动效完整呈现
    await new Promise((r) => setTimeout(r, 380))
    emotion.value = data
    lastAnalysisTime.value = (data.analyzedAt || '').slice(11, 19) || nowTime()
    // 跨页面共享：情绪花园种花时读取这些字段作为预填
    emotionStore.setLatest({
      emotion: data.emotion,
      emotionIcon: data.emotionIcon,
      emotionScore: data.emotionStar,
      sleepScore: data.sleepStar,
      stressScore: data.stressStar,
      analyzedAt: data.analyzedAt
    })
  } catch (e) {
    /* 分析失败不打扰对话 */
  } finally {
    analyzing.value = false
  }
}

function nowTime() {
  return new Date().toLocaleTimeString('zh-CN', { hour12: false })
}

function scrollToBottom() {
  nextTick(() => {
    const el = listRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function pushMessage(role, content, cards = []) {
  messages.value.push({ role, content, time: nowTime(), cards })
  scrollToBottom()
}

// ===== 会话 =====
async function loadSessions() {
  loadingSessions.value = true
  try {
    const data = await getMySessions({ page: 1, pageSize: 30 })
    sessions.value = Array.isArray(data) ? data : (data?.list || [])
  } catch (e) {
    if (!e?.handled) ElMessage.error(e.message || '加载会话列表失败')
  } finally {
    loadingSessions.value = false
  }
}

function formatSessionTime(session) {
  const t = session.lastTime || session.startedAt || ''
  return t ? t.slice(5, 16) : ''
}

const historyTabs = computed(() => [
  { key: 'all', label: '全部', count: sessions.value.length },
  { key: 'archived', label: '已归档', count: sessions.value.filter((s) => s.status === 2).length }
])

const visibleSessions = computed(() => {
  const list = sessions.value
  if (historyTab.value === 'archived') return list.filter((s) => s.status === 2)
  return list
})

const allSelected = computed(
  () => visibleSessions.value.length > 0 && selected.value.size === visibleSessions.value.length
)

async function createNewSession() {
  creating.value = true
  try {
    const data = await createSession()
    if (data?.id) {
      sessionId.value = data.id
    }
    messages.value = []
    pushMessage('assistant', WELCOME)
    loadSessions()
    return true
  } catch (e) {
    if (!e?.handled) ElMessage.error(e.message || '创建会话失败，请稍后重试')
    return false
  } finally {
    creating.value = false
  }
}

// 新建会话：归档所有进行中的会话（确保永远只有一个进行中），再创建新会话
async function startNewSession() {
  // 归档所有进行中的会话
  const activeList = sessions.value.filter((s) => s.status !== 2)
  for (const s of activeList) {
    if (s.id === sessionId.value && !messages.value.some((m) => m.role === 'user')) continue
    try { await archiveSession(s.id); s.status = 2; s.statusText = '已结束' } catch (e) { /* 不阻塞 */ }
  }
  historyVisible.value = false
  await createNewSession()
}

async function archiveSessionItem(session) {
  if (session.id === sessionId.value) {
    ElMessage.warning('正在进行的会话不能归档，请先新建一个会话')
    return
  }
  try {
    await archiveSession(session.id)
    session.status = 2
    session.statusText = '已结束'
    session.endedAt = new Date().toLocaleString()
    ElMessage.success('会话已归档')
  } catch (e) {
    if (!e?.handled) ElMessage.error(e.message || '归档失败，请稍后重试')
  }
}

async function removeSession(session) {
  if (session.id === sessionId.value) {
    ElMessage.warning('正在进行的会话不能删除，请先新建一个会话')
    return
  }
  try {
    await ElMessageBox.confirm('确定删除这条会话记录吗？删除后不可恢复。', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await deleteSession(session.id)
    sessions.value = sessions.value.filter((s) => s.id !== session.id)
    ElMessage.success('会话已删除')
  } catch (e) {
    if (!e?.handled) ElMessage.error(e.message || '删除失败，请稍后重试')
  }
}

async function openSession(session) {
  if (sending.value || loadingMessages.value) return
  if (session.id === sessionId.value && messages.value.length) return
  sessionId.value = session.id
  creating.value = false
  historyVisible.value = false
  loadingMessages.value = true
  try {
    const list = await getMessageList(session.id)
    messages.value = (list || []).map((m) => ({
      role: m.role,
      content: m.content,
      time: (m.createdAt || '').slice(11, 19),
      cards: m.cards || []
    }))
    if (!messages.value.length) {
      pushMessage('assistant', WELCOME)
    }
    nextTick(() => {
      const el = listRef.value
      if (el) el.scrollTop = el.scrollHeight
    })
  } catch (e) {
    if (!e?.handled) ElMessage.error(e.message || '加载会话消息失败')
  } finally {
    loadingMessages.value = false
  }
}

// ===== 多选删除 =====
function toggleSelectMode() {
  selectMode.value = !selectMode.value
  if (!selectMode.value) selected.value.clear()
}

function toggleSelect(session) {
  const set = selected.value
  if (set.has(session.id)) {
    set.delete(session.id)
  } else {
    set.add(session.id)
  }
}

function toggleSelectAll() {
  if (allSelected.value) {
    selected.value.clear()
  } else {
    const set = selected.value
    visibleSessions.value.forEach((s) => set.add(s.id))
  }
}

async function removeSelected() {
  const ids = [...selected.value]
  if (!ids.length) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${ids.length} 个会话吗？删除后不可恢复。`, '批量删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    for (const id of ids) {
      await deleteSession(id)
    }
    sessions.value = sessions.value.filter((s) => !ids.includes(s.id))
    if (ids.includes(sessionId.value)) {
      sessionId.value = null
      messages.value = []
      await createNewSession()
    }
    selected.value.clear()
    selectMode.value = false
    ElMessage.success(`已删除 ${ids.length} 个会话`)
  } catch (e) {
    if (!e?.handled) ElMessage.error(e.message || '删除失败，请稍后重试')
  }
}

// ===== 消息发送 =====
async function handleSend(text) {
  const content = (text ?? input.value).trim()
  if (!content || sending.value) return
  if (!sessionId.value) {
    ElMessage.warning('会话创建中，请稍候')
    return
  }
  pushMessage('user', content)
  input.value = ''
  sending.value = true

  // 用户倾诉后自动触发 AI 情绪分析
  runAnalysis(content)

  // 先插入一条空的 AI 消息，等待首字到达后开始流式输出
  const reply = { role: 'assistant', content: '', time: nowTime(), cards: [], streaming: true }
  messages.value.push(reply)
  scrollToBottom()
  try {
    for await (const chunk of streamChatMessage(sessionId.value, content, currentModel.value)) {
      if (chunk.text) reply.content = (reply.content || '') + chunk.text
      if (chunk.done) {
        reply.cards = chunk.cards || []
        reply.streaming = false
      }
      scrollToBottom()
    }
  } catch (e) {
    const idx = messages.value.indexOf(reply)
    if (idx > -1 && !reply.content) messages.value.splice(idx, 1)
    if (!e?.handled) ElMessage.error(e.message || '发送失败，请稍后再试')
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

// ===== 心情 =====
function toggleMood() {
  moodPanel.value = !moodPanel.value
}

function pickMood(option) {
  currentMood.value = option
  moodPanel.value = false
  handleSend(`我今天感觉${option}`)
}

function handleTodo() {
  ElMessage.info('功能开发中，敬请期待')
}

function askAdvice() {
  handleSend('可以给我一些放松心情的小建议吗？')
}

// ===== 语音输入 =====
const listening = ref(false)
let recognition = null

function toggleVoice() {
  const SR = window.SpeechRecognition || window.webkitSpeechRecognition
  if (!SR) {
    ElMessage.warning('当前浏览器不支持语音输入，请使用 Chrome 或 Edge')
    return
  }
  if (!recognition) {
    recognition = new SR()
    recognition.lang = 'zh-CN'
    recognition.interimResults = true
    recognition.continuous = false
    recognition.onresult = (e) => {
      let text = ''
      for (let i = 0; i < e.results.length; i++) {
        text += e.results[i][0].transcript
      }
      input.value = text
    }
    recognition.onend = () => {
      listening.value = false
    }
    recognition.onerror = (e) => {
      listening.value = false
      if (e.error !== 'aborted' && e.error !== 'no-speech') {
        ElMessage.warning('语音识别出错，请重试')
      }
    }
  }
  if (listening.value) {
    recognition.stop()
    listening.value = false
    ElMessage.success('已结束录音')
  } else {
    input.value = ''
    recognition.start()
    listening.value = true
    ElMessage.info('正在聆听，请说话…')
  }
}

// ===== 退出登录 =====
async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '退出确认', {
      confirmButtonText: '退出登录',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await logoutApi()
  } catch (e) {
    /* 后端注销失败也继续清空本地状态 */
  }
  authStore.logout()
  ElMessage.success('已退出登录')
  router.replace('/login')
}

onMounted(async () => {
  // 加载可用模型
  try {
    availableModels.value = await getAvailableModels()
    // 本地保存的旧模型可能已下线，自动回退到默认旗舰
    if (!Object.values(availableModels.value).includes(currentModel.value)) {
      currentModel.value = 'qwen3.8-max'
      localStorage.setItem('mha_model', currentModel.value)
    }
  } catch {
    availableModels.value = { 'Qwen3.8-Max（旗舰）': 'qwen3.8-max' }
  }
  await loadSessions()
  // 优先根据 URL ?session=xxx 打开指定会话
  const targetId = Number(route.query.session)
  if (targetId) {
    const target = sessions.value.find((s) => s.id === targetId)
    if (target) {
      openSession(target)
      return
    }
  }
  // 有历史会话时恢复最近一条，不自动新建；只有完全没有会话时才自动创建
  const active = sessions.value.find((s) => s.status !== 2)
  if (active) {
    openSession(active)
  } else if (sessions.value.length) {
    openSession(sessions.value[0])
  } else {
    createNewSession()
  }
})
</script>

<template>
  <div class="workspace">
    <aside class="side" :class="{ collapsed: sideCollapsed }">
      <!-- 折叠态：面板完全隐藏 -->
      <template v-if="sideCollapsed"></template>

      <!-- 展开态 -->
      <template v-else>
        <div class="side-head">
          <div class="side-head-row">
            <div class="side-title">
              <span class="pulse-dot"></span>
              <span>AI 情绪分析</span>
            </div>
            <el-tooltip content="收起面板" placement="left" :show-after="300">
              <button class="collapse-btn" @click="sideCollapsed = true">
                <el-icon><DArrowLeft /></el-icon>
              </button>
            </el-tooltip>
          </div>
          <div class="side-sub">Mind Sensor · 实时感知</div>
        </div>

        <div class="side-body">
          <!-- 空状态 -->
          <div v-if="!analyzing && !emotion" class="analysis-idle">
            <svg viewBox="0 0 120 50" class="wave-svg" aria-hidden="true">
              <path
                d="M4 28 Q 16 6 28 24 T 52 24 T 76 24 T 100 24 T 116 20"
                fill="none"
                stroke="url(#waveGrad)"
                stroke-width="2.5"
                stroke-linecap="round"
              />
              <defs>
                <linearGradient id="waveGrad" x1="0%" y1="0%" x2="100%" y2="0%">
                  <stop offset="0%" stop-color="#60a5fa" />
                  <stop offset="100%" stop-color="#3b82f6" />
                </linearGradient>
              </defs>
            </svg>
            <p class="idle-text">开始倾诉后<br />自动分析情绪状态</p>
          </div>

          <!-- 分析中 -->
          <div v-else-if="analyzing" class="analysis-scan">
            <div class="scan-wrap">
              <div class="scan-ring"></div>
              <div class="scan-line"></div>
              <span class="scan-core"></span>
            </div>
            <p class="scan-text">正在分析…</p>
          </div>

          <!-- 结果 -->
          <div v-else class="analysis-result">
            <!-- 主导情绪 + 评分 -->
            <div class="emotion-chip">
              <span class="emotion-icon">{{ emotion.emotionIcon }}</span>
              <div class="emotion-meta">
                <div class="emotion-name">{{ emotion.emotion }}</div>
                <div class="emotion-bar">
                  <span :style="{ width: emotion.emotionScore + '%' }"></span>
                </div>
              </div>
              <span class="emotion-score">{{ emotion.emotionScore }}</span>
            </div>

            <!-- 三维星制评分（与后台格式一致） -->
            <div class="star-board">
              <div class="star-row">
                <span class="star-label">情绪评分</span>
                <el-rate
                  :model-value="emotion.emotionStar"
                  disabled
                  :colors="['#fbbf24', '#fbbf24', '#fbbf24']"
                />
                <span class="star-val">{{ emotion.emotionStar }}/5</span>
              </div>
              <div class="star-row">
                <span class="star-label">睡眠质量</span>
                <el-rate
                  :model-value="emotion.sleepStar"
                  disabled
                  :colors="['#22d3ee', '#22d3ee', '#22d3ee']"
                />
                <span class="star-val">{{ emotion.sleepStar }}/5</span>
              </div>
              <div class="star-row">
                <span class="star-label">压力水平</span>
                <el-rate
                  :model-value="emotion.stressStar"
                  disabled
                  :colors="['#ef4444', '#ef4444', '#ef4444']"
                />
                <span class="star-val">{{ emotion.stressStar }}/5</span>
              </div>
            </div>

            <div class="rings-stack">
              <div class="ring-card">
                <AnalysisRing
                  :value="emotion.stress"
                  label="压力值"
                  :level="emotion.stressLevel"
                  color="#ff6b6b"
                  color2="#ffb35c"
                  :size="88"
                />
              </div>
              <div class="ring-card">
                <AnalysisRing
                  :value="emotion.anxiety"
                  label="焦虑指数"
                  :level="emotion.anxietyLevel"
                  color="#4d7cff"
                  color2="#60a5fa"
                  :size="88"
                />
              </div>
              <div class="ring-card">
                <AnalysisRing
                  :value="emotion.sleepRisk"
                  label="睡眠风险"
                  :level="emotion.sleepLevel"
                  color="#22d3ee"
                  color2="#34d399"
                  :size="88"
                />
              </div>
            </div>

            <div class="analysis-suggestions">
              <div v-for="(tip, ti) in emotion.suggestions" :key="ti" class="suggestion-item">
                <el-icon><Sparkles /></el-icon>
                <span>{{ tip }}</span>
              </div>
            </div>

            <!-- AI 分析反馈卡片 -->
            <div class="feedback-card">
              <div class="fb-card-head">
                <el-icon><ChatLineSquare /></el-icon>
                <span>AI 分析反馈</span>
              </div>
              <p class="fb-card-body">{{ analysisSummary }}</p>
              <div v-if="lastAnalysisTime" class="fb-card-foot">
                <el-icon><Timer /></el-icon>
                {{ lastAnalysisTime }} 更新
              </div>
            </div>

          </div>
        </div>

        <div class="side-foot">
          <span v-if="lastAnalysisTime" class="foot-time">{{ lastAnalysisTime }} 更新</span>
        </div>
      </template>
    </aside>

    <section class="main">
      <header class="chat-header">
        <div class="ai-brand">
          <div class="ai-logo">
            <svg viewBox="0 0 48 48" width="22" height="22" aria-hidden="true">
              <g fill="#ffffff">
                <ellipse cx="24" cy="10" rx="6" ry="8.5" />
                <ellipse cx="24" cy="38" rx="6" ry="8.5" />
                <ellipse cx="10" cy="24" rx="8.5" ry="6" />
                <ellipse cx="38" cy="24" rx="8.5" ry="6" />
                <circle cx="24" cy="24" r="5.5" />
              </g>
            </svg>
          </div>
          <div class="ai-info">
            <div class="ai-name">MindMan</div>
            <div class="ai-sub" :class="{ thinking: sending || loadingMessages }">
              <span v-if="sending || loadingMessages">正在思考…</span>
              <span v-else>MindMan · 在线陪伴</span>
            </div>
          </div>
        </div>

        <div class="header-actions">
           <el-tooltip
             :content="sideCollapsed ? '展开 AI 情绪分析' : '折叠 AI 情绪分析'"
             placement="bottom"
             :show-after="300"
           >
             <button class="icon-btn" @click="sideCollapsed = !sideCollapsed">
               <el-icon><DataAnalysis /></el-icon>
             </button>
           </el-tooltip>

          <el-tooltip content="新建对话" placement="bottom" :show-after="400">
            <button class="icon-btn" @click="startNewSession">
              <el-icon><Plus /></el-icon>
            </button>
          </el-tooltip>

          <el-tooltip content="会话历史" placement="bottom" :show-after="400">
            <button class="icon-btn" @click="historyVisible = true">
              <el-icon><Clock /></el-icon>
            </button>
          </el-tooltip>

          <!-- 导航 -->
          <el-tooltip content="情绪花园" placement="bottom" :show-after="400">
            <button class="icon-btn" @click="router.push('/garden')">
              <el-icon><Cherry /></el-icon>
            </button>
          </el-tooltip>
          <el-tooltip content="知识文章" placement="bottom" :show-after="400">
             <button class="icon-btn hide-sm" @click="router.push('/home/articles')">
               <el-icon><Collection /></el-icon>
             </button>
           </el-tooltip>
          <el-tooltip content="白噪音空间" placement="bottom" :show-after="400">
            <button class="icon-btn hide-sm" @click="router.push('/relax')">
              <el-icon><WindPower /></el-icon>
            </button>
          </el-tooltip>
          <el-tooltip content="回到主页" placement="bottom" :show-after="400">
            <button class="icon-btn home-icon" @click="goHome">
              <el-icon><HomeFilled /></el-icon>
            </button>
          </el-tooltip>

          <!-- 用户 -->
          <UserDropdown />
        </div>
      </header>

      <div ref="listRef" class="chat-scroll">
        <div class="chat-column">
          <div
            v-for="(msg, index) in messages"
            :key="index"
            class="chat-row"
            :class="msg.role"
          >
            <div
              v-if="msg.role === 'assistant'"
              class="bubble-avatar"
              :class="{ breathe: msg.streaming }"
            >
              <svg viewBox="0 0 48 48" width="18" height="18" aria-hidden="true">
                <g fill="#ffffff">
                  <ellipse cx="24" cy="10" rx="5" ry="7.5" />
                  <ellipse cx="24" cy="38" rx="5" ry="7.5" />
                  <ellipse cx="10" cy="24" rx="7.5" ry="5" />
                  <ellipse cx="38" cy="24" rx="7.5" ry="5" />
                  <circle cx="24" cy="24" r="4.5" />
                </g>
              </svg>
            </div>

            <div v-if="msg.role === 'assistant'" class="ai-bubble" :class="{ streaming: msg.streaming }">
              <div class="bubble-meta">
                <span class="bubble-name">MindMan</span>
                <span class="bubble-time">{{ msg.time }}</span>
              </div>

              <div v-if="msg.streaming && !msg.content" class="thinking-inline">
                <span class="thinking-label">正在思考</span>
                <div class="thinking-wave">
                  <span v-for="i in 9" :key="i" :style="{ animationDelay: i * 0.08 + 's' }"></span>
                </div>
              </div>
              <template v-else>
                <div class="chat-content">
                  {{ msg.content }}<span v-if="msg.streaming" class="stream-cursor"></span>
                </div>
                <div v-for="(card, ci) in msg.cards" :key="ci" class="ai-card">
                  <div class="card-head">
                    <span class="card-label">{{ card.label }}</span>
                    <span class="card-emoji">{{ card.emoji }}</span>
                  </div>
                  <div class="card-title">{{ card.title }}</div>
                  <div v-if="card.percent !== undefined" class="card-bar">
                    <span class="card-bar-fill" :style="{ width: card.percent + '%' }"></span>
                  </div>
                  <div v-if="card.percent !== undefined" class="card-percent">
                    {{ card.percent }}%
                  </div>
                  <div v-if="card.duration" class="card-duration">{{ card.duration }}</div>
                </div>
              </template>
            </div>

            <div v-if="msg.role === 'user'" class="user-bubble">
              <div class="chat-content">{{ msg.content }}</div>
              <div class="bubble-time">{{ msg.time }}</div>
            </div>
          </div>

          <div v-if="loadingMessages" class="chat-row assistant">
            <div class="bubble-avatar breathe">
              <svg viewBox="0 0 48 48" width="18" height="18" aria-hidden="true">
                <g fill="#ffffff">
                  <ellipse cx="24" cy="10" rx="5" ry="7.5" />
                  <ellipse cx="24" cy="38" rx="5" ry="7.5" />
                  <ellipse cx="10" cy="24" rx="7.5" ry="5" />
                  <ellipse cx="38" cy="24" rx="7.5" ry="5" />
                  <circle cx="24" cy="24" r="4.5" />
                </g>
              </svg>
            </div>
            <div class="ai-bubble thinking-bubble">
              <span class="thinking-label">正在思考</span>
              <div class="thinking-wave">
                <span v-for="i in 9" :key="i" :style="{ animationDelay: i * 0.08 + 's' }"></span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="composer-wrap">
        <Transition name="mood-fade">
          <div v-if="moodPanel" class="mood-panel">
            <span
              v-for="option in moodOptions"
              :key="option"
              class="mood-chip"
              @click="pickMood(option)"
            >
              {{ option }}
            </span>
          </div>
        </Transition>

          <div class="composer">
            <div class="composer-tools">
              <el-tooltip content="心情" placement="top" :show-after="300">
                <button class="tool-btn" @click="toggleMood">
                  <span>😊</span>
                </button>
              </el-tooltip>
              <el-tooltip content="上传图片" placement="top" :show-after="300">
                <button class="tool-btn" @click="handleTodo">
                  <span>📎</span>
                </button>
              </el-tooltip>
              <el-tooltip :content="listening ? '点击结束录音' : '语音输入'" placement="top" :show-after="300">
                <button class="tool-btn" :class="{ listening }" @click="toggleVoice">
                  <span>🎤</span>
                </button>
              </el-tooltip>
              <el-tooltip content="AI 建议" placement="top" :show-after="300">
                <button class="tool-btn" @click="askAdvice">
                  <span>✨</span>
                </button>
              </el-tooltip>
              <el-tooltip
                :content="'当前模型：' + (modelLabel(currentModel) || currentModel)"
                placement="top"
                :show-after="300"
              >
                <el-dropdown
                  v-model:visible="modelMenuVisible"
                  trigger="click"
                  @command="selectModel"
                  :hide-on-click="true"
                >
                  <button class="tool-btn model-btn">
                    <el-icon class="model-icon"><Cpu /></el-icon>
                  </button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item
                        v-for="(id, label) in availableModels"
                        :key="id"
                        :command="id"
                        :disabled="id === currentModel"
                      >
                        <span class="model-item-label">{{ label }}</span>
                        <el-icon v-if="id === currentModel" class="model-check"><Check /></el-icon>
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </el-tooltip>
          </div>

          <el-input
            v-model="input"
            type="textarea"
            :rows="1"
            resize="none"
            class="composer-input"
            placeholder="今天发生了一件让我焦虑的事情…"
            @keydown="handleKeydown"
          />

          <button
            class="send-btn"
            :disabled="sending || creating"
            title="发送"
            @click="handleSend()"
          >
            <el-icon><Right /></el-icon>
          </button>
        </div>
      </div>

      <!-- 反馈卡片 -->
      <div v-if="messages.some(m => m.role === 'user')" class="feedback-bar">
        <span class="feedback-label">这次对话对你有帮助吗？</span>
        <div class="feedback-btns">
          <button
            class="fb-btn"
            :class="{ active: feedbackGiven === 'helpful', picked: feedbackGiven }"
            @click="giveFeedback('helpful')"
          >
            <span>👍</span> 有帮助
          </button>
          <button
            class="fb-btn"
            :class="{ active: feedbackGiven === 'not-helpful', picked: feedbackGiven }"
            @click="giveFeedback('not-helpful')"
          >
            <span>👎</span> 需改进
          </button>
          <button
            class="fb-btn primary"
            :class="{ active: feedbackGiven === 'great', picked: feedbackGiven }"
            @click="giveFeedback('great')"
          >
            <span>✨</span> 太棒了
          </button>
        </div>
      </div>
    </section>

    <Transition name="drawer">
      <div v-if="historyVisible" class="drawer-mask" @click.self="historyVisible = false">
        <div class="history-drawer">
          <div class="drawer-head">
            <span class="drawer-title">会话历史</span>
            <div class="drawer-head-actions">
              <button class="manage-btn" @click="toggleSelectMode">
                {{ selectMode ? '完成' : '管理' }}
              </button>
              <button class="drawer-close" @click="historyVisible = false">
                <el-icon><Close /></el-icon>
              </button>
            </div>
          </div>

          <button class="new-session-btn" :disabled="creating" @click="startNewSession">
            <el-icon><Plus /></el-icon>
            <span>{{ creating ? '创建中…' : '新建会话' }}</span>
          </button>

          <div class="history-tabs">
            <button
              v-for="tab in historyTabs"
              :key="tab.key"
              class="history-tab"
              :class="{ active: historyTab === tab.key }"
              @click="historyTab = tab.key"
            >
              {{ tab.label }}
              <span class="tab-count">{{ tab.count }}</span>
            </button>
          </div>

          <div v-if="loadingSessions" class="session-loading">
            <el-icon class="is-loading"><Loading /></el-icon>
          </div>
          <p v-else-if="!visibleSessions.length" class="panel-empty">暂无会话记录</p>
          <div v-else class="session-list">
            <div
              v-for="session in visibleSessions"
              :key="session.id"
              class="session-item"
              :class="{
                active: !selectMode && session.id === sessionId,
                selected: selected.has(session.id)
              }"
              @click="selectMode ? toggleSelect(session) : openSession(session)"
            >
              <div
                v-if="selectMode"
                class="session-check"
                :class="{ checked: selected.has(session.id) }"
              >
                <el-icon v-if="selected.has(session.id)"><Check /></el-icon>
              </div>
              <div class="session-body">
                <div class="session-top">
                  <span class="session-emotion">{{ session.emotion || '倾诉' }}</span>
                  <span class="session-status" :class="{ archived: session.status === 2, current: session.id === sessionId }">
                    {{ session.id === sessionId ? '当前会话' : (session.status === 2 ? '已归档' : '历史') }}
                  </span>
                </div>
                <p class="session-preview">{{ session.lastMessage || '开始一段新的倾诉…' }}</p>
                <div class="session-bottom">
                  <span class="session-time">{{ formatSessionTime(session) }}</span>
                  <span class="session-count">{{ session.messageCount || 0 }} 条</span>
                  <div v-if="!selectMode" class="session-actions">
                    <template v-if="session.id !== sessionId">
                      <button
                        v-if="session.status !== 2"
                        class="mini-btn"
                        @click.stop="archiveSessionItem(session)"
                      >
                        归档
                      </button>
                      <button class="mini-btn danger" @click.stop="removeSession(session)">
                        删除
                      </button>
                    </template>
                    <span v-else class="session-current">当前会话</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div v-if="selectMode" class="select-bar">
            <button class="select-all" @click="toggleSelectAll">
              {{ allSelected ? '取消全选' : '全选' }}
            </button>
            <span class="select-count">已选 {{ selected.size }} 项</span>
            <button class="select-delete" :disabled="!selected.size" @click="removeSelected">
              删除
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.workspace {
  display: flex;
  height: 100vh;
  background: #f5f9ff;
  overflow: hidden;
}

/* ===== 左侧情绪分析面板（玻璃拟态，与顶部栏自然衔接） ===== */
.side {
  width: 248px;
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(24px);
  display: flex;
  flex-direction: column;
  padding: 0;
  border-right: none;
  position: relative;
  transition: width 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 折叠态 */
.side.collapsed {
  width: 0;
  overflow: hidden;
  padding: 0;
}

.side-head {
  padding: 12px 14px 10px;
}

.side-head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
}

.side-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
  color: #111827;
  letter-spacing: 1px;
}

.collapse-btn {
  width: 28px;
  height: 28px;
  border-radius: 10px;
  border: 1px solid rgba(226, 232, 240, 0.85);
  background: rgba(255, 255, 255, 0.6);
  color: #94a3b8;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.25s;
  flex-shrink: 0;
}

.collapse-btn:hover {
  color: #6366f1;
  border-color: #a5b4fc;
  background: rgba(99, 102, 241, 0.08);
  transform: scale(1.05);
}

.side-sub {
  padding-left: 23px;
  margin-top: 2px;
  font-size: 10px;
  color: #94a3b8;
  letter-spacing: 1.5px;
  text-transform: uppercase;
}

.pulse-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: linear-gradient(135deg, #22d3ee, #4d7cff);
  box-shadow: 0 0 0 0 rgba(56, 130, 246, 0.55);
  animation: pulse 1.8s ease-out infinite;
}

@keyframes pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(56, 130, 246, 0.55);
  }
  70% {
    box-shadow: 0 0 0 8px rgba(56, 130, 246, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(56, 130, 246, 0);
  }
}

.side-body {
  flex: 1;
  min-height: 0;
  padding: 14px 16px 16px;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: rgba(99,102,241,0.3) transparent;
}
.side-body::-webkit-scrollbar { width: 6px; }
.side-body::-webkit-scrollbar-thumb {
  background: rgba(99,102,241,0.3);
  border-radius: 999px;
}
.side-body::-webkit-scrollbar-thumb:hover { background: rgba(99,102,241,0.5); }
.side-body::-webkit-scrollbar-track { background: transparent; }

/* 空状态：脑波 */
.analysis-idle {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 18px 4px 22px;
}

.wave-svg {
  width: 132px;
  filter: drop-shadow(0 2px 6px rgba(96, 165, 250, 0.35));
}

.wave-svg path {
  stroke-dasharray: 260;
  animation: waveDash 4.5s ease-in-out infinite alternate;
}

@keyframes waveDash {
  from {
    stroke-dashoffset: 0;
  }
  to {
    stroke-dashoffset: -130;
  }
}

.idle-text {
  margin: 0;
  font-size: 12px;
  line-height: 1.9;
  text-align: center;
  color: #94a3b8;
  letter-spacing: 1px;
}

/* 分析中：雷达扫描 */
.analysis-scan {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 18px;
  padding: 8px 4px 20px;
}

.scan-wrap {
  position: relative;
  width: 132px;
  height: 132px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.scan-ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 1px solid rgba(96, 165, 250, 0.45);
  box-shadow:
    inset 0 0 18px rgba(96, 165, 250, 0.15),
    0 0 18px rgba(96, 165, 250, 0.1);
  animation: scanRing 2.4s ease-in-out infinite;
}

.scan-ring::before,
.scan-ring::after {
  content: '';
  position: absolute;
  border-radius: 50%;
}

.scan-ring::before {
  inset: 12px;
  border: 1px dashed rgba(96, 165, 250, 0.55);
  animation: ringSpin 9s linear infinite;
}

.scan-ring::after {
  inset: -14px;
  border: 1px solid rgba(148, 163, 184, 0.18);
}

@keyframes scanRing {
  0%,
  100% {
    transform: scale(0.92);
    opacity: 0.6;
  }
  50% {
    transform: scale(1.06);
    opacity: 1;
  }
}

@keyframes ringSpin {
  to {
    transform: rotate(360deg);
  }
}

.scan-line {
  position: absolute;
  left: 10%;
  right: 10%;
  height: 2px;
  border-radius: 2px;
  background: linear-gradient(90deg, transparent, #4d7cff, transparent);
  box-shadow: 0 0 10px rgba(77, 124, 255, 0.7);
  animation: scanLine 1.8s ease-in-out infinite;
}

@keyframes scanLine {
  0% {
    top: 16%;
    opacity: 0;
  }
  15% {
    opacity: 1;
  }
  85% {
    opacity: 1;
  }
  100% {
    top: 84%;
    opacity: 0;
  }
}

.scan-core {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: linear-gradient(135deg, #4d7cff, #60a5fa);
  box-shadow:
    0 0 12px rgba(77, 124, 255, 0.7),
    0 0 26px rgba(96, 165, 250, 0.45);
  animation: coreBreath 1.6s ease-in-out infinite;
}

@keyframes coreBreath {
  0%,
  100% {
    transform: scale(1);
    opacity: 0.85;
  }
  50% {
    transform: scale(1.35);
    opacity: 1;
  }
}

.scan-text {
  margin: 0;
  font-size: 12px;
  letter-spacing: 2px;
  color: #64748b;
  animation: blinkSoft 1.2s ease-in-out infinite;
}

@keyframes blinkSoft {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.55;
  }
}

/* 分析结果 */
.analysis-result {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 14px;
  animation: resultIn 0.5s ease both;
}

@keyframes resultIn {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.rings-stack {
  display: flex;
  flex-direction: column;
  gap: 14px;
  align-items: center;
}

.ring-card {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 14px 10px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.88);
  box-shadow:
    0 8px 24px rgba(59, 130, 246, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.75);
}

/* 主导情绪 + 健康度 */
.emotion-chip {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 14px 16px;
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(96, 165, 250, 0.14), rgba(52, 211, 153, 0.1));
  border: 1px solid rgba(96, 165, 250, 0.25);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.emotion-icon {
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.9);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  box-shadow: 0 6px 16px rgba(59, 130, 246, 0.14);
}

.emotion-meta {
  flex: 1;
  min-width: 0;
}

.emotion-name {
  font-size: 13px;
  font-weight: 600;
  color: #111827;
  margin-bottom: 6px;
}

.emotion-bar {
  height: 6px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.2);
  overflow: hidden;
}

.emotion-bar span {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #34d399, #4d7cff);
  transition: width 0.8s ease;
}

.emotion-score {
  font-size: 17px;
  font-weight: 800;
  color: #0f766e;
}

/* 星制评分面板（与后台格式一致） */
.star-board {
  display: flex;
  flex-direction: column;
  gap: 9px;
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.65);
  border: 1px solid rgba(255, 255, 255, 0.9);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.star-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11.5px;
  color: #475569;
}

.star-label {
  width: 56px;
  flex-shrink: 0;
  color: #64748b;
}

.star-row :deep(.el-rate) {
  flex: 1;
  min-width: 0;
}

.star-row :deep(.el-rate__icon) {
  font-size: 14px;
}

.star-val {
  font-size: 10.5px;
  font-weight: 600;
  color: #94a3b8;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

/* 建议列表 */
.analysis-suggestions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.suggestion-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 11.5px;
  line-height: 1.7;
  color: #475569;
  padding: 9px 12px;
  border-radius: 14px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.6);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.suggestion-item .el-icon {
  flex-shrink: 0;
  margin-top: 3px;
  color: #4d7cff;
  font-size: 13px;
}

/* ===== AI 分析反馈卡片 ===== */
.feedback-card {
  margin-top: 4px;
  padding: 14px 16px;
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.07), rgba(99, 102, 241, 0.05));
  border: 1px solid rgba(99, 102, 241, 0.18);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.55),
    0 6px 18px rgba(99, 102, 241, 0.06);
}

.fb-card-head {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 8px;
  font-size: 11.5px;
  font-weight: 700;
  color: #6366f1;
  letter-spacing: 1px;
}

.fb-card-head .el-icon {
  font-size: 15px;
}

.fb-card-body {
  margin: 0;
  font-size: 12px;
  line-height: 1.75;
  color: #475569;
}

.fb-card-foot {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 10px;
  color: #94a3b8;
}

.fb-card-foot .el-icon {
  font-size: 12px;
}

.side-foot {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px 16px 14px;
}

.foot-label {
  font-size: 10px;
  letter-spacing: 3px;
  color: #94a3b8;
}

.foot-time {
  font-size: 10px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
}

/* ===== 主区域：浅蓝渐变 + 玻璃光斑 + 噪点 ===== */
.main {
  position: relative;
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background:
    radial-gradient(440px 320px at 12% 16%, rgba(96, 165, 250, 0.14), transparent 66%),
    radial-gradient(380px 300px at 90% 26%, rgba(96, 165, 250, 0.12), transparent 66%),
    radial-gradient(480px 360px at 80% 84%, rgba(96, 165, 250, 0.1), transparent 66%),
    radial-gradient(340px 280px at 6% 80%, rgba(96, 165, 250, 0.1), transparent 66%),
    linear-gradient(180deg, #f8fbff 0%, #eef5ff 55%, #ffffff 100%);
}

.main::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  opacity: 0.025;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='2' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
}

/* ===== 顶部栏（透明悬浮 Pill 统一风格） ===== */
.chat-header {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 14px 22px 0;
  padding: 10px 20px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(20px) saturate(1.6);
  box-shadow:
    0 10px 34px rgba(47, 111, 219, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.85);
}

.ai-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ai-logo {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: linear-gradient(135deg, #60a5fa 0%, #3b82f6 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow:
    0 10px 26px rgba(59, 130, 246, 0.32),
    inset 0 1px 0 rgba(255, 255, 255, 0.35);
}

.ai-name {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}

.ai-sub {
  font-size: 12px;
  color: #8a93a1;
  margin-top: 2px;
}

.ai-sub.thinking span {
  background: linear-gradient(90deg, #60a5fa, #3b82f6, #60a5fa);
  background-size: 200% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  animation: shimmer 1.5s linear infinite;
}

@keyframes shimmer {
  to {
    background-position: -200% 0;
  }
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 16px;
  border-radius: 999px;
  border: 1px solid rgba(226, 232, 240, 0.85);
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(14px);
  font-size: 13px;
  color: #475569;
  cursor: pointer;
  transition: all 0.2s;
}

.status-chip:hover {
  border-color: #3b82f6;
  color: #3b82f6;
}

.status-emoji {
  font-size: 15px;
}

.icon-btn {
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

.icon-btn:hover {
  color: #3b82f6;
  border-color: #3b82f6;
  transform: translateY(-1px);
}

.garden-icon:hover {
  color: #10b981;
  border-color: #34d399;
}

/* ===== 聊天区：居中窄列 + 大量留白 ===== */
.chat-scroll {
  position: relative;
  z-index: 1;
  flex: 1;
  overflow-y: auto;
  padding: 30px 24px 14px;
}

.chat-column {
  max-width: 760px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.chat-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.chat-row.user {
  justify-content: flex-end;
}

.chat-row.assistant {
  animation: aiIn 0.5s ease both;
}

@keyframes aiIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.chat-row.user {
  animation: userIn 0.3s ease both;
}

@keyframes userIn {
  from {
    opacity: 0;
    transform: scale(0.95);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.bubble-avatar {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  flex-shrink: 0;
  background: linear-gradient(135deg, #60a5fa 0%, #3b82f6 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 20px rgba(59, 130, 246, 0.3);
}

.bubble-avatar.breathe {
  animation: breathe 1.6s ease-in-out infinite;
}

@keyframes breathe {
  0%,
  100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.05);
  }
}

.ai-bubble {
  max-width: 78%;
  padding: 18px 22px;
  border-radius: 28px;
  border-bottom-left-radius: 8px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.28) 0%, rgba(255, 255, 255, 0.12) 100%);
  backdrop-filter: blur(30px) saturate(1.5);
  -webkit-backdrop-filter: blur(30px) saturate(1.5);
  border: 1px solid rgba(255, 255, 255, 0.45);
  box-shadow:
    0 20px 60px rgba(52, 104, 255, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.5);
  color: #111827;
}

.user-bubble {
  max-width: 70%;
  padding: 14px 20px;
  border-radius: 24px;
  border-bottom-right-radius: 8px;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.9);
  box-shadow:
    0 12px 36px rgba(59, 130, 246, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.95);
  color: #111827;
}

.bubble-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 8px;
}

.bubble-name {
  font-size: 12px;
  font-weight: 600;
  color: #3b82f6;
}

.bubble-time {
  font-size: 11px;
  color: #94a3b8;
}

.chat-content {
  font-size: 14px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

/* AI 回复中的玻璃卡片 */
.ai-card {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.42);
  border: 1px solid rgba(255, 255, 255, 0.7);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.65);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 8px;
}

.card-emoji {
  font-size: 16px;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}

.card-bar {
  margin-top: 10px;
  height: 6px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.12);
  overflow: hidden;
}

.card-bar-fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #60a5fa, #3b82f6);
}

.card-percent {
  margin-top: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #3b82f6;
}

.card-duration {
  margin-top: 6px;
  font-size: 12px;
  color: #64748b;
}

/* 正在思考：声波动效，不用三点 */
.thinking-bubble {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 15px 20px;
  border-radius: 22px;
}

.thinking-label {
  font-size: 13px;
  color: #64748b;
}

.thinking-wave {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  height: 20px;
}

.thinking-wave span {
  width: 3px;
  border-radius: 2px;
  background: linear-gradient(180deg, #60a5fa, #3b82f6);
  animation: wave 1.1s ease-in-out infinite;
}

/* AI 气泡内联的思考状态（首字到达前） */
.thinking-inline {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 6px 0;
}

/* 流式输出的打字光标 */
.stream-cursor {
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 2px;
  vertical-align: -2px;
  background: #3b82f6;
  border-radius: 1px;
  animation: cursorBlink 0.9s steps(2) infinite;
}

@keyframes cursorBlink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0;
  }
}

@keyframes wave {
  0%,
  100% {
    height: 6px;
    opacity: 0.5;
  }
  50% {
    height: 18px;
    opacity: 1;
  }
}

/* ===== 输入区：玻璃胶囊 ===== */
.composer-wrap {
  position: relative;
  z-index: 2;
  padding: 14px 22px 22px;
}

.composer {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  max-width: 820px;
  margin: 0 auto;
  min-height: 72px;
  padding: 10px 12px 10px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(20px) saturate(1.6);
  -webkit-backdrop-filter: blur(20px) saturate(1.6);
  border: 1px solid rgba(255, 255, 255, 0.72);
  box-shadow:
    0 10px 34px rgba(47, 111, 219, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.85);
  transition: border-color 0.25s, box-shadow 0.25s;
}

.composer:focus-within {
  border-color: #4d7cff;
  box-shadow:
    0 0 30px rgba(84, 128, 255, 0.25),
    0 12px 38px rgba(59, 130, 246, 0.12);
}

.composer-tools {
  display: flex;
  align-items: center;
  gap: 4px;
}

.tool-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 1px solid rgba(226, 232, 240, 0.85);
  background: rgba(255, 255, 255, 0.6);
  font-size: 17px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.tool-btn:hover {
  transform: scale(1.08);
  box-shadow: 0 6px 16px rgba(59, 130, 246, 0.18);
}

/* 录音中状态：红色脉冲 */
.tool-btn.listening {
  border-color: #f43f5e;
  background: rgba(244, 63, 94, 0.12);
  animation: micPulse 1.3s ease-in-out infinite;
}

@keyframes micPulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(244, 63, 94, 0.35);
  }
  50% {
    box-shadow: 0 0 0 8px rgba(244, 63, 94, 0);
  }
}

/* 模型切换按钮：Cpu 图标 + 蓝色渐变，风格跟其他工具按钮一致 */
.model-btn {
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.16), rgba(59, 130, 246, 0.08)) !important;
  border-color: rgba(59, 130, 246, 0.35) !important;
}

.model-icon {
  font-size: 17px;
  color: #3b82f6;
}
.model-item-label {
  font-size: 13px;
}
.model-check {
  margin-left: 8px;
  color: #4f46e5;
}

.composer-input {
  flex: 1;
  min-width: 0;
}

.composer-input :deep(.el-textarea__inner) {
  border: none;
  background: transparent;
  box-shadow: none !important;
  font-size: 14px;
  color: #111827;
  padding: 12px 4px;
}

.composer-input :deep(.el-textarea__inner::placeholder) {
  color: #94a3b8;
}

.send-btn {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  border: none;
  flex-shrink: 0;
  background: linear-gradient(135deg, #60a5fa 0%, #3b82f6 100%);
  color: #ffffff;
  font-size: 17px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 8px 22px rgba(59, 130, 246, 0.35);
  transition: transform 0.2s, box-shadow 0.2s, opacity 0.2s;
}

.send-btn:hover:not(:disabled) {
  transform: scale(1.06);
  box-shadow: 0 12px 28px rgba(59, 130, 246, 0.42);
}

.send-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* 心情选择面板 */
.mood-panel {
  position: absolute;
  left: 50%;
  bottom: calc(100% - 6px);
  transform: translateX(-50%);
  display: flex;
  gap: 8px;
  padding: 12px 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(26px);
  -webkit-backdrop-filter: blur(26px);
  border: 1px solid rgba(255, 255, 255, 0.9);
  box-shadow: 0 20px 50px rgba(59, 130, 246, 0.16);
}

.mood-chip {
  padding: 7px 15px;
  border-radius: 999px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  font-size: 13px;
  color: #475569;
  cursor: pointer;
  transition: all 0.2s;
}

.mood-chip:hover {
  border-color: #3b82f6;
  color: #3b82f6;
  transform: translateY(-1px);
}

.mood-fade-enter-active,
.mood-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.mood-fade-enter-from,
.mood-fade-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(6px);
}

/* ===== 反馈卡片 ===== */
.feedback-bar {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 10px 24px 18px;
}

.feedback-label {
  font-size: 12.5px;
  color: #94a3b8;
  white-space: nowrap;
}

.feedback-btns {
  display: flex;
  gap: 8px;
}

.fb-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 7px 16px;
  border-radius: 999px;
  border: 1px solid rgba(226, 232, 240, 0.95);
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(14px);
  font-size: 12.5px;
  color: #64748b;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.fb-btn:hover {
  border-color: #93c5fd;
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.06);
  transform: translateY(-1px);
}

.fb-btn.primary {
  border-color: rgba(251, 191, 36, 0.4);
  color: #d97706;
}

.fb-btn.primary:hover {
  border-color: #fbbf24;
  background: rgba(251, 191, 36, 0.08);
  color: #b45309;
}

.fb-btn.active {
  transform: scale(0.96);
}

.fb-btn.picked {
  opacity: 0.65;
  pointer-events: none;
}

/* ===== 会话历史抽屉 ===== */
.drawer-mask {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  justify-content: flex-end;
  background: rgba(15, 23, 42, 0.18);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}

.history-drawer {
  width: 310px;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 22px 18px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(30px) saturate(1.5);
  -webkit-backdrop-filter: blur(30px) saturate(1.5);
  border-left: 1px solid rgba(255, 255, 255, 0.85);
  box-shadow: -24px 0 70px rgba(59, 130, 246, 0.14);
}

.drawer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.drawer-title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}

.drawer-close {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.8);
  background: rgba(255, 255, 255, 0.5);
  color: #64748b;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
}

.drawer-close:hover {
  color: #3b82f6;
}

.drawer-head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.manage-btn {
  padding: 6px 13px;
  border: 1px solid rgba(59, 130, 246, 0.3);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.7);
  font-size: 12px;
  color: #3b82f6;
  cursor: pointer;
  transition: all 0.2s;
}

.manage-btn:hover {
  background: #3b82f6;
  color: #ffffff;
}

.new-session-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  width: 100%;
  height: 42px;
  margin-bottom: 14px;
  border: none;
  border-radius: 14px;
  background: linear-gradient(135deg, #60a5fa 0%, #3b82f6 100%);
  color: #ffffff;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 2px;
  cursor: pointer;
  box-shadow: 0 10px 26px rgba(59, 130, 246, 0.32);
  transition: transform 0.2s, box-shadow 0.2s, opacity 0.2s;
}

.new-session-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 14px 32px rgba(59, 130, 246, 0.4);
}

.new-session-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.history-tabs {
  display: flex;
  gap: 6px;
  padding: 4px;
  margin-bottom: 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.55);
  border: 1px solid rgba(255, 255, 255, 0.8);
}

.history-tab {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 7px 0;
  border: none;
  border-radius: 9px;
  background: transparent;
  font-size: 12px;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s;
}

.history-tab:hover {
  color: #3b82f6;
}

.history-tab.active {
  background: #ffffff;
  color: #3b82f6;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.12);
}

.tab-count {
  font-size: 11px;
  opacity: 0.75;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.session-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 11px 13px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.7);
  cursor: pointer;
  transition: all 0.2s;
}

.session-item:hover {
  border-color: rgba(59, 130, 246, 0.4);
}

.session-item.active {
  border-color: #3b82f6;
  background: rgba(59, 130, 246, 0.07);
}

.session-item.selected {
  border-color: #3b82f6;
  background: rgba(59, 130, 246, 0.08);
}

.session-body {
  flex: 1;
  min-width: 0;
}

.session-check {
  width: 18px;
  height: 18px;
  margin-top: 2px;
  flex-shrink: 0;
  border-radius: 50%;
  border: 1.5px solid #cbd5e1;
  background: #ffffff;
  color: #ffffff;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.session-check.checked {
  background: #3b82f6;
  border-color: #3b82f6;
}

.select-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid rgba(226, 232, 240, 0.9);
}

.select-all {
  padding: 5px 12px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.7);
  font-size: 12px;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s;
}

.select-all:hover {
  border-color: #3b82f6;
  color: #3b82f6;
}

.select-count {
  flex: 1;
  font-size: 12px;
  color: #64748b;
}

.select-delete {
  padding: 6px 16px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, #fb7185, #f43f5e);
  color: #ffffff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 8px 20px rgba(244, 63, 94, 0.3);
  transition: all 0.2s;
}

.select-delete:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 12px 26px rgba(244, 63, 94, 0.38);
}

.select-delete:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.session-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.session-emotion {
  flex-shrink: 0;
  padding: 2px 9px;
  border-radius: 999px;
  font-size: 11px;
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.09);
}

.session-status {
  flex-shrink: 0;
  font-size: 11px;
  color: #3b82f6;
}

.session-status.archived {
  color: #94a3b8;
}

.session-status.current {
  color: #6366f1;
  font-weight: 600;
}

.session-preview {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: #64748b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-bottom {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.session-time {
  font-size: 11px;
  color: #94a3b8;
}

.session-count {
  font-size: 11px;
  color: #94a3b8;
}

.session-actions {
  margin-left: auto;
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}

.session-item:hover .session-actions {
  opacity: 1;
}

.mini-btn {
  padding: 3px 9px;
  border: 1px solid rgba(59, 130, 246, 0.3);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.7);
  font-size: 11px;
  color: #3b82f6;
  cursor: pointer;
  transition: all 0.2s;
}

.mini-btn:hover {
  background: #3b82f6;
  color: #ffffff;
}

.mini-btn.danger {
  border-color: rgba(244, 63, 94, 0.3);
  color: #f43f5e;
}

.mini-btn.danger:hover {
  background: #f43f5e;
  color: #ffffff;
}

.session-current {
  font-size: 11px;
  color: #3b82f6;
  font-weight: 600;
  padding: 2px 6px;
}

.session-loading {
  display: flex;
  justify-content: center;
  padding: 26px 0;
  font-size: 18px;
  color: #3b82f6;
}

.panel-empty {
  margin: 0;
  font-size: 12px;
  color: #94a3b8;
  text-align: center;
  padding: 24px 0;
}

.drawer-enter-active,
.drawer-leave-active {
  transition: opacity 0.25s ease;
}

.drawer-enter-active .history-drawer,
.drawer-leave-active .history-drawer {
  transition: transform 0.25s ease;
}

.drawer-enter-from,
.drawer-leave-to {
  opacity: 0;
}

.drawer-enter-from .history-drawer,
.drawer-leave-to .history-drawer {
  transform: translateX(100%);
}


/* ===== 响应式 ===== */
@media (max-width: 1100px) {
  .side:not(.collapsed) {
    width: 216px;
  }

  .chat-column {
    max-width: 620px;
  }
}

@media (max-width: 860px) {
  .side:not(.collapsed) {
    width: 56px;
  }

  .side:not(.collapsed) .side-sub,
  .side:not(.collapsed) .side-body,
  .side:not(.collapsed) .side-foot,
  .side:not(.collapsed) .side-actions,
  .side:not(.collapsed) .pulse-dot + span {
    display: none;
  }

  .side:not(.collapsed) .side-head {
    justify-content: center;
    padding: 18px 0;
  }

  .side.collapsed {
    width: 0;
  }

  .feedback-bar {
    flex-direction: column;
    gap: 8px;
  }

  .chat-header {
    padding: 12px 16px;
  }

  .status-chip {
    padding: 8px 12px;
  }

  .chat-scroll {
    padding: 22px 16px 10px;
  }

  .composer-wrap {
    padding: 12px 14px 16px;
  }

  .tool-btn {
    width: 36px;
    height: 36px;
  }
}
/* iPhone 窄屏：压缩顶栏与输入栏，UI 不变仅防错乱 */
@media (max-width: 520px) {
  .workspace {
    height: 100dvh;
  }

  .chat-header {
    margin: 8px 10px 0;
    padding: 8px 12px;
    gap: 8px;
  }

  .ai-logo {
    width: 36px;
    height: 36px;
    border-radius: 12px;
  }

  .ai-name {
    font-size: 13px;
  }

  .ai-sub {
    font-size: 11px;
  }

  .icon-btn {
    width: 34px;
    height: 34px;
    border-radius: 10px;
    font-size: 15px;
  }

  .header-actions {
    gap: 6px;
  }

  .status-chip {
    padding: 6px 10px;
    font-size: 12px;
  }

  .hide-sm {
    display: none !important;
  }

  .chat-scroll {
    padding: 18px 12px 8px;
  }

  .ai-bubble {
    max-width: 85%;
    padding: 14px 16px;
    border-radius: 22px;
  }

  .user-bubble {
    max-width: 82%;
    padding: 11px 16px;
  }

  .composer-wrap {
    padding: 8px 10px 12px;
  }

  .composer {
    min-height: 62px;
    padding: 8px 10px 8px 12px;
    gap: 6px;
  }

  .composer-tools {
    gap: 2px;
  }

  .tool-btn {
    width: 36px;
    height: 36px;
    font-size: 15px;
  }

  .model-icon {
    font-size: 15px;
  }

  .send-btn {
    width: 40px;
    height: 40px;
    font-size: 15px;
  }

  .composer-input :deep(.el-textarea__inner) {
    font-size: 13px;
    padding: 10px 2px;
  }

  .chat-tip {
    font-size: 10px;
  }

  .mood-panel {
    flex-wrap: wrap;
    justify-content: center;
    max-width: calc(100vw - 24px);
  }
}
</style>
