<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getArticleDetail } from '@/api/knowledge'
import AppNavBar from '@/components/AppNavBar.vue'
import UserDropdown from '@/components/UserDropdown.vue'

const route = useRoute()
const router = useRouter()

const article = ref(null)
const loading = ref(true)
const scrollRef = ref()

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
      :actions="[
        { key: 'consult', title: 'AI 咨询', icon: 'ChatDotRound', path: '/consult' },
        { key: 'garden', title: '情绪花园', icon: 'Cherry', path: '/garden' },
        { key: 'relax', title: '白噪音空间', icon: 'WindPower', path: '/relax' },
        { key: 'home', title: '回到主页', icon: 'HomeFilled', path: '/home' }
      ]"
      :current-path="route.path"
    >
      <template #brand>
        <router-link to="/home/articles" class="art-brand">
          <span class="art-name">知识文章</span>
          <span class="art-sub">用知识温暖每一颗心</span>
        </router-link>
      </template>
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

        <div class="detail-content">{{ article.content }}</div>

        <div v-if="tagsList.length" class="detail-tags">
          <span v-for="t in tagsList" :key="t" class="tag-pill">#{{ t }}</span>
        </div>

        <div class="detail-foot">
          <span class="foot-note">
            {{ isLive ? 'MindMan · 实时心理精选 · 每日 04:30 自动更新' : '心理健康助手 · 让知识陪伴每一天' }}
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
    radial-gradient(620px 420px at 88% 10%, rgba(96, 165, 250, 0.1), transparent 65%),
    linear-gradient(180deg, #f0f7ff 0%, #eaf4ff 55%, #f8fafc 100%);
}

.art-brand {
  display: flex;
  flex-direction: column;
  gap: 2px;
  text-decoration: none;
}

.art-name {
  font-size: 18px;
  font-weight: 800;
  color: #111827;
  letter-spacing: 1px;
}

.art-sub {
  font-size: 11px;
  color: #94a3b8;
  letter-spacing: 0.5px;
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
  color: #2f6fdb;
  border-color: #93c5fd;
  transform: translateX(-2px);
}

.loading,
.empty-block {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 420px;
  font-size: 24px;
  color: #2f6fdb;
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
  border: 1px solid rgba(16, 185, 129, 0.4);
  box-shadow: 0 24px 70px rgba(16, 185, 129, 0.16);
}

.detail-cat {
  display: inline-block;
  font-size: 12px;
  color: #2f6fdb;
  background: rgba(47, 111, 219, 0.08);
  padding: 4px 13px;
  border-radius: 999px;
  font-weight: 600;
  letter-spacing: 1px;
  margin-bottom: 16px;
}
.detail-cat-live {
  color: #047857;
  background: rgba(16, 185, 129, 0.12);
  border: 1px solid rgba(16, 185, 129, 0.3);
}

.detail-title {
  margin: 0 0 14px;
  font-size: 26px;
  font-weight: 800;
  color: #111827;
  line-height: 1.45;
}

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
  color: #10b981;
  font-weight: 600;
  cursor: pointer;
  border-bottom: 1px dashed rgba(16, 185, 129, 0.5);
  padding-bottom: 1px;
}
.meta-source:hover { color: #059669; border-bottom-style: solid; }
.meta-source .el-icon { font-size: 13px; }

.detail-summary {
  margin: 0 0 20px;
  padding: 13px 16px;
  border-left: 3px solid #2f6fdb;
  border-radius: 0 12px 12px 0;
  background: rgba(47, 111, 219, 0.05);
  font-size: 14px;
  line-height: 1.85;
  color: #475569;
}

.detail-content {
  font-size: 15px;
  line-height: 2;
  color: #334155;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 22px;
}

.tag-pill {
  font-size: 12px;
  color: #2f6fdb;
  background: rgba(47, 111, 219, 0.07);
  padding: 4px 11px;
  border-radius: 999px;
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

  .detail-title {
    font-size: 21px;
  }
}
/* iPhone 窄屏：紧凑卡片与留白，UI 不变仅防错乱 */
@media (max-width: 520px) {
  .detail-page {
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
}
</style>
