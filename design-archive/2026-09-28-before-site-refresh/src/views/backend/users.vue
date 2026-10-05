<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '@/components/PageHead.vue'
import TableSearch from '@/components/TableSearch.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { getAdminUsers, updateUserStatus, updateUserRole, deleteUser } from '@/api/admin'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
// 当前登录管理员 ID（用于禁止操作自身账号）
const currentUserId = computed(() => authStore.userInfo?.id)

const roleOptions = [
  { label: '普通用户', value: 'user' },
  { label: '管理员', value: 'admin' }
]
const statusOptions = [
  { label: '正常', value: 1 },
  { label: '已禁用', value: 0 }
]

const searchFields = [
  { prop: 'keyword', label: '用户', type: 'input', placeholder: '用户名/昵称/ID' },
  { prop: 'role', label: '角色', type: 'select', options: roleOptions },
  { prop: 'status', label: '状态', type: 'select', options: statusOptions }
]

let query = reactive({ keyword: '', role: '', status: '' })

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = 10

function roleTagType(role) {
  return role === 'admin' ? 'danger' : 'info'
}

async function loadUsers() {
  loading.value = true
  try {
    const data = await getAdminUsers({
      page: currentPage.value,
      pageSize,
      keyword: query.keyword || undefined,
      role: query.role || undefined,
      status: query.status === '' ? undefined : query.status
    })
    tableData.value = data.list || []
    total.value = data.total || 0
  } catch (e) {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadUsers().then(() => ElMessage.success(`共找到 ${total.value} 名用户`))
}

function handleReset() {
  currentPage.value = 1
  loadUsers()
}

async function handleStatusChange(row, targetStatus) {
  if (row.id === currentUserId.value) {
    ElMessage.warning('不能修改自己的账号状态')
    return
  }
  const prev = row.status
  try {
    await updateUserStatus(row.id, targetStatus)
    row.status = targetStatus
    ElMessage.success(targetStatus === 1 ? '已启用' : '已禁用')
  } catch (e) {
    if (!e?.handled) ElMessage.error(e.message || '操作失败')
    row.status = prev
  }
}

async function handleRoleChange(row, role) {
  if (row.id === currentUserId.value) {
    ElMessage.warning('不能修改自己的角色')
    return
  }
  if (row.role === role) return
  try {
    await updateUserRole(row.id, role)
    ElMessage.success('角色已更新')
    loadUsers()
  } catch (e) {
    if (!e?.handled) ElMessage.error(e.message || '操作失败')
  }
}

async function handleDelete(row) {
  if (row.id === currentUserId.value) {
    ElMessage.warning('不能删除自己的账号')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要删除用户「${row.nickname || row.username}」吗？删除后不可恢复。`,
      '删除确认',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await deleteUser(row.id)
    if (tableData.value.length === 1 && currentPage.value > 1) {
      currentPage.value -= 1
    }
    ElMessage.success('删除成功')
    loadUsers()
  } catch (e) {
    if (!e?.handled) ElMessage.error(e.message || '删除失败')
  }
}

onMounted(loadUsers)
</script>

<template>
  <div class="page">
    <PageHead icon="User" title="用户管理" />

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
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="用户" min-width="170">
          <template #default="{ row }">
            <UserAvatar :name="row.role === 'admin' ? '管理员' : (row.nickname || row.username)" :user-id="row.id" :avatar="row.avatar" />
          </template>
        </el-table-column>
        <el-table-column prop="username" label="账号" width="130" />
        <el-table-column prop="nickname" label="昵称" width="120" />
        <el-table-column prop="email" label="邮箱" min-width="190" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column label="角色" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="roleTagType(row.role)" effect="light">
              {{ row.role === 'admin' ? '管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              :disabled="row.id === currentUserId"
              @change="(val) => handleStatusChange(row, val ? 1 : 0)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <div style="display:inline-flex;align-items:center;gap:6px;white-space:nowrap">
              <el-dropdown trigger="click" @command="(cmd) => handleRoleChange(row, cmd)">
                <el-button link type="primary" size="small">设角色</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="user" :disabled="row.role === 'user'">设为普通用户</el-dropdown-item>
                    <el-dropdown-item command="admin" :disabled="row.role === 'admin'">设为管理员</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button
                link
                type="danger"
                size="small"
                :disabled="row.id === currentUserId"
                @click="handleDelete(row)"
              >
                删除
              </el-button>
            </div>
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
          @current-change="loadUsers"
        />
        <span v-else class="page-total">共 {{ total }} 名用户</span>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.row-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
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
</style>
