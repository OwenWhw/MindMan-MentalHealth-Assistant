<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '@/components/PageHead.vue'
import TableSearch from '@/components/TableSearch.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { getAdminSessionPage, getAdminMessages, deleteAdminSession } from '@/api/consult'

const statusOptions = [
  { label: '进行中', value: 1 },
  { label: '已结束', value: 2 }
]

const searchFields = [
  { prop: 'keyword', label: '用户/会话', type: 'input', placeholder: '请输入用户昵称或会话ID' },
  { prop: 'status', label: '状态', type: 'select', options: statusOptions }
]

let query = reactive({ keyword: '', status: '' })

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = 8

const detailVisible = ref(false)
const currentSession = ref(null)
const messages = ref([])
const messageLoading = ref(false)

function emotionTagType(emotion) {
  const map = { 开心: 'success', 平静: 'info', 焦虑: 'warning', 难过: 'danger', 愤怒: 'danger' }
  return map[emotion] || 'info'
}

// 后端 LocalDateTime 序列化为 ISO（yyyy-MM-ddTHH:mm:ss），统一取时分秒展示
function formatTime(s) {
  if (!s) return ''
  const t = String(s).replace('T', ' ')
  return t.slice(11, 19) || t
}

async function loadSessions() {
  loading.value = true
  try {
    const data = await getAdminSessionPage({
      page: currentPage.value,
      pageSize,
      keyword: query.keyword || undefined,
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
  loadSessions().then(() => {
    ElMessage.success(`共找到 ${total.value} 条记录`)
  })
}

function handleReset() {
  currentPage.value = 1
  loadSessions()
}

async function openDetail(row) {
  currentSession.value = row
  messages.value = []
  detailVisible.value = true
  messageLoading.value = true
  try {
    messages.value = await getAdminMessages(row.sessionId)
  } catch (e) {
    messages.value = []
  } finally {
    messageLoading.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定要删除会话「${row.sessionId}」吗？删除后不可恢复。`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await deleteAdminSession(row.sessionId)
    if (tableData.value.length === 1 && currentPage.value > 1) {
      currentPage.value -= 1
    }
    ElMessage.success('删除成功')
    loadSessions()
  } catch (e) {
    if (!e?.handled) ElMessage.error(e.message || '删除失败')
  }
}

onMounted(loadSessions)
</script>

<template>
  <div class="page">
    <PageHead icon="ChatDotRound" title="咨询记录" />

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
        <el-table-column label="情绪标签" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="emotionTagType(row.emotion)" effect="light">
              {{ row.emotion }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'" effect="light">
              {{ row.statusText || (row.status === 1 ? '进行中' : '已结束') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后消息" min-width="320">
          <template #default="{ row }">
            <div class="last-message">
              <span class="msg-sender">{{ row.lastSender }} · {{ row.lastTime }}</span>
              <div class="msg-text">{{ row.lastMessage }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="messageCount" label="消息数" width="90" align="center" />
        <el-table-column prop="lastTime" label="时间" width="170" />
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
          @current-change="loadSessions"
        />
        <span v-else class="page-total">共 {{ total }} 条</span>
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" title="咨询会话详情" width="640px" top="6vh">
      <div v-if="currentSession" class="session-meta">
        <div class="meta-item"><span class="meta-label">用户</span><span class="meta-value">{{ currentSession.userName }}</span></div>
        <div class="meta-item"><span class="meta-label">开始时间</span><span class="meta-value">{{ currentSession.startedAt }}</span></div>
        <div class="meta-item"><span class="meta-label">消息数</span><span class="meta-value">{{ currentSession.messageCount }}</span></div>
        <el-tag size="small" :type="emotionTagType(currentSession.emotion)">{{ currentSession.emotion }}</el-tag>
        <span class="meta-session-id">会话ID：{{ currentSession.sessionId }}</span>
      </div>

      <h4 class="chat-title">对话记录</h4>
      <div
        v-loading="messageLoading"
        element-loading-text="正在加载对话..."
        element-loading-background="rgba(255, 255, 255, 0.8)"
        class="chat-box"
      >
        <div
          v-for="msg in messages"
          :key="msg.id"
          class="chat-row"
          :class="msg.role"
        >
          <div class="chat-bubble">
            <div class="chat-sender">
              <span class="sender-name">{{ msg.role === 'user' ? '用户' : 'AI助手' }}</span>
              <span class="sender-time">{{ formatTime(msg.createdAt) }}</span>
            </div>
            <div class="chat-content">{{ msg.content }}</div>
          </div>
        </div>
        <el-empty v-if="!messageLoading && !messages.length" description="暂无消息" :image-size="70" />
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

.last-message {
  min-width: 0;
}

.msg-sender {
  display: block;
  font-size: 12px;
  color: #9ca3af;
  margin-bottom: 2px;
}

.msg-text {
  color: #4b5563;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.session-meta {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
  padding: 10px 14px;
  margin-bottom: 14px;
  background: #f8fafc;
  border-radius: 8px;
  font-size: 13px;
  color: #4b5563;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.meta-label {
  color: #9ca3af;
}

.meta-value {
  color: #1f2937;
  font-weight: 500;
}

.meta-session-id {
  color: #9ca3af;
  font-size: 12px;
  margin-left: auto;
}

.chat-title {
  margin: 0 0 12px;
  font-size: 15px;
  color: #1f2937;
}

.chat-box {
  max-height: 420px;
  overflow-y: auto;
  padding: 8px 4px;
}

.chat-row {
  display: flex;
  margin-bottom: 14px;
}

.chat-row.user {
  justify-content: flex-end;
}

.chat-row.assistant {
  justify-content: flex-start;
}

.chat-bubble {
  max-width: 78%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.7;
}

.chat-row.user .chat-bubble {
  background: #2f6fdb;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.chat-row.assistant .chat-bubble {
  background: #f3f4f6;
  color: #1f2937;
  border-bottom-left-radius: 4px;
}

.chat-sender {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.sender-name {
  font-size: 12px;
  font-weight: 600;
  opacity: 0.9;
}

.sender-time {
  font-size: 11px;
  opacity: 0.7;
}

.chat-content {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
