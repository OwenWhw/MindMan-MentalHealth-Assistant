<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '@/components/PageHead.vue'
import TableSearch from '@/components/TableSearch.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { getDiaryPage, deleteDiary } from '@/api/emotion'

const scoreRangeOptions = [
  { label: '全部', value: '' },
  { label: '1-2 分', value: '1-2' },
  { label: '3 分', value: '3' },
  { label: '4-5 分', value: '4-5' }
]

const searchFields = [
  { prop: 'userId', label: '用户ID', type: 'input', placeholder: '请输入用户ID' },
  { prop: 'scoreRange', label: '情绪评分', type: 'select', options: scoreRangeOptions }
]

let query = reactive({ userId: '', scoreRange: '' })

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = 8

const detailVisible = ref(false)
const currentDiary = ref(null)

function emotionTagType(emotion) {
  const map = { 开心: 'success', 平静: 'info', 焦虑: 'warning', 难过: 'danger', 愤怒: 'danger' }
  return map[emotion] || 'info'
}

async function loadDiaries() {
  loading.value = true
  try {
    const data = await getDiaryPage({
      page: currentPage.value,
      pageSize,
      userId: query.userId,
      scoreRange: query.scoreRange === '' ? undefined : query.scoreRange
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
  loadDiaries().then(() => {
    ElMessage.success(`共找到 ${total.value} 条记录`)
  })
}

function handleReset() {
  currentPage.value = 1
  loadDiaries()
}

function openDetail(row) {
  currentDiary.value = row
  detailVisible.value = true
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定要删除这条情绪日志吗？删除后不可恢复。`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await deleteDiary(row.diaryId)
    if (tableData.value.length === 1 && currentPage.value > 1) {
      currentPage.value -= 1
    }
    ElMessage.success('删除成功')
    loadDiaries()
  } catch (e) {
    if (!e?.handled) ElMessage.error(e.message || '删除失败')
  }
}

onMounted(loadDiaries)
</script>

<template>
  <div class="page">
    <PageHead icon="Sunny" title="情绪日志" />

    <TableSearch
      v-model="query"
      :fields="searchFields"
      :loading="loading"
      @search="handleSearch"
      @reset="handleReset"
    />

    <el-card shadow="never">
      <el-table
        v-loading="loading"
        element-loading-text="加载中..."
        element-loading-background="rgba(255, 255, 255, 0.75)"
        :data="tableData"
        stripe
      >
        <el-table-column prop="sessionId" label="会话ID" width="100" />
        <el-table-column label="用户" width="110">
          <template #default="{ row }">
            <UserAvatar :name="row.userName" :user-id="row.userId" :avatar="row.avatar" />
          </template>
        </el-table-column>
        <el-table-column prop="recordDate" label="记录日期" width="110" />
        <el-table-column label="情绪评分" width="170" align="center">
          <template #default="{ row }">
            <el-rate :model-value="row.emotionScore" disabled />
          </template>
        </el-table-column>
        <el-table-column label="生活指标" width="150">
          <template #default="{ row }">
            <div class="life-indicators">
              <span>睡眠 {{ row.sleepScore }}/5</span>
              <span>压力 {{ row.stressScore }}/5</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="trigger" label="情绪触发因素" min-width="130" show-overflow-tooltip />
        <el-table-column prop="content" label="日记内容" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
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
          @current-change="loadDiaries"
        />
        <span v-else class="page-total">共 {{ total }} 条</span>
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" title="情绪日志详情" width="520px" top="10vh">
      <div v-if="currentDiary" class="detail-body">
        <div class="detail-header">
          <UserAvatar
            :name="currentDiary.userName"
            :user-id="currentDiary.userId"
            :avatar="currentDiary.avatar"
            :size="42"
          />
          <div class="detail-user">
            <div class="detail-name">{{ currentDiary.userName }} <span class="detail-id">#{{ currentDiary.userId }}</span></div>
            <div class="detail-date">{{ currentDiary.recordDate }}</div>
          </div>
          <el-tag size="small" :type="emotionTagType(currentDiary.emotion)">{{ currentDiary.emotion }}</el-tag>
        </div>

        <div class="detail-row">
          <span class="detail-label">情绪评分</span>
          <el-rate :model-value="currentDiary.emotionScore" disabled />
        </div>
        <div class="detail-row">
          <span class="detail-label">生活指标</span>
          <span>睡眠 {{ currentDiary.sleepScore }}/5 · 压力 {{ currentDiary.stressScore }}/5</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">触发因素</span>
          <span>{{ currentDiary.trigger }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">会话ID</span>
          <span>{{ currentDiary.sessionId }}</span>
        </div>
        <div class="detail-content">
          <div class="detail-label">日记内容</div>
          <p>{{ currentDiary.content }}</p>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.user-name {
  font-weight: 500;
  color: #1f2937;
}

.user-id {
  margin-left: 4px;
  font-size: 12px;
  color: #9ca3af;
}

.life-indicators {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 13px;
  color: #4b5563;
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

.detail-body {
  padding: 0 4px;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  background: #f8fafc;
  border-radius: 10px;
  margin-bottom: 16px;
}

.detail-user {
  flex: 1;
}

.detail-name {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

.detail-id {
  font-size: 12px;
  color: #9ca3af;
  font-weight: 400;
}

.detail-date {
  font-size: 12px;
  color: #9ca3af;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 0;
  font-size: 14px;
  color: #374151;
  border-bottom: 1px dashed #e5e7eb;
}

.detail-label {
  width: 80px;
  flex-shrink: 0;
  color: #6b7280;
}

.detail-content {
  margin-top: 14px;
}

.detail-content p {
  margin: 8px 0 0;
  padding: 12px 14px;
  background: #f8fafc;
  border-radius: 8px;
  color: #374151;
  line-height: 1.8;
}
</style>
