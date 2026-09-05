<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import PageHead from '@/components/PageHead.vue'
import { getCategoryTree, saveCategory, updateCategory, deleteCategory } from '@/api/knowledge'

const loading = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const editingId = ref(null)
const formRef = ref()
const saving = ref(false)
const form = reactive({
  categoryName: '',
  description: '',
  sortOrder: 10,
  status: 1
})

const rules = {
  categoryName: [{ required: true, message: '请输入分类名称', trigger: 'blur' }]
}

async function loadCategories() {
  loading.value = true
  try {
    tableData.value = await getCategoryTree()
  } catch (e) {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { categoryName: '', description: '', sortOrder: 10, status: 1 })
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.categoryId
  Object.assign(form, {
    categoryName: row.categoryName,
    description: row.description || '',
    sortOrder: row.sortOrder,
    status: row.status
  })
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      categoryName: form.categoryName.trim(),
      description: form.description.trim(),
      sortOrder: Number(form.sortOrder),
      status: Number(form.status)
    }
    if (editingId.value) {
      await updateCategory({ categoryId: editingId.value, ...payload })
      ElMessage.success('分类更新成功')
    } else {
      await saveCategory(payload)
      ElMessage.success('分类创建成功')
    }
    dialogVisible.value = false
    loadCategories()
  } catch (e) {
    if (!e?.handled) ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定要删除分类「${row.categoryName}」吗？`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await deleteCategory(row.categoryId)
    ElMessage.success('删除成功')
    loadCategories()
  } catch (e) {
    if (!e?.handled) ElMessage.error(e.message || '删除失败')
  }
}

onMounted(loadCategories)
</script>

<template>
  <div class="page">
    <PageHead icon="Collection" title="分类管理">
      <el-button type="primary" :icon="Plus" @click="openCreate">新增分类</el-button>
    </PageHead>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column prop="categoryName" label="分类名称" min-width="140" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="90" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="articleCount" label="文章数" width="90" align="center" />
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column prop="updatedAt" label="更新时间" width="170" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑分类' : '新增分类'"
      width="460px"
      :close-on-click-modal="false"
      @closed="formRef?.resetFields()"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="分类名称" prop="categoryName">
          <el-input v-model="form.categoryName" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="请输入分类描述（可选）" />
        </el-form-item>
        <el-form-item label="排序值" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
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
</style>
