<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import PageHead from '@/components/PageHead.vue'
import TableSearch from '@/components/TableSearch.vue'
import ArticleFormDialog from '@/components/ArticleFormDialog.vue'
import { getCategoryTree, getArticlePage, getArticleDetail, updateArticleStatus, deleteArticle } from '@/api/knowledge'

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
})
</script>

<template>
  <div class="page">
    <PageHead icon="Document" title="知识文章">
      <el-button type="primary" :icon="Plus" @click="openCreate">新增文章</el-button>
    </PageHead>

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

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.page-total {
  font-size: 14px;
  color: #6b7280;
}
</style>
