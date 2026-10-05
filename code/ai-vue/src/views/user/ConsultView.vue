<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useEmotionStore } from '@/stores/emotion'
import { logout as logoutApi } from '@/api/auth'
import { renderChatMarkdown } from '@/utils/chatMarkdown'
import { visibleChatMessageContent } from '@/utils/chatActions'
import {
  createSession,
  getMySessions,
  getMessageList,
  archiveSession,
  restoreSession,
  deleteSession,
  streamChatMessage,
  analyzeEmotion,
  getAvailableModels,
  summarizeSession
} from '@/api/consult'
import AppNavBar from '@/components/AppNavBar.vue'
import UserDropdown from '@/components/UserDropdown.vue'
import { BookOpen, FileText, Languages, MessageCircle, Mic, Paperclip, SmilePlus, Sparkles as SparklesIcon, X } from 'lucide-vue-next'
import { USER_NAV_ACTIONS } from '@/constants/userNavigation'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const displayName = computed(() => authStore.userInfo?.nickname || '用户')
const roleText = computed(() => (authStore.userInfo?.role === 'admin' ? '管理员' : '普通用户'))
const emotionStore = useEmotionStore()

const sessionId = ref(null)
const messages = ref([])
const input = ref('')
const referenceArticleId = ref(null)
const referenceArticleTitle = computed(() => {
  const title = route.query.articleTitle
  return Array.isArray(title) ? title[0] : (title || '已选文章')
})
const referenceArticleLanguage = computed(() => {
  const language = Array.isArray(route.query.articleLanguage)
    ? route.query.articleLanguage[0]
    : route.query.articleLanguage
  if (language === 'en') return 'en'
  if (language === 'zh') return 'zh'
  return /[A-Za-z]{3}/.test(referenceArticleTitle.value) && !/[\u3400-\u9fff]/.test(referenceArticleTitle.value)
    ? 'en'
    : 'zh'
})
const referenceArticleIsExcerpt = computed(() => {
  const value = Array.isArray(route.query.articleExcerpt) ? route.query.articleExcerpt[0] : route.query.articleExcerpt
  return value === '1'
})
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
const summaryVisible = ref(false)
const summaryLoading = ref(false)
const summaryContent = ref('')
const summaryError = ref('')
const historyTab = ref('all')
const selectMode = ref(false)
const selected = ref(new Set())

const moodPanel = ref(false)
const listRef = ref()
const pageRef = ref(null)
const composerInputRef = ref(null)
let motionContext
const showWelcomeStage = computed(() => messages.value.length > 0 && !messages.value.some((message) => message.role === 'user'))
const openingPrompts = ['最近有点压力，想说说', '今天心情有些复杂', '我想先整理一下思绪']
function chooseOpening(text) {
  input.value = text
  nextTick(() => composerInputRef.value?.focus())
}

function chatSegments(content) {
  const text = String(content || '')
  const marker = /\[\[mindman-article:(\d+)\]\]/g
  const segments = []
  let cursor = 0
  let match
  while ((match = marker.exec(text))) {
    if (match.index > cursor) segments.push({ type: 'text', value: text.slice(cursor, match.index) })
    segments.push({ type: 'article', id: match[1] })
    cursor = marker.lastIndex
  }
  if (cursor < text.length) segments.push({ type: 'text', value: text.slice(cursor) })
  return segments
}

function clearArticleReference() {
  referenceArticleId.value = null
  const query = { ...route.query }
  delete query.articleId
  delete query.articleTitle
  delete query.articleLanguage
  delete query.articleExcerpt
  router.replace({ path: route.path, query })
}

function analyzeAndTranslateArticle() {
  if (!referenceArticleId.value) return
  const title = `《${referenceArticleTitle.value}》`
  const prompt = referenceArticleLanguage.value === 'en'
    ? `请只依据本轮带入的文章${title}翻译并解读，不要根据标题补写正文。使用简体中文，按以下格式简洁回复：\n\n## 译文\n按原文顺序忠实翻译 MindMan 实际收录的内容，保留专名、数字、引语和段落层次，不增加原文没有的观点。遇到双关、文字游戏或难直译的说法，保留英文原词并用一句话解释；不要把修辞误译成心理或医学术语。\n\n## 文章要点\n最多列 3 点，只总结这段原文明确表达的内容；材料不足以判断全文时直接说明。\n\n## 谨慎解读\n最多 2 句，区分“文章写明”与“可以理解为”。不要推测公众人物、粉丝或读者的具体心理状态，不作诊断。涉及死亡、疾病或其他时效性事实时，只表述为“文章称”，不声称已独立核实。若本站保存的是摘要或片段，开头注明翻译范围仅限当前收录内容。不要加通用安慰或反问。`
    : `请只依据本轮带入的文章${title}，用简体中文简洁回复：列出最多 3 个文章明确表达的要点，再用最多 2 句区分文章观点与谨慎解读。不要从标题补写正文，不推测文中人物或读者的心理状态，不把修辞当成诊断，也不要加通用安慰或反问。`
  handleSend(prompt, {
    displayText: `翻译并解读文章：《${referenceArticleTitle.value}》`,
    skipEmotionAnalysis: true,
    articleTranslation: true
  })
}

function discussReferencedArticle() {
  if (!referenceArticleId.value) return
  handleSend(
    `请结合文章《${referenceArticleTitle.value}》和我接下来分享的情况，陪我梳理它与现实生活的联系。先简要说明文章中相关的观点，再问我一个具体、开放的问题；请区分文章内容和你的推测，不作诊断。`,
    { displayText: `结合文章聊聊：《${referenceArticleTitle.value}》`, skipEmotionAnalysis: true }
  )
}

// ===== AI 情绪分析 =====
const analysisVisible = ref(true)
const analyzing = ref(false)
const emotion = ref(null)
const lastAnalysisTime = ref('')
// 默认折叠，若系统设置开启「自动展开情绪分析」则展开
const sideCollapsed = ref(localStorage.getItem('mha_analysis_auto_open') === '1' ? false : true)

// 分析反馈文案
const analysisSummary = computed(() => {
  return emotion.value?.interpretation || ''
})

const WELCOME =
  '你好，我是你的 AI 倾听助手。今天有什么让你挂心的事？不必想好怎么说，从一句话开始就好。'

const moodOptions = ['今天有件开心的事', '我有点累', '我有些担心', '说不上来是什么感觉']

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
    if (data.emotion && data.emotion !== '暂不判断') {
      emotionStore.setLatest({
        emotion: data.emotion,
        evidence: data.evidence || '',
        analysisSource: data.analysisSource || 'rules',
        analyzedAt: data.analyzedAt
      })
    } else {
      emotionStore.clear()
    }
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
    const rows = Array.isArray(data) ? data : (data?.list || [])
    sessions.value = rows.map((session) => ({
      ...session,
      title: visibleChatMessageContent('user', session.title),
      lastMessage: visibleChatMessageContent('user', session.lastMessage || session.lastMessagePreview || ''),
      lastTime: session.lastTime || session.updatedAt || session.createdAt
    }))
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
    await loadSessions()
    return true
  } catch (e) {
    if (!e?.handled) ElMessage.error(e.message || '创建会话失败，请稍后重试')
    return false
  } finally {
    creating.value = false
  }
}

// 新建会话：先收好所有进行中的会话，再创建新的空白会话
async function startNewSession() {
  const activeList = sessions.value.filter((s) => s.status !== 2)
  for (const s of activeList) {
    try {
      await archiveSession(s.id)
      s.status = 2
      s.statusText = '已归档'
    } catch (e) {
      if (!e?.handled) ElMessage.error(e.message || '归档旧会话失败，请稍后重试')
      return
    }
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
    session.statusText = '已归档'
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
  if (session.id === sessionId.value && messages.value.length && session.status !== 2) return
  loadingMessages.value = true
  try {
    const current = sessions.value.find((item) => item.id === sessionId.value)
    if (current && current.id !== session.id && current.status !== 2) {
      await archiveSession(current.id)
      current.status = 2
      current.statusText = '已归档'
    }
    if (session.status === 2) {
      await restoreSession(session.id)
      session.status = 1
      session.statusText = '进行中'
      session.endedAt = null
    }
    const list = await getMessageList(session.id)
    messages.value = (list || []).map((m) => ({
      role: m.role,
      content: visibleChatMessageContent(m.role, m.content),
      time: (m.createdAt || '').slice(11, 19),
      cards: m.cards || [],
      deliveryStatus: m.deliveryStatus || 'complete'
    }))
    sessionId.value = session.id
    creating.value = false
    historyVisible.value = false
    summaryContent.value = ''
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
async function handleSend(text, options = {}) {
  const content = (text ?? input.value).trim()
  const displayContent = String(options.displayText ?? content).trim()
  if (!content || sending.value) return
  if (!sessionId.value) {
    ElMessage.warning('会话创建中，请稍候')
    return
  }
  pushMessage('user', displayContent || content)
  input.value = ''
  sending.value = true
  summaryContent.value = ''

  // 用户倾诉后自动触发 AI 情绪分析
  if (!options.skipEmotionAnalysis) runAnalysis(content)

  // 先插入一条空的 AI 消息，等待首字到达后开始流式输出
  const reply = { role: 'assistant', content: '', time: nowTime(), cards: [], streaming: true }
  messages.value.push(reply)
  scrollToBottom()
  try {
    for await (const chunk of streamChatMessage(sessionId.value, content, currentModel.value, {
      includeGardenContext: !!options.includeGardenContext,
      referenceArticleId: referenceArticleId.value,
      articleTranslationMode: !!options.articleTranslation,
      displayContent: displayContent !== content ? displayContent : undefined
    })) {
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
    if (idx > -1 && reply.content) {
      reply.streaming = false
      reply.deliveryStatus = 'interrupted'
    }
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
  moodPanel.value = false
  input.value = input.value.trim()
    ? `${input.value.trimEnd()}\n${option}`
    : option
  nextTick(() => composerInputRef.value?.focus())
}

function handleTodo() {
  ElMessage.info('功能开发中，敬请期待')
}

function askAdvice() {
  handleSend('请结合我刚才分享的内容，给我一两个温和、可尝试的小建议。', {
    displayText: '根据刚才的内容给我一些建议',
    skipEmotionAnalysis: true
  })
}

function chooseAiAction(action) {
  if (action === 'article') {
    handleSend('按我们这段对话里我提到的主题，从 MindMan 心理阅读里找一篇已发布文章给我。', {
      displayText: '按刚才聊到的主题找一篇文章',
      skipEmotionAnalysis: true
    })
    return
  }
  if (action === 'garden') {
    handleSend(
      '请结合我近30天的情绪花园记录，温和回顾出现较多的情绪、评分和可能的情境联系。请区分记录事实与推测，不做诊断，并给我一个值得继续觉察的问题。',
      { displayText: '回顾近30天的情绪花园', includeGardenContext: true, skipEmotionAnalysis: true }
    )
    return
  }
  askAdvice()
}

async function createConversationSummary() {
  if (!sessionId.value || summaryLoading.value) return
  if (sending.value) {
    ElMessage.info('等这条回复完成后，就可以生成总结了')
    return
  }
  if (!messages.value.some((message) => message.role === 'user')) {
    ElMessage.info('先聊几句，再生成本次对话总结')
    return
  }
  summaryVisible.value = true
  summaryLoading.value = true
  summaryContent.value = ''
  summaryError.value = ''
  try {
    summaryContent.value = await summarizeSession(sessionId.value)
  } catch (error) {
    summaryError.value = error.message || 'AI 总结暂时不可用，请稍后重试'
  } finally {
    summaryLoading.value = false
  }
}

async function copySummary() {
  if (!summaryContent.value) return
  try {
    await navigator.clipboard.writeText(summaryContent.value)
    ElMessage.success('总结已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择总结内容')
  }
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
  const articleId = String(route.query.articleId || '')
  referenceArticleId.value = /^\d+$/.test(articleId) ? Number(articleId) : null
  const shouldReviewGarden = route.query.gardenReview === '1'
  if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    import('gsap').then(({ gsap }) => {
      if (!pageRef.value) return
      motionContext = gsap.context(() => {
        gsap.from('.consult-context-bar', { y: -18, autoAlpha: 0, duration: 0.7, ease: 'power2.out', clearProps: 'all' })
        gsap.from('.composer-wrap', { y: 24, autoAlpha: 0, duration: 0.75, delay: 0.15, ease: 'power2.out', clearProps: 'all' })
      }, pageRef.value)
    })
  }
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
  const target = targetId ? sessions.value.find((s) => s.id === targetId) : null
  if (target) {
    await openSession(target)
  } else {
    // 有历史会话时恢复最近一条，不自动新建；只有完全没有会话时才自动创建
    const active = sessions.value.find((s) => s.status !== 2)
    if (active) await openSession(active)
    else if (sessions.value.length) await openSession(sessions.value[0])
    else await createNewSession()
  }

  if (shouldReviewGarden) {
    const query = { ...route.query }
    delete query.gardenReview
    await router.replace({ path: route.path, query })
    await handleSend('请根据我最近30天的情绪花园记录，帮我温和回顾出现较多的情绪、评分和可能的情境联系。请区分记录事实与推测，不做诊断，并给我一个值得继续觉察的问题。', {
      displayText: '回顾近30天的情绪花园',
      includeGardenContext: true,
      skipEmotionAnalysis: true
    })
  }
})
onUnmounted(() => motionContext?.revert())
</script>

<template>
  <div ref="pageRef" class="workspace">
    <AppNavBar
      :actions="USER_NAV_ACTIONS"
      :current-path="route.path === '/home/consult' ? '/consult' : route.path"
    >
      <template #actions-after>
        <UserDropdown />
      </template>
    </AppNavBar>
    <div class="workspace-body">
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
          <div class="side-sub">AI 会在倾听中整理情绪变化</div>
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
                  <stop offset="0%" stop-color="#a9c5a6" />
                  <stop offset="100%" stop-color="#426b4b" />
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
            <p class="scan-text">正在整理这句话里的线索…</p>
          </div>

          <!-- 结果 -->
          <div v-else class="analysis-result">
            <div class="analysis-result-head">
              <span>本轮观察</span>
              <span class="analysis-source" :class="emotion.analysisSource">
                {{ emotion.analysisSource === 'agent' ? 'MindMan Agent' : '本地规则备用' }}
              </span>
            </div>

            <div class="emotion-chip">
              <span class="emotion-icon"><SparklesIcon :size="18" :stroke-width="1.8" aria-hidden="true" /></span>
              <div class="emotion-meta">
                <div class="emotion-name">{{ emotion.emotion }}</div>
                <div class="emotion-state">
                  {{ emotion.emotion === '暂不判断' ? '这句话没有可核验的明确线索，先不判断' : '仅描述这一次表达，不代表长期状态' }}
                </div>
              </div>
            </div>

            <blockquote v-if="emotion.evidence" class="analysis-evidence">
              <span>原话依据</span>
              <p>“{{ emotion.evidence }}”</p>
            </blockquote>

            <div v-if="emotion.cues?.length" class="analysis-cues">
              <div v-for="cue in emotion.cues" :key="cue.label" class="analysis-cue">
                <div class="cue-heading">
                  <span class="cue-dot" :class="{ mentioned: cue.evidence }"></span>
                  <strong>{{ cue.label }}</strong>
                  <span class="cue-status" :class="{ mentioned: cue.evidence }">{{ cue.status }}</span>
                </div>
                <small v-if="cue.evidence">“{{ cue.evidence }}”</small>
              </div>
            </div>

            <div class="feedback-card">
              <div class="fb-card-head">
                <SparklesIcon :size="14" :stroke-width="1.8" aria-hidden="true" />
                <span>{{ emotion.analysisSource === 'agent' ? 'AI 的初步理解' : '规则观察' }}</span>
              </div>
              <p class="fb-card-body">{{ analysisSummary }}</p>
            </div>

            <div class="analysis-provenance">
              <span>依据：本轮原话</span>
              <span>不是量表或诊断</span>
            </div>

          </div>
        </div>

        <div class="side-foot">
          <span v-if="lastAnalysisTime" class="foot-time">{{ lastAnalysisTime }} 更新</span>
        </div>
      </template>
    </aside>

    <section class="main">
      <div class="consult-context-bar">
        <div class="consult-context-copy">
          <div class="consult-context-title-row">
            <h1 class="consult-context-title">倾听空间</h1>
            <span class="consult-ai-badge"><SparklesIcon :size="13" :stroke-width="1.8" /> AI 倾听助手</span>
          </div>
          <p class="consult-context-status" :class="{ thinking: sending || loadingMessages }">
            {{ sending || loadingMessages ? '正在思考…' : '慢慢说，我在听' }}
          </p>
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

          <el-tooltip content="AI 总结本次对话" placement="bottom" :show-after="400">
            <button
              class="icon-btn summary-action"
              aria-label="AI 总结本次对话"
              :disabled="sending || !messages.some(message => message.role === 'user')"
              @click="createConversationSummary"
            >
              <FileText :size="16" :stroke-width="1.8" />
            </button>
          </el-tooltip>

          <el-tooltip content="会话历史" placement="bottom" :show-after="400">
            <button class="icon-btn" @click="historyVisible = true">
              <el-icon><Clock /></el-icon>
            </button>
          </el-tooltip>

        </div>
      </div>

        <div ref="listRef" class="chat-scroll">
        <div class="chat-column">
          <div v-if="showWelcomeStage" class="chat-welcome">
            <span class="welcome-index">AI 正在倾听</span>
            <h1>今天，<em>想从哪里说起？</em></h1>
            <p>我是你的 AI 倾听助手，可以先听你说，再陪你整理此刻的感受。不必讲完整，从一句话开始就好。</p>
            <div class="opening-prompts" aria-label="对话开场建议">
              <button v-for="prompt in openingPrompts" :key="prompt" type="button" @click="chooseOpening(prompt)">{{ prompt }} <span aria-hidden="true">↗</span></button>
            </div>
          </div>
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
              <img src="/mindman-mark-refresh.svg" alt="" />
            </div>

            <div v-if="msg.role === 'assistant'" class="ai-bubble" :class="{ streaming: msg.streaming }">
              <div class="bubble-meta">
                <span class="bubble-name">AI 倾听助手</span>
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
                  <template v-for="(segment, segmentIndex) in chatSegments(msg.content)" :key="segmentIndex">
                    <div v-if="segment.type === 'text'" class="chat-markdown" v-html="renderChatMarkdown(segment.value)"></div>
                    <router-link v-else class="article-inline-link" :to="`/home/articles/${segment.id}`">
                      <BookOpen :size="14" :stroke-width="1.8" /> 打开这篇文章
                    </router-link>
                  </template><span v-if="msg.streaming" class="stream-cursor"></span>
                </div>
                <div v-if="msg.deliveryStatus === 'interrupted' || msg.deliveryStatus === 'failed'" class="reply-state">
                  {{ msg.deliveryStatus === 'interrupted' ? '这段回复没有完整生成，已保留已收到的内容。' : '这次回复未能生成，可以重新发送。' }}
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
              <img src="/mindman-mark-refresh.svg" alt="" />
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
        <div v-if="referenceArticleId" class="article-context-bar">
          <div class="article-context-icon"><BookOpen :size="17" :stroke-width="1.8" aria-hidden="true" /></div>
          <div class="article-context-copy">
            <span class="article-context-label">本轮参考文章</span>
            <strong :title="referenceArticleTitle">{{ referenceArticleTitle }}</strong>
            <small v-if="referenceArticleIsExcerpt">当前为来源摘要，翻译与分析仅覆盖这部分收录内容</small>
            <small v-else>AI 只依据 MindMan 收录内容回应，不会自动读取外链全文</small>
          </div>
          <div class="article-context-actions">
            <button
              type="button"
              class="article-context-action primary"
              :disabled="sending || creating"
              @click="analyzeAndTranslateArticle"
            >
              <Languages v-if="referenceArticleLanguage === 'en'" :size="15" :stroke-width="1.9" aria-hidden="true" />
              <SparklesIcon v-else :size="15" :stroke-width="1.9" aria-hidden="true" />
              {{ referenceArticleLanguage === 'en' ? '翻译并解读' : '提炼文章要点' }}
            </button>
            <button
              type="button"
              class="article-context-action"
              :disabled="sending || creating"
              @click="discussReferencedArticle"
            >
              <MessageCircle :size="15" :stroke-width="1.8" aria-hidden="true" />
              结合文章聊聊
            </button>
            <button type="button" class="article-context-remove" aria-label="移除参考文章" @click="clearArticleReference">
              <X :size="15" :stroke-width="1.8" />
            </button>
          </div>
        </div>

        <Transition name="mood-fade">
          <div v-if="moodPanel" class="mood-panel" role="group" aria-label="心情开场句">
            <span class="mood-panel-hint">从一句话开始，接着写发生了什么</span>
            <button
              v-for="option in moodOptions"
              :key="option"
              type="button"
              class="mood-chip"
              @click="pickMood(option)"
            >
              {{ option }}
            </button>
          </div>
        </Transition>

          <div class="composer">
            <div class="composer-tools">
              <el-tooltip content="心情" placement="top" :show-after="300">
                <button class="tool-btn" type="button" aria-label="选择一句心情开场" :aria-expanded="moodPanel" @click="toggleMood">
                  <SmilePlus :size="18" :stroke-width="1.7" />
                </button>
              </el-tooltip>
              <el-tooltip content="上传图片" placement="top" :show-after="300">
                <button class="tool-btn" @click="handleTodo">
                  <Paperclip :size="18" :stroke-width="1.7" />
                </button>
              </el-tooltip>
              <el-tooltip :content="listening ? '点击结束录音' : '语音输入'" placement="top" :show-after="300">
                <button class="tool-btn" :class="{ listening }" @click="toggleVoice">
                  <Mic :size="18" :stroke-width="1.7" />
                </button>
              </el-tooltip>
              <el-tooltip content="AI 建议与花园回顾" placement="top" :show-after="300">
                <el-dropdown trigger="click" @command="chooseAiAction">
                  <button class="tool-btn" aria-label="AI 建议与花园回顾" :disabled="sending">
                    <SparklesIcon :size="18" :stroke-width="1.7" />
                  </button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="advice">根据对话给我温和建议</el-dropdown-item>
                      <el-dropdown-item command="garden">结合情绪花园回顾近况</el-dropdown-item>
                      <el-dropdown-item command="article">从对话主题找一篇文章</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
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
            ref="composerInputRef"
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

    </section>
    </div>

    <el-dialog
      v-model="summaryVisible"
      class="conversation-summary-dialog"
      width="min(620px, calc(100vw - 32px))"
      :close-on-click-modal="!summaryLoading"
    >
      <template #header>
        <div class="summary-dialog-heading">
          <span class="summary-dialog-icon"><SparklesIcon :size="17" :stroke-width="1.8" /></span>
          <div>
            <strong>本次对话总结</strong>
            <span>只整理你在当前会话里分享的内容</span>
          </div>
        </div>
      </template>
      <div v-if="summaryLoading" class="summary-loading">
        <span class="summary-loading-orb"></span>
        <span>AI 正在整理这段对话…</span>
      </div>
      <div v-else-if="summaryError" class="summary-error">
        <p>{{ summaryError }}</p>
        <button type="button" @click="createConversationSummary">再试一次</button>
      </div>
      <div v-else class="summary-content chat-markdown" v-html="renderChatMarkdown(summaryContent)"></div>
      <template #footer>
        <div class="summary-dialog-footer">
          <span>这是一份温和回顾，不构成诊断。</span>
          <div>
            <button type="button" class="summary-close" @click="summaryVisible = false">关闭</button>
            <button type="button" class="summary-copy" :disabled="!summaryContent" @click="copySummary">复制总结</button>
          </div>
        </div>
      </template>
    </el-dialog>

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
                      <button
                        v-else
                        class="mini-btn"
                        @click.stop="openSession(session)"
                      >
                        继续
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
  padding-top: 82px;
  box-sizing: border-box;
  background: #f8f7f2;
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

.article-inline-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0 3px;
  padding: 4px 9px;
  border: 1px solid #d5e3d2;
  border-radius: 999px;
  background: #f4f8f1;
  color: #426b4b;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
  text-decoration: none;
  white-space: nowrap;
  transition: background .18s ease, border-color .18s ease, transform .18s ease;
}

.article-inline-link:hover {
  transform: translateY(-1px);
  border-color: #9bb99a;
  background: #eaf2e7;
}

.article-inline-link:focus-visible {
  outline: 2px solid #739a79;
  outline-offset: 2px;
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
  flex-wrap: wrap;
  gap: 8px;
  width: max-content;
  max-width: min(680px, calc(100vw - 24px));
  padding: 12px 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(26px);
  -webkit-backdrop-filter: blur(26px);
  border: 1px solid rgba(255, 255, 255, 0.9);
  box-shadow: 0 20px 50px rgba(59, 130, 246, 0.16);
}

.mood-panel-hint {
  flex-basis: 100%;
  color: #718675;
  font-size: 11px;
  line-height: 1.5;
  padding: 0 3px 2px;
}

.mood-chip {
  padding: 7px 15px;
  border-radius: 999px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  font-size: 13px;
  font-family: inherit;
  color: #475569;
  cursor: pointer;
  transition: all 0.2s;
}

.mood-chip:focus-visible { outline: 2px solid #729677; outline-offset: 2px; }

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

/* MindMan editorial chat */
.workspace .main {
  background: radial-gradient(560px 380px at 88% 8%, #e9f0e5 0, transparent 70%), #faf9f5 !important;
}
.workspace .side { background: #f1f5ed !important; border-right: 1px solid #dfe8da !important; }
.side-title { font-family: 'Noto Serif SC', Georgia, serif; color: #294733; letter-spacing: -.04em; }
.side-sub, .idle-text, .scan-text { color: #809384; }
.pulse-dot { background: #83a98a; box-shadow: none; }
.side-body { scrollbar-color: #bed0bf transparent; }
.side-body::-webkit-scrollbar-thumb { background: #bed0bf; }
.collapse-btn:hover { color: #426b4b; border-color: #92ae94; background: #eaf2e7; }
.scan-line { background: linear-gradient(90deg, transparent, #7ca082, transparent); box-shadow: 0 0 10px #7ca0827a; }
.scan-core { background: #789d7d; box-shadow: 0 0 12px #789d7d88; }
.emotion-chip { background: linear-gradient(135deg, #e9f2e7, #f6eee5); border-color: #d7e6d3; }
.emotion-icon, .ring-card, .star-board, .feedback-card { box-shadow: 0 7px 19px #3557410d; border-color: #e1eadc; }
.emotion-name, .card-title { color: #304b37; }
.emotion-bar span { background: linear-gradient(90deg, #83a989, #426b4b); }
.emotion-score { color: #456e4d; }
.workspace .chat-header {
  margin: 18px 22px 0;
  padding: 11px 16px;
  background: #fffdf9ed !important;
  border: 1px solid #dce6da !important;
  border-radius: 15px !important;
  box-shadow: 0 9px 26px #31523a12 !important;
}
.workspace .ai-logo, .workspace .bubble-avatar { background: transparent !important; box-shadow: none !important; overflow: hidden; }
.ai-logo img, .bubble-avatar img { display: block; width: 100%; height: 100%; object-fit: cover; }
.ai-name { font: 600 20px/1.25 Georgia, 'Noto Serif SC', serif; letter-spacing: -.04em; color: #294733; }
.ai-sub { color: #78907b; }
.ai-sub.thinking span { background-image: linear-gradient(90deg, #729374, #315a3e, #729374); }
.header-actions { gap: 6px; }
.icon-btn { border-radius: 9px; border-color: #e0e8dd; background: #fafbf7; color: #55705b; box-shadow: none; }
.icon-btn:disabled { opacity: .42; cursor: not-allowed; transform: none; }
.icon-btn:hover, .garden-icon:hover { color: #315d3f; border-color: #91ad96; background: #eaf2e7; transform: translateY(-2px); }
.chat-source-note { display: flex; align-items: center; gap: 9px; width: fit-content; max-width: 100%; margin: 0 8px -11px; padding: 8px 11px; border: 1px solid #dce7d8; border-radius: 9px; background: #f1f5ed; color: #5e7d62; font-size: 12px; }
.chat-source-note span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chat-source-note button { display: grid; place-items: center; flex: none; width: 22px; height: 22px; padding: 0; border: 0; border-radius: 50%; background: transparent; color: #788b79; cursor: pointer; }
.chat-source-note button:hover { background: #e1eadc; color: #31593c; }
.chat-seal { display: grid; place-items: center; width: 37px; height: 37px; margin-left: 5px; flex: none; border-radius: 12px; transition: transform .3s ease, box-shadow .3s ease; }
.chat-seal img { width: 100%; height: 100%; display: block; border-radius: inherit; }
.chat-seal:hover { transform: rotate(-8deg) scale(1.06); box-shadow: 0 7px 16px #31523a28; }
.chat-seal:focus-visible { outline: 2px solid #426b4b; outline-offset: 3px; }
.chat-scroll { padding: 26px 28px 17px; }
.chat-column { max-width: 840px; gap: 21px; }
.chat-welcome { padding: 34px 8px 31px; margin-bottom: 8px; border-bottom: 1px solid #d8e3d5; animation: welcomeIn .8s both; }
.welcome-index { display: block; font: 700 10px/1.4 'DM Sans', Arial, sans-serif; letter-spacing: .22em; color: #66856a; }
.welcome-index i { color: #b58b72; font-style: normal; padding: 0 .3em; }
.chat-welcome h1 { margin: 15px 0 11px; font: 500 clamp(32px, 3.8vw, 50px)/1.3 'Noto Serif SC', Georgia, serif; letter-spacing: -.055em; color: #2c4533; }
.chat-welcome h1 em { font-style: normal; color: #729171; }
.chat-welcome p { max-width: 550px; margin: 0; color: #758575; line-height: 1.85; font-size: 14px; }
.opening-prompts { display: flex; flex-wrap: wrap; gap: 9px; margin-top: 22px; }
.opening-prompts button { display: inline-flex; align-items: center; gap: 17px; padding: 10px 14px; border: 1px solid #cadac8; border-radius: 8px; background: #fffdf9; color: #42634a; font-family: inherit; font-size: 13px; font-weight: 500; line-height: 1.45; cursor: pointer; transition: transform .25s ease, border-color .25s ease, background .25s ease, box-shadow .25s ease; }
.opening-prompts button span { color: #9aaf9a; font-size: 15px; }
.opening-prompts button:hover { transform: translateY(-3px); border-color: #799d7c; background: #f2f7ee; box-shadow: 0 7px 16px #36594015; }
.opening-prompts button:focus-visible { outline: 2px solid #426b4b; outline-offset: 2px; }
@keyframes welcomeIn { from { opacity: 0; transform: translateY(22px); } to { opacity: 1; transform: translateY(0); } }
.workspace .ai-bubble { max-width: 80%; padding: 17px 21px; background: #fff !important; border: 1px solid #e3eade !important; border-radius: 17px 17px 17px 5px !important; box-shadow: 0 10px 25px #3557410e !important; backdrop-filter: none; }
.workspace .user-bubble { padding: 14px 18px; background: #e6f0e3 !important; border: 1px solid #d1e1ce !important; border-radius: 17px 17px 5px 17px !important; box-shadow: none !important; backdrop-filter: none; }
.bubble-name { color: #3c6845; }
.chat-content { color: #344d39; line-height: 1.85; }
.reply-state { margin-top: 10px; color: #94745b; font-size: 11px; line-height: 1.6; }
.ai-card { border-radius: 10px; background: #f7f9f5; border-color: #e1eadf; box-shadow: none; }
.card-head, .card-duration { color: #7d9080; }
.card-bar { background: #e4ece1; }
.card-bar-fill { background: linear-gradient(90deg, #a5bea1, #5f8d65); }
.card-percent { color: #527c58; }
.thinking-wave span { background: #75997b; }
.stream-cursor { background: #426b4b; }
.composer-wrap { padding: 12px 22px 20px; }
.workspace .composer { max-width: 840px; background: #fff !important; border: 1px solid #d9e6d7 !important; border-radius: 15px !important; box-shadow: 0 8px 25px #33563c15 !important; }
.workspace .composer:focus-within { border-color: #739a79 !important; box-shadow: 0 0 0 2px #71957530, 0 10px 29px #33563c19 !important; }
.tool-btn { border-radius: 9px; border-color: #e4eadf; background: #f5f8f2; color: #5a785d; box-shadow: none; }
.tool-btn:hover { background: #e6f0e3; color: #31593c; transform: translateY(-2px); box-shadow: none; }
.model-btn { background: #eaf1e7 !important; border-color: #cbdcca !important; }
.model-icon, .model-check { color: #4f7457; }
.composer-input :deep(.el-textarea__inner) { color: #344d39; }
.composer-input :deep(.el-textarea__inner::placeholder) { color: #879d8b; }
.workspace .send-btn { border-radius: 11px !important; background: #315a3e !important; box-shadow: none !important; }
.workspace .send-btn:hover:not(:disabled) { background: #244c32 !important; transform: translateY(-2px); box-shadow: 0 8px 17px #31523a30 !important; }
.mood-panel { border-color: #dce7d8; background: #fffdf9; box-shadow: 0 18px 45px #31523a20; }
.summary-dialog-heading { display: flex; align-items: center; gap: 12px; color: #294733; }
.summary-dialog-heading > div { display: grid; gap: 4px; }
.summary-dialog-heading strong { font: 600 19px/1.3 'Noto Serif SC', Georgia, serif; }
.summary-dialog-heading > div > span { color: #829083; font-size: 12px; font-weight: 400; }
.summary-dialog-icon { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 11px; background: #edf3e9; color: #507454; }
.summary-loading { display: flex; align-items: center; gap: 11px; min-height: 180px; justify-content: center; color: #718473; font-size: 13px; }
.summary-loading-orb { width: 10px; height: 10px; border-radius: 50%; background: #739478; box-shadow: 0 0 0 6px #73947820; animation: summaryPulse 1.4s ease-in-out infinite; }
@keyframes summaryPulse { 50% { transform: scale(.78); opacity: .58; } }
.summary-content { max-height: min(52vh, 520px); overflow: auto; padding: 18px 20px; border: 1px solid #e4eadf; border-radius: 13px; background: #f8f9f5; color: #3d5541; font-size: 14px; line-height: 1.95; }
.summary-error { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 150px; color: #8b635a; }
.summary-error button, .summary-copy, .summary-close { padding: 9px 14px; border: 1px solid #d6e2d3; border-radius: 9px; background: #f3f7f0; color: #426b4b; font-family: inherit; font-size: 12px; font-weight: 500; line-height: 1.2; cursor: pointer; }
.summary-copy { border-color: #315a3e; background: #315a3e; color: white; }
.summary-copy:disabled { opacity: .45; cursor: not-allowed; }
.summary-dialog-footer { display: flex; align-items: center; justify-content: space-between; gap: 14px; }
.summary-dialog-footer > span { color: #899689; font-size: 11px; }
.summary-dialog-footer > div { display: flex; gap: 8px; }
:global(.conversation-summary-dialog .el-dialog) { overflow: hidden; border-radius: 17px; background: #fffdf9; box-shadow: 0 22px 75px #1c33252b; }
:global(.conversation-summary-dialog .el-dialog__header) { margin: 0; padding: 22px 24px 15px; border-bottom: 1px solid #edf0e9; }
:global(.conversation-summary-dialog .el-dialog__body) { padding: 20px 24px 8px; }
:global(.conversation-summary-dialog .el-dialog__footer) { padding: 14px 24px 20px; }
@media (max-width: 860px) { .chat-welcome { padding-top: 23px; } .chat-seal { display: none; } }
@media (max-width: 520px) {
  .workspace .chat-header { margin: 8px 10px 0; padding: 8px 10px; }
  .ai-brand { gap: 8px; }
  .ai-logo { width: 34px; height: 34px; border-radius: 10px; }
  .ai-name { font-size: 17px; }
  .ai-sub { display: none; }
  .header-actions { gap: 4px; }
  .icon-btn { width: 32px; height: 32px; }
  .chat-scroll { padding: 16px 12px 8px; }
  .chat-welcome { padding: 17px 2px 23px; }
  .chat-welcome h1 { font-size: 30px; }
  .chat-welcome p { font-size: 13px; }
  .opening-prompts { margin-top: 17px; }
  .opening-prompts button { padding: 8px 10px; font-size: 12px; }
  .workspace .ai-bubble { max-width: 85%; padding: 14px 16px; }
  .workspace .user-bubble { max-width: 82%; }
  .composer-wrap { padding: 8px 10px 12px; }
  .chat-source-note { margin: 0 2px -8px; font-size: 11px; }
  .summary-dialog-footer { align-items: flex-start; flex-direction: column; }
  .summary-dialog-footer > div { width: 100%; justify-content: flex-end; }
  :global(.conversation-summary-dialog .el-dialog__header) { padding: 18px 18px 13px; }
  :global(.conversation-summary-dialog .el-dialog__body) { padding: 16px 18px 6px; }
  :global(.conversation-summary-dialog .el-dialog__footer) { padding: 12px 18px 17px; }
  .workspace .composer { flex-wrap: wrap; gap: 7px; padding: 8px; }
  .composer-tools { width: 100%; justify-content: space-between; }
  .composer-input { order: 1; flex: 1 1 0; }
  .send-btn { order: 2; }
  .composer-input :deep(.el-textarea__inner) { min-height: 40px !important; max-height: 90px; overflow-y: auto; }
}

/* Shared navigation and page heading */
.workspace { flex-direction: column; background: #f8f7f2; }
.workspace-body { display: flex; flex: 1; min-height: 0; }
.consult-context-bar { position: relative; z-index: 1; display: flex; align-items: center; justify-content: space-between; gap: 24px; min-height: 82px; padding: 12px clamp(18px, 2.3vw, 32px); border-bottom: 1px solid #e7e9df; }
.consult-context-copy { display: grid; gap: 4px; min-width: 0; }
.consult-context-title-row { display: flex; align-items: center; gap: 12px; min-width: 0; }
.consult-context-title { margin: 0; color: #2e4c38; font: 600 clamp(20px, 2vw, 24px)/1.3 'Noto Serif SC', Georgia, serif; letter-spacing: -.045em; white-space: nowrap; }
.consult-context-status { margin: 0; color: #839385; font: 500 12px/1.5 'Noto Sans SC', sans-serif; }
.consult-context-status.thinking { color: #5c8260; }
.consult-ai-badge { display: inline-flex; align-items: center; gap: 5px; padding: 5px 9px; border: 1px solid #dbe5d7; border-radius: 999px; background: #eef3e9; color: #527457; font: 600 11px/1 'Noto Sans SC', sans-serif; white-space: nowrap; }
.consult-context-bar .icon-btn { width: 35px; height: 35px; }
@media (max-width: 780px) {
  .consult-context-bar { min-height: 74px; padding: 10px 18px; }
  .consult-context-title-row { gap: 9px; }
}
@media (max-width: 520px) {
  .consult-context-bar { min-height: 66px; padding: 8px 13px; gap: 10px; }
  .consult-context-copy { gap: 2px; }
  .consult-context-title { font-size: 17px; }
  .consult-context-status { font-size: 10px; }
  .consult-ai-badge { gap: 4px; padding: 4px 6px; font-size: 9px; }
  .consult-context-bar .icon-btn { width: 32px; height: 32px; }
  .consult-context-bar .header-actions { gap: 5px; }
}
@media (max-width: 900px) { .workspace { padding-top: 74px; } }
@media (max-width: 620px) {
  .workspace { padding-top: 70px; }
}
@media (prefers-reduced-motion: reduce) {
  .chat-welcome, .chat-row, .bubble-avatar.breathe { animation: none !important; }
  .opening-prompts button, .icon-btn, .tool-btn, .send-btn { transition: none !important; }
}

.article-context-bar {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  width: min(840px, 100%);
  box-sizing: border-box;
  margin: 0 auto 10px;
  padding: 12px 14px;
  border: 1px solid #dce8d8;
  border-radius: 14px;
  background: linear-gradient(110deg, #f1f6ed, #fffdf8 76%);
  box-shadow: 0 6px 18px #31523a0b;
}
.article-context-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border: 1px solid #d5e4d1;
  border-radius: 11px;
  background: #e8f1e4;
  color: #527958;
}
.article-context-copy { display: grid; min-width: 0; gap: 3px; }
.article-context-label { color: #779079; font-size: 10px; font-weight: 650; letter-spacing: .04em; }
.article-context-copy strong {
  overflow: hidden;
  color: #304c37;
  font: 600 13px/1.45 'Noto Sans SC', sans-serif;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.article-context-copy small { color: #819081; font-size: 10px; line-height: 1.45; }
.article-context-actions { display: flex; align-items: center; gap: 6px; }
.article-context-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 34px;
  padding: 0 10px;
  border: 1px solid #d9e5d6;
  border-radius: 9px;
  background: #fffefa;
  color: #496b4f;
  font: 550 11px/1.2 'Noto Sans SC', sans-serif;
  white-space: nowrap;
  cursor: pointer;
  transition: background .2s ease, border-color .2s ease, transform .2s ease;
}
.article-context-action.primary { border-color: #426b4b; background: #426b4b; color: #fff; }
.article-context-action:hover:not(:disabled) { transform: translateY(-1px); border-color: #88a787; background: #edf4e9; }
.article-context-action.primary:hover:not(:disabled) { border-color: #31583b; background: #31583b; }
.article-context-action:disabled { opacity: .48; cursor: not-allowed; }
.article-context-action:focus-visible, .article-context-remove:focus-visible { outline: 2px solid #426b4b; outline-offset: 2px; }
.article-context-remove {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  flex: none;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: #879687;
  cursor: pointer;
}
.article-context-remove:hover { background: #e6eee2; color: #426b4b; }
.ai-bubble .chat-content { white-space: normal; }
.user-bubble .chat-content { white-space: pre-wrap; }
@media (max-width: 860px) {
  .article-context-bar { grid-template-columns: 34px minmax(0, 1fr) 28px; gap: 9px; padding: 10px 11px; }
  .article-context-icon { width: 34px; height: 34px; }
  .article-context-copy { grid-column: 2; grid-row: 1; padding-right: 2px; }
  .article-context-remove { grid-column: 3; grid-row: 1; align-self: start; }
  .article-context-actions { grid-column: 2 / 4; grid-row: 2; flex-wrap: wrap; }
}
@media (max-width: 520px) {
  .article-context-bar { margin-bottom: 8px; border-radius: 12px; }
  .article-context-copy strong { font-size: 12px; }
  .article-context-copy small { font-size: 9px; }
  .article-context-actions { gap: 6px; }
  .article-context-action { min-height: 32px; flex: 1 1 auto; padding: 0 8px; font-size: 10px; }
}
@media (prefers-reduced-motion: reduce) {
  .article-context-action { transition: none; }
  .article-context-action:hover { transform: none; }
}
/* Evidence-led AI observation panel: no decorative, ungrounded scores. */
.analysis-result { display: grid; gap: 11px; padding: 1px 0 8px; }
.analysis-result > * { animation: analysisCueIn .38s both; }
.analysis-result > *:nth-child(2) { animation-delay: 35ms; }
.analysis-result > *:nth-child(3) { animation-delay: 70ms; }
.analysis-result > *:nth-child(4) { animation-delay: 105ms; }
.analysis-result > *:nth-child(5) { animation-delay: 140ms; }
.analysis-result > *:nth-child(6) { animation-delay: 175ms; }
.analysis-result-head { display: flex; justify-content: space-between; align-items: center; color: #829283; font-size: 10px; letter-spacing: .08em; }
.analysis-source { padding: 4px 7px; border-radius: 6px; background: #edf2e9; color: #6a856d; font-size: 9px; letter-spacing: 0; }
.analysis-source.agent { background: #e8f0e4; color: #456b4d; }
.analysis-source.rules { background: #f3efe6; color: #89775c; }
.analysis-result .emotion-chip { gap: 11px; padding: 12px; border: 1px solid #dce7d8; border-radius: 13px; background: linear-gradient(135deg,#f2f6ef,#f8f4ec); box-shadow: none; }
.analysis-result .emotion-icon { width: 36px; height: 36px; border: 1px solid #e0e9dc; border-radius: 11px; background: #fffdfa; color: #66876a; box-shadow: none; }
.analysis-result .emotion-name { margin-bottom: 3px; color: #304a36; font-size: 15px; font-weight: 650; }
.emotion-state { color: #849183; font-size: 10px; line-height: 1.55; }
.analysis-evidence { margin: 0; padding: 10px 11px; border-left: 2px solid #91aa8d; border-radius: 0 9px 9px 0; background: #f5f7f2; color: #4d654f; }
.analysis-evidence > span { color: #879586; font-size: 9px; letter-spacing: .08em; }
.analysis-evidence p { margin: 4px 0 0; font-size: 11px; line-height: 1.7; overflow-wrap: anywhere; }
.analysis-cues { display: grid; gap: 6px; }
.analysis-cue { padding: 8px 9px; border: 1px solid #e5eadf; border-radius: 9px; background: #fffefa; }
.cue-heading { display: flex; align-items: center; gap: 7px; min-height: 16px; }
.cue-dot { width: 6px; height: 6px; flex: none; border-radius: 50%; background: #c6cec2; }
.cue-dot.mentioned { background: #6e9874; box-shadow: 0 0 0 3px #6e98741a; }
.cue-heading strong { color: #546958; font-size: 10px; font-weight: 600; }
.cue-status { margin-left: auto; color: #a0a99c; font-size: 9px; }
.cue-status.mentioned { color: #658469; }
.analysis-cue small { display: block; margin: 5px 0 0 13px; color: #829181; font-size: 9px; line-height: 1.55; overflow-wrap: anywhere; }
.analysis-result .feedback-card { margin: 0; padding: 11px 12px; border: 1px solid #e4e9df; border-radius: 11px; background: #fffefa; box-shadow: none; }
.analysis-result .fb-card-head { margin-bottom: 5px; color: #618066; font-size: 10px; letter-spacing: 0; }
.analysis-result .fb-card-body { margin: 0; color: #566b59; font-size: 10px; line-height: 1.75; }
.analysis-provenance { display: flex; justify-content: space-between; gap: 8px; color: #98a294; font-size: 9px; line-height: 1.5; }
@keyframes analysisCueIn { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
@media (prefers-reduced-motion: reduce) { .analysis-result > * { animation: none; } }
</style>
