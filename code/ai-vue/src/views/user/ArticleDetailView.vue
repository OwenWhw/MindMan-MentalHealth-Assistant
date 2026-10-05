<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowUpRight, MessageCircle } from 'lucide-vue-next'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { getArticleDetail } from '@/api/knowledge'
import AppNavBar from '@/components/AppNavBar.vue'
import UserDropdown from '@/components/UserDropdown.vue'
import { USER_NAV_ACTIONS } from '@/constants/userNavigation'

const route = useRoute()
const router = useRouter()

const article = ref(null)
const loading = ref(true)
const scrollRef = ref()

/**
 * 本站旧文章以 HTML 保存，新抓取/编辑内容也可能是 Markdown。
 * 统一解析为阅读排版，并用明确的标签白名单净化后再交给 v-html。
 */
const renderedContent = computed(() => {
  const source = String(article.value?.content || '')
  if (!source.trim()) return ''

  const html = marked.parse(source, { gfm: true, breaks: false, async: false })
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: [
      'p', 'br', 'hr', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
      'strong', 'b', 'em', 'i', 'del', 'blockquote',
      'ul', 'ol', 'li', 'a', 'code', 'pre',
      'table', 'thead', 'tbody', 'tr', 'th', 'td'
    ],
    ALLOWED_ATTR: ['href', 'title']
  })
})

/** tags 后端是 JSON 字符串，这里解析为数组 */
const tagsList = computed(() => {
  try {
    if (!article.value?.tags) return []
    const parsed = JSON.parse(article.value.tags)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    // 兜底：按英文逗号 split
    return String(article.value?.tags || '').split(',').map(s => s.trim()).filter(Boolean)
  }
})

/** 实时文章：来源标注 */
const isLive = computed(() => article.value?.sourceType === 'crawled')
const isExternalFeedArticle = computed(() => isLive.value && /^https:\/\//i.test(article.value?.sourceUrl || ''))
const isEnglishArticle = computed(() => {
  const content = [article.value?.title, article.value?.summary, article.value?.content]
    .filter(Boolean)
    .join(' ')
    .replace(/<[^>]*>/g, ' ')
  const latinLetters = (content.match(/[A-Za-z]/g) || []).length
  const chineseCharacters = (content.match(/[\u3400-\u9fff]/g) || []).length
  return latinLetters >= 20 && latinLetters > chineseCharacters * 1.2
})

async function loadDetail() {
  loading.value = true
  try {
    article.value = await getArticleDetail(route.params.id)
  } catch (e) {
    article.value = null
    if (!e?.handled) ElMessage.error(e.message || '文章加载失败')
  } finally {
    loading.value = false
    nextTick(() => {
      if (scrollRef.value) scrollRef.value.scrollTop = 0
    })
  }
}

function goBack() {
  router.push('/home/articles')
}

function takeToListening() {
  if (!article.value) return
  router.push({
    path: '/consult',
    query: {
      articleId: String(article.value.articleId || route.params.id),
      articleTitle: article.value.title,
      articleLanguage: isEnglishArticle.value ? 'en' : 'zh',
      articleExcerpt: isExternalFeedArticle.value ? '1' : '0'
    }
  })
}

function openSourceUrl() {
  const url = article.value?.sourceUrl
  // AI 生成的文章 sourceUrl 是 "ai://..." 占位，不应打开
  if (!url || url.startsWith('ai://')) return
  window.open(url, '_blank', 'noopener')
}

onMounted(loadDetail)
</script>

<template>
  <div ref="scrollRef" class="detail-page">
    <AppNavBar
      :actions="USER_NAV_ACTIONS"
      :current-path="route.path"
    >
      <template #actions-after>
        <UserDropdown />
      </template>
    </AppNavBar>

    <main class="detail-wrap">
      <button class="back-btn" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        <span>返回列表</span>
      </button>

      <div v-if="loading" class="loading">
        <el-icon class="is-loading"><Loading /></el-icon>
      </div>

      <div v-else-if="!article" class="empty-block">
        <el-empty description="文章不存在或已被删除" :image-size="100" />
      </div>

      <article v-else class="detail-card" :class="{ 'is-live': isLive }">
        <span class="detail-cat" :class="{ 'detail-cat-live': isLive }">
          <template v-if="isLive">🛰 实时 · {{ article.sourceName || '实时心理' }}</template>
          <template v-else>{{ article.categoryName }}</template>
        </span>
        <p class="editorial-whisper detail-whisper" aria-hidden="true">one page<span>at a time.</span></p>
        <h1 class="detail-title">{{ article.title }}</h1>

        <div class="detail-meta">
          <span><el-icon><View /></el-icon>{{ article.reads }} 阅读</span>
          <span><el-icon><Clock /></el-icon>{{ article.publishTime?.slice(0, 10) }}</span>
          <span><el-icon><User /></el-icon>@{{ article.author }}</span>
          <a
            v-if="isLive && article.sourceUrl && !article.sourceUrl.startsWith('ai://')"
            class="meta-source"
            href="javascript:void(0)"
            @click="openSourceUrl"
          >
            <el-icon><Link /></el-icon>查看原文
          </a>
        </div>

        <p v-if="article.summary" class="detail-summary">{{ article.summary }}</p>

        <div class="detail-content" v-html="renderedContent"></div>

        <div v-if="isExternalFeedArticle" class="source-summary-note">
          <span class="source-note-label">来源摘要 · 原文版权归发布方所有</span>
          <p>这里展示的是订阅源提供的内容摘要，不是全文转载。查看完整文章与上下文，请前往原始发布页面。</p>
          <a :href="article.sourceUrl" target="_blank" rel="noopener noreferrer">
            阅读 {{ article.sourceName || '来源网站' }} 原文
            <el-icon><Link /></el-icon>
          </a>
        </div>

        <div class="article-ai-nudge">
          <div class="article-ai-copy">
            <span>{{ isEnglishArticle ? '读懂这篇英文文章' : '带着问题继续读' }}</span>
            <p>{{ isEnglishArticle
              ? '进入倾听空间后，可让 AI 翻译本站收录内容、梳理要点并联系实际。'
              : '让 AI 结合这篇文章，陪你聊聊它和当下感受的联系。' }}</p>
          </div>
          <button type="button" class="article-ai-button" @click="takeToListening">
            <MessageCircle :size="16" :stroke-width="1.8" aria-hidden="true" />
            <span>带去倾听空间</span>
            <ArrowUpRight :size="15" aria-hidden="true" />
          </button>
        </div>

        <div v-if="tagsList.length" class="detail-tags">
          <span v-for="t in tagsList" :key="t" class="tag-pill">#{{ t }}</span>
        </div>

        <div class="detail-foot">
          <span class="foot-note">
            {{ isLive ? 'MindMan 心理阅读 · 来源摘要每日检查更新' : '心理健康助手 · 让知识陪伴每一天' }}
          </span>
        </div>
      </article>
    </main>
  </div>
</template>

<style scoped>
.detail-page {
  height: 100vh;
  overflow-y: auto;
  background:
    radial-gradient(620px 420px at 88% 10%, rgba(130, 158, 117, 0.09), transparent 66%),
    linear-gradient(180deg, #faf9f5 0%, #f7f7f1 58%, #f8f7f2 100%);
}

.detail-wrap {
  max-width: 760px;
  margin: 0 auto;
  padding: 100px 24px 64px;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  margin-bottom: 18px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.85);
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(14px);
  font-size: 13px;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s;
}

.back-btn:hover {
  color: #426b4b;
  border-color: #b7cbb1;
  background: #f7f9f4;
  transform: translateX(-2px);
}

.loading,
.empty-block {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 420px;
  font-size: 24px;
  color: #426b4b;
}

.detail-card {
  padding: 34px 38px 30px;
  border-radius: 26px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(26px);
  border: 1px solid rgba(255, 255, 255, 0.9);
  box-shadow: 0 24px 70px rgba(47, 111, 219, 0.1);
}
.detail-card.is-live {
  border: 1px solid rgba(112, 151, 111, 0.4);
  box-shadow: 0 24px 70px rgba(66, 107, 75, 0.12);
}

.detail-cat {
  display: inline-block;
  font-size: 12px;
  color: #426b4b;
  background: #edf3e9;
  padding: 4px 13px;
  border-radius: 999px;
  font-weight: 600;
  letter-spacing: 1px;
  margin-bottom: 16px;
}
.detail-cat-live {
  color: #426b4b;
  background: #e8f0e4;
  border: 1px solid #d4e3d0;
}

.detail-title {
  margin: 0 0 14px;
  font-size: 26px;
  font-weight: 800;
  color: #111827;
  line-height: 1.45;
}
.detail-whisper { width:max-content; margin:4px 0 20px 4px; font-size:25px; }

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding-bottom: 18px;
  margin-bottom: 18px;
  border-bottom: 1px dashed rgba(226, 232, 240, 0.9);
  font-size: 12px;
  color: #94a3b8;
}

.detail-meta span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.meta-source {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #527a58;
  font-weight: 600;
  cursor: pointer;
  border-bottom: 1px dashed rgba(82, 122, 88, 0.45);
  padding-bottom: 1px;
}
.meta-source:hover { color: #315a3e; border-bottom-style: solid; }
.meta-source .el-icon { font-size: 13px; }

.detail-summary {
  margin: 0 0 20px;
  padding: 13px 16px;
  border-left: 3px solid #789a78;
  border-radius: 0 8px 8px 0;
  background: #f0f5ed;
  font-size: 14px;
  line-height: 1.85;
  color: #526758;
}

.source-summary-note { margin-top:22px; padding:16px 18px; border:1px solid #dce8d9; border-radius:14px; background:#f4f7f0; }
.source-note-label { color:#527757; font-size:12px; font-weight:700; }
.source-summary-note p { margin:7px 0 10px; color:#758174; font-size:13px; line-height:1.75; }
.source-summary-note a { display:inline-flex; align-items:center; gap:6px; color:#426b4b; font-size:13px; font-weight:650; text-decoration:none; }
.source-summary-note a:hover { text-decoration:underline; }

.detail-content {
  max-width: 70ch;
  font-size: 16px;
  line-height: 1.95;
  color: #334155;
  word-break: break-word;
}

.detail-content :deep(p) {
  margin: 0 0 1.1em;
}

.detail-content :deep(h1),
.detail-content :deep(h2),
.detail-content :deep(h3),
.detail-content :deep(h4),
.detail-content :deep(h5),
.detail-content :deep(h6) {
  margin: 1.8em 0 0.7em;
  color: #304b38;
  font-family: 'Noto Serif SC', Georgia, serif;
  font-weight: 600;
  line-height: 1.5;
  text-wrap: pretty;
}

.detail-content :deep(h1) { font-size: 1.55em; }
.detail-content :deep(h2) { font-size: 1.38em; }
.detail-content :deep(h3) { font-size: 1.22em; }
.detail-content :deep(h4),
.detail-content :deep(h5),
.detail-content :deep(h6) { font-size: 1.08em; }

.detail-content :deep(ul),
.detail-content :deep(ol) {
  margin: 0.35em 0 1.35em;
  padding-left: 1.55em;
}

.detail-content :deep(li) {
  padding-left: 0.22em;
  margin: 0.45em 0;
}

.detail-content :deep(li::marker) {
  color: #789a78;
  font-weight: 650;
}

.detail-content :deep(strong),
.detail-content :deep(b) {
  color: #36563d;
  font-weight: 650;
}

.detail-content :deep(blockquote) {
  margin: 1.4em 0;
  padding: 0.85em 1.15em;
  border-left: 2px solid #8eaa88;
  border-radius: 0 10px 10px 0;
  background: #f3f6ef;
  color: #637967;
}

.detail-content :deep(blockquote > :last-child) { margin-bottom: 0; }

.detail-content :deep(a) {
  color: #426b4b;
  text-decoration-color: #a7bea1;
  text-underline-offset: 3px;
}

.detail-content :deep(code) {
  padding: 0.12em 0.38em;
  border-radius: 5px;
  background: #f0f3eb;
  color: #48674c;
  font-size: 0.9em;
}

.detail-content :deep(pre) {
  overflow-x: auto;
  margin: 1.25em 0;
  padding: 1em 1.1em;
  border-radius: 11px;
  background: #f2f4ee;
}

.detail-content :deep(pre code) { padding: 0; background: none; }

.detail-content :deep(table) {
  width: 100%;
  margin: 1.2em 0;
  border-collapse: collapse;
  font-size: 0.94em;
}

.detail-content :deep(th),
.detail-content :deep(td) {
  padding: 0.65em 0.8em;
  border-bottom: 1px solid #e5eade;
  text-align: left;
}

.detail-content :deep(th) { color: #36563d; background: #f4f6f0; }

.article-ai-nudge {
  display:flex; align-items:center; justify-content:space-between; gap:18px;
  margin-top:24px; padding:17px 19px; border:1px solid #e0e8db; border-radius:16px;
  background:linear-gradient(130deg,#f2f6ed,#faf8f1);
}
.article-ai-copy span { color:#45694a; font-size:12px; font-weight:650; }
.article-ai-copy p { margin:5px 0 0; color:#819082; font-size:11px; line-height:1.6; }
.article-ai-button {
  display:inline-flex; align-items:center; justify-content:center; gap:8px; flex:none;
  min-height:40px; padding:0 14px; border:1px solid #426b4b; border-radius:12px;
  background:#426b4b; color:#fff; font:600 11px/1.2 'Inter','Noto Sans SC',sans-serif;
  cursor:pointer; transition:background .2s ease,transform .2s ease,box-shadow .2s ease;
}
.article-ai-button:hover { transform:translateY(-2px); background:#31563b; box-shadow:0 7px 17px #426b4b2b; }

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 22px;
}

.tag-pill {
  font-size: 12px;
  color: #527757;
  background: #edf3e9;
  padding: 5px 10px;
  border-radius: 6px;
}

.detail-foot {
  margin-top: 30px;
  padding-top: 16px;
  border-top: 1px solid rgba(226, 232, 240, 0.9);
  text-align: center;
}

.foot-note {
  font-size: 12px;
  color: #a4abb7;
  letter-spacing: 1px;
}

@media (max-width: 860px) {
  .detail-card {
    padding: 24px 20px;
  }

  .article-ai-nudge { align-items:flex-start; flex-direction:column; gap:13px; padding:15px; }
  .article-ai-button { width:100%; }

  .detail-title {
    font-size: 21px;
  }
}
/* iPhone 窄屏：紧凑卡片与留白，UI 不变仅防错乱 */
@media (max-width: 520px) {
  .detail-page {
    min-height: 100dvh;
    height: 100dvh;
  }

  .detail-wrap {
    padding: 84px 14px 40px;
  }

  .detail-card {
    padding: 22px 18px;
    border-radius: 20px;
  }

  .detail-title {
    font-size: 19px;
  }
  .detail-whisper { margin-bottom:15px; font-size:21px; }
}
</style>
