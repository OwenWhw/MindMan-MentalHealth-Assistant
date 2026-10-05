<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getArticlePage,
  getCategoryTree
} from '@/api/knowledge'
import AppNavBar from '@/components/AppNavBar.vue'
import UserDropdown from '@/components/UserDropdown.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const articles = ref([])
const total = ref(0)
const totalAll = ref(0)
const page = ref(1)
const pageSize = 8

const categories = ref([])
const activeCat = ref(null) // null = 全部
const keyword = ref('')

const navActions = [
  { key: 'consult', title: 'AI 咨询', icon: 'ChatDotRound', path: '/consult' },
  { key: 'garden',  title: '情绪花园', icon: 'Cherry',       path: '/garden' },
  { key: 'relax',   title: '白噪音空间', icon: 'WindPower',  path: '/relax' },
  { key: 'home',    title: '回到主页', icon: 'HomeFilled',   path: '/home' }
]

async function loadCategories() {
  try {
    categories.value = await getCategoryTree()
  } catch (e) {
    categories.value = []
  }
}

async function loadArticles() {
  loading.value = true
  try {
    const params = { page: page.value, pageSize }
    if (activeCat.value) params.categoryId = activeCat.value
    if (keyword.value) params.keyword = keyword.value
    const data = await getArticlePage(params)
    articles.value = data.list || []
    total.value = data.total || 0
  } catch (e) {
    articles.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 全部文章的真实总数：单独查询一次，不跟随当前筛选变化
async function loadTotalAll() {
  try {
    const data = await getArticlePage({ page: 1, pageSize: 1 })
    totalAll.value = data?.total || 0
  } catch (e) {
    totalAll.value = 0
  }
}

function setCategory(catId) {
  activeCat.value = catId
  page.value = 1
}

function openArticle(article) {
  router.push(`/home/articles/${article.articleId}`)
}

watch([activeCat, keyword], () => loadArticles())
watch(page, () => loadArticles())

onMounted(async () => {
  await loadCategories()
  await loadArticles()
  loadTotalAll()
})

// 高亮关键词
function highlight(text) {
  if (!keyword.value) return text
  const re = new RegExp(`(${keyword.value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi')
  return String(text || '').replace(re, '<mark>$1</mark>')
}

const empty = computed(() => !loading.value && !articles.value.length)
</script>

<template>
  <div class="articles-page">
    <!-- 顶部导航 -->
    <AppNavBar :actions="navActions" :current-path="route.path">
      <template #brand>
        <router-link to="/home/articles" class="art-brand">
          <span class="art-name">知识文章</span>
          <span class="art-sub">用知识温暖每一颗心</span>
        </router-link>
      </template>
      <template #extra>
        <div class="nav-search">
          <el-input
            v-model="keyword"
            placeholder="搜索文章标题、关键词…"
            clearable
            size="default"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>
      </template>
      <template #actions-after>
        <UserDropdown />
      </template>
    </AppNavBar>

    <!-- 主体 -->
    <div class="page-body">
      <!-- 左侧分类 -->
      <aside class="cat-rail">
        <div class="rail-title">分类</div>
        <div
          class="cat-item"
          :class="{ active: activeCat === null }"
          @click="setCategory(null)"
        >
          <span class="cat-name">全部文章</span>
          <span class="cat-count">{{ totalAll }}</span>
        </div>
        <div
          v-for="c in categories"
          :key="c.categoryId"
          class="cat-item"
          :class="{ active: activeCat === c.categoryId }"
          @click="setCategory(c.categoryId)"
        >
          <span class="cat-name">{{ c.categoryName }}</span>
          <span class="cat-count">{{ c.articleCount || 0 }}</span>
        </div>
      </aside>

      <!-- 右侧文章列表 -->
      <main class="article-main">
        <div v-if="loading" class="loading">
          <el-icon class="is-loading"><Loading /></el-icon>
        </div>
        <div v-else-if="empty" class="empty-block">
          <el-empty description="暂无相关文章，换个关键词试试？" :image-size="100" />
        </div>
        <div v-else class="article-grid">
          <div
            v-for="article in articles"
            :key="article.articleId"
            class="article-card"
            :class="{ 'is-live': article.sourceType === 'crawled' }"
            @click="openArticle(article)"
          >
            <div class="card-cover" :class="{ 'card-cover-live': article.sourceType === 'crawled' }">
              <span class="cover-cat" :class="{ 'cover-cat-live': article.sourceType === 'crawled' }">
                <template v-if="article.sourceType === 'crawled'">
                  🛰 实时 · {{ article.sourceName || '实时心理' }}
                </template>
                <template v-else>
                  {{ article.categoryName }}
                </template>
              </span>
              <el-icon class="cover-icon"><component
                :is="article.categoryId === 1 ? 'User'
                  : article.categoryId === 2 ? 'Watermelon'
                  : article.categoryId === 3 ? 'Sunrise'
                  : article.categoryId === 4 ? 'ChatDotRound'
                  : article.categoryId === 5 ? 'Reading'
                  : article.categoryId === 6 ? 'Briefcase'
                  : article.categoryId === 7 ? 'Refresh'
                  : 'Mug'"
              /></el-icon>
            </div>
            <div class="card-body">
              <h3 class="card-title" v-html="highlight(article.title)"></h3>
              <p class="card-summary">
                {{ article.summary || '点击阅读全文，了解更多心理健康小知识。' }}
              </p>
              <div class="card-meta">
                <span class="meta-item">
                  <el-icon><View /></el-icon>
                  {{ article.reads }}
                </span>
                <span class="meta-item">
                  <el-icon><Clock /></el-icon>
                  {{ article.publishTime?.slice(0, 10) }}
                </span>
                <span class="meta-author">@{{ article.author }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 分页 -->
        <div v-if="total > pageSize" class="pager">
          <el-pagination
            v-model:current-page="page"
            :page-size="pageSize"
            :total="total"
            layout="prev, pager, next, jumper"
            background
          />
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.articles-page {
  height: 100vh;
  overflow-y: auto;
  background: linear-gradient(180deg, #f0f7ff 0%, #eaf4ff 55%, #f8fafc 100%);
}

/* ===== 文章品牌 + 搜索 ===== */
.art-brand { display: flex; flex-direction: column; gap: 2px; text-decoration: none; }
.art-name { font-size: 18px; font-weight: 800; color: #111827; letter-spacing: 1px; }
.art-sub  { font-size: 11px; color: #94a3b8; letter-spacing: 0.5px; }

.nav-search { flex: 1; min-width: 120px; max-width: 360px; }
.nav-search :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(14px);
  border-radius: 12px;
  box-shadow: 0 0 0 1px rgba(226, 232, 240, 0.85) inset;
}

/* ===== 主体 ===== */
.page-body {
  display: grid;
  grid-template-columns: 200px 1fr;
  gap: 24px;
  align-items: flex-start;
  padding: 96px 28px 48px;
  max-width: 1180px;
  margin: 0 auto;
  width: 100%;
}

/* 左侧分类 */
.cat-rail {
  position: sticky;
  top: 24px;
  padding: 16px 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(22px);
  border: 1px solid rgba(255, 255, 255, 0.9);
  box-shadow: 0 12px 32px rgba(15, 118, 110, 0.06);
}
.rail-title {
  padding: 0 8px 10px;
  font-size: 11px;
  color: #94a3b8;
  letter-spacing: 2px;
  text-transform: uppercase;
}
.cat-item {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 12px;
  border-radius: 12px;
  font-size: 13px;
  color: #475569;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 2px;
}
.cat-item:hover { background: rgba(99, 102, 241, 0.06); color: #4338ca; }
.cat-item.active {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.12), rgba(59, 130, 246, 0.08));
  color: #4338ca;
  font-weight: 600;
}
.cat-emoji { display: none; }
.cat-name  { flex: 1; min-width: 0; }
.cat-count {
  font-size: 11px;
  color: #94a3b8;
  background: rgba(148, 163, 184, 0.14);
  padding: 1px 7px;
  border-radius: 999px;
  font-variant-numeric: tabular-nums;
}

/* 右侧文章 */
.article-main { min-height: 480px; }

.article-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 18px;
}

.article-card {
  display: flex;
  flex-direction: column;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(22px);
  border: 1px solid rgba(255, 255, 255, 0.9);
  box-shadow: 0 8px 24px rgba(15, 118, 110, 0.06);
  cursor: pointer;
  transition: all 0.25s ease;
  overflow: hidden;
}
.article-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 16px 40px rgba(15, 118, 110, 0.12);
  border-color: rgba(99, 102, 241, 0.3);
}
.article-card.is-live {
  border: 1px solid rgba(16, 185, 129, 0.3);
  box-shadow: 0 8px 24px rgba(16, 185, 129, 0.10);
}
.article-card.is-live:hover {
  box-shadow: 0 16px 40px rgba(16, 185, 129, 0.18);
  border-color: rgba(16, 185, 129, 0.5);
}

.card-cover {
  position: relative;
  height: 110px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 14px 16px;
  background: linear-gradient(135deg, #dbeafe 0%, #ede9fe 50%, #dbeafe 100%);
}
.card-cover-live {
  background: linear-gradient(135deg, #d1fae5 0%, #a7f3d0 50%, #6ee7b7 100%);
}
.cover-cat {
  font-size: 11px;
  color: #4338ca;
  background: rgba(255, 255, 255, 0.85);
  padding: 3px 10px;
  border-radius: 999px;
  font-weight: 600;
  letter-spacing: 0.5px;
}
.cover-cat-live {
  color: #047857;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(16, 185, 129, 0.3);
}
.cover-icon {
  font-size: 36px;
  align-self: center;
  filter: drop-shadow(0 4px 8px rgba(0, 0, 0, 0.1));
}

.card-body { padding: 14px 16px 16px; display: flex; flex-direction: column; flex: 1; }
.card-title {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 700;
  color: #111827;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.card-title :deep(mark) {
  background: rgba(251, 191, 36, 0.3);
  color: #b45309;
  padding: 0 2px;
  border-radius: 3px;
}
.card-summary {
  margin: 0 0 12px;
  font-size: 12.5px;
  line-height: 1.7;
  color: #64748b;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  flex: 1;
}
.card-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 10px;
  border-top: 1px dashed rgba(226, 232, 240, 0.9);
  font-size: 11px;
  color: #94a3b8;
}
.meta-item { display: inline-flex; align-items: center; gap: 4px; }
.meta-item .el-icon { font-size: 12px; }
.meta-author {
  margin-left: auto;
  color: #6366f1;
  font-weight: 600;
}

.loading, .empty-block {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 360px;
  font-size: 22px;
  color: #6366f1;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 28px;
}

/* ===== 响应式 ===== */
@media (max-width: 900px) {
  .page-body { grid-template-columns: 1fr; }
  .cat-rail { position: static; }
  .article-grid { grid-template-columns: 1fr; }
  .head-search { width: 200px; }
}
/* iPhone 窄屏：隐藏搜索、紧凑留白，UI 不变仅防错乱 */
@media (max-width: 520px) {
  .articles-page {
    height: 100dvh;
  }

  .nav-search {
    display: none;
  }

  .page-body {
    padding: 84px 12px 36px;
    gap: 14px;
  }

  .cat-rail {
    padding: 12px;
  }

  .article-grid {
    gap: 12px;
  }

  .article-card {
    border-radius: 16px;
  }
}
</style>
