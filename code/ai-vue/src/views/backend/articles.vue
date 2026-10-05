<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, RefreshRight, Link } from '@element-plus/icons-vue'
import PageHead from '@/components/PageHead.vue'
import TableSearch from '@/components/TableSearch.vue'
import ArticleFormDialog from '@/components/ArticleFormDialog.vue'
import { getCategoryTree, getArticlePage, getArticleDetail, updateArticleStatus, deleteArticle, getCrawlerStatus, runCrawlerOnce } from '@/api/knowledge'

const statusOptions = [
  { label: '已发布', value: 1 },
  { label: '已下线', value: 0 }
]

const categoryOptions = ref([])

const searchFields = computed(() => [
  { prop: 'title', label: '文章标题', type: 'input', placeholder: '请输入文章标题' },
  { prop: 'categoryId', label: '分类', type: 'select', options: categoryOptions.value },
  { prop: 'status', label: '状态', type: 'select', options: statusOptions }
])

let query = reactive({ title: '', categoryId: '', status: '' })

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = 8
const formVisible = ref(false)
const editingArticle = ref(null)
const crawlerStatus = ref(null)
const crawlerLoading = ref(false)

async function loadCrawlerStatus() {
  try {
    crawlerStatus.value = await getCrawlerStatus()
  } catch {
    crawlerStatus.value = null
  }
}

async function handleCrawlerRun() {
  crawlerLoading.value = true
  try {
    const response = await runCrawlerOnce()
    const run = response?.run
    if (run?.status === 'SKIPPED') ElMessage.info(run.errorSummary || '同步任务正在运行')
    else if (run?.status === 'DISABLED') ElMessage.warning('文章自动同步已关闭')
    else if (run?.status === 'FAILED') ElMessage.error(run.errorSummary || '本次同步失败')
    else ElMessage.success(`同步完成：新增 ${run?.importedCount || 0} 篇，更新 ${run?.updatedCount || 0} 篇`)
    await Promise.all([loadCrawlerStatus(), loadArticles()])
  } catch {
    // Error toast is handled by the shared request layer.
  } finally {
    crawlerLoading.value = false
  }
}

function formatCheckedAt(value) {
  return value ? value.replace('T', ' ') : '尚未同步'
}

async function loadCategories() {
  try {
    const tree = await getCategoryTree()
    const options = []
    const walk = (list) => {
      list.forEach((item) => {
        options.push({ label: item.categoryName, value: item.categoryId })
        if (item.children && item.children.length) walk(item.children)
      })
    }
    walk(tree)
    categoryOptions.value = options
  } catch (e) {
    categoryOptions.value = []
  }
}

async function loadArticles() {
  loading.value = true
  try {
    const data = await getArticlePage({
      page: currentPage.value,
      pageSize,
      title: query.title,
      categoryId: query.categoryId === '' ? undefined : query.categoryId,
      status: query.status === '' ? undefined : query.status
    })
    tableData.value = data.list
    total.value = data.total
  } catch (e) {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadArticles().then(() => {
    ElMessage.success(`共找到 ${total.value} 条文章`)
  })
}

function handleReset() {
  currentPage.value = 1
  loadArticles()
}

async function handleToggle(row) {
  const nextStatus = row.status === 1 ? 0 : 1
  try {
    await updateArticleStatus({ articleId: row.articleId, status: nextStatus })
    row.status = nextStatus
    ElMessage.success(nextStatus === 1 ? '文章已发布' : '文章已下线')
  } catch (e) {
    // 错误提示已由请求层统一处理
  }
}

function openCreate() {
  editingArticle.value = null
  formVisible.value = true
}

async function handleEdit(row) {
  try {
    const detail = await getArticleDetail(row.articleId)
    editingArticle.value = detail
    formVisible.value = true
  } catch (e) {
    if (!e?.handled) ElMessage.error(e.message || '获取文章详情失败')
  }
}

function handleSaved() {
  currentPage.value = 1
  loadArticles()
  loadCategories()
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定要删除文章「${row.title}」吗？删除后不可恢复。`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await deleteArticle(row.articleId)
    if (tableData.value.length === 1 && currentPage.value > 1) {
      currentPage.value -= 1
    }
    ElMessage.success('删除成功')
    loadArticles()
  } catch (e) {
    // 错误提示已由请求层统一处理
  }
}

onMounted(() => {
  loadCategories()
  loadArticles()
  loadCrawlerStatus()
})
</script>

<template>
  <div class="page">
    <PageHead icon="Document" title="知识文章">
      <el-button type="primary" :icon="Plus" @click="openCreate">新增文章</el-button>
    </PageHead>

    <el-card class="crawler-card" shadow="never">
      <div class="crawler-head">
        <div>
          <span class="crawler-eyebrow">可信来源 · 自动更新</span>
          <h3>心理资讯订阅</h3>
          <p>每日从 APA 官方 RSS 获取英文心理学新闻与来源摘要，保留原文链接；重复内容自动识别，源内容有变化时更新文章记录。</p>
        </div>
        <el-button
          :icon="RefreshRight"
          type="primary"
          plain
          :loading="crawlerLoading"
          :disabled="crawlerStatus && !crawlerStatus.enabled"
          @click="handleCrawlerRun"
        >立即同步</el-button>
      </div>

      <div class="feed-list">
        <div v-for="feed in crawlerStatus?.feeds || []" :key="feed.feedUrl" class="feed-row">
          <div class="feed-name"><el-icon><Link /></el-icon><span>{{ feed.name }}</span></div>
          <span class="feed-time">上次成功：{{ formatCheckedAt(feed.lastSuccessfulAt) }}</span>
        </div>
        <div v-if="!crawlerStatus" class="feed-row feed-muted">同步状态暂不可用，请确认数据库迁移已执行并重新启动后端。</div>
      </div>

      <div v-if="crawlerStatus?.lastRun" class="crawler-last-run">
        <span>最近一次：{{ crawlerStatus.lastRun.status === 'SUCCESS' ? '完成' : crawlerStatus.lastRun.status }}</span>
        <span>新增 {{ crawlerStatus.lastRun.importedCount }} 篇</span>
        <span>更新 {{ crawlerStatus.lastRun.updatedCount }} 篇</span>
        <span>过滤 {{ crawlerStatus.lastRun.filteredCount }} 条</span>
        <span v-if="crawlerStatus.lastRun.failedCount" class="feed-error">失败 {{ crawlerStatus.lastRun.failedCount }} 项：{{ crawlerStatus.lastRun.errorSummary }}</span>
        <span v-else-if="crawlerStatus.lastRun.skippedCount" class="feed-muted">来源在 20 小时内已检查，已跳过重复请求。</span>
      </div>
    </el-card>

    <TableSearch
      v-model="query"
      :fields="searchFields"
      @search="handleSearch"
      @reset="handleReset"
    />

    <el-card shadow="never">
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column prop="title" label="文章标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="categoryName" label="分类" width="120" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '已发布' : '已下线' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="author" label="作者" width="120" />
        <el-table-column prop="reads" label="阅读量" width="90" align="center" />
        <el-table-column prop="publishTime" label="发布时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button
              link
              :type="row.status === 1 ? 'warning' : 'success'"
              size="small"
              @click="handleToggle(row)"
            >
              {{ row.status === 1 ? '下线' : '上线' }}
            </el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-if="total > pageSize"
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          background
          @current-change="loadArticles"
        />
        <span v-else class="page-total">共 {{ total }} 条</span>
      </div>
    </el-card>

    <ArticleFormDialog
      v-model="formVisible"
      :article="editingArticle"
      @saved="handleSaved"
    />
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.crawler-card { border: 1px solid #e4ebdf; border-radius: 14px; background: linear-gradient(135deg, #fbfcf8, #f3f7ef); }
.crawler-head { display:flex; justify-content:space-between; align-items:flex-start; gap:18px; }
.crawler-eyebrow { color:#668568; font-size:12px; font-weight:650; letter-spacing:.06em; }
.crawler-head h3 { margin:6px 0 5px; color:#283c30; font-size:18px; }
.crawler-head p { max-width:760px; margin:0; color:#758174; font-size:13px; line-height:1.7; }
.feed-list { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:8px 18px; margin-top:16px; }
.feed-row { display:flex; justify-content:space-between; align-items:center; gap:12px; min-width:0; padding:9px 11px; border:1px solid #e8eee3; border-radius:10px; background:#fff; font-size:12px; }
.feed-name { display:flex; align-items:center; gap:7px; min-width:0; color:#4d7053; font-weight:600; }
.feed-name span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.feed-time,.feed-muted { color:#909a90; }
.crawler-last-run { display:flex; flex-wrap:wrap; gap:8px 18px; margin-top:12px; color:#758174; font-size:12px; }
.feed-error { color:#bd6b5c; }

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.page-total {
  font-size: 14px;
  color: #6b7280;
}

@media(max-width:720px) {
  .crawler-head { flex-direction:column; }
  .crawler-head .el-button { width:100%; }
  .feed-list { grid-template-columns:1fr; }
  .feed-row { align-items:flex-start; flex-direction:column; gap:5px; }
}
</style>
