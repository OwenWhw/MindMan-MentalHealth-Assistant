<script setup>
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { getCategoryTree, saveArticle, updateArticle } from '@/api/knowledge'
import { uploadFile } from '@/api/file'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  // 编辑时传入文章行数据，新增时为 null
  article: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const formRef = ref()
const saving = ref(false)
const uploading = ref(false)
const categoryOptions = ref([])
const editorRef = ref()
const fontSize = ref('3')
const fontName = ref('')

const tagSuggestions = ['心理健康', '压力管理', '情绪调节', '青少年心理', '正念冥想', '睡眠健康']
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']
const ALLOWED_IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp', 'gif']
const MAX_IMAGE_SIZE = 5 * 1024 * 1024

function isValidImage(file) {
  if (ALLOWED_IMAGE_TYPES.includes(file.type)) return true
  const ext = String(file.name || '').split('.').pop().toLowerCase()
  return ALLOWED_IMAGE_EXTENSIONS.includes(ext)
}

const form = reactive({
  title: '',
  categoryId: '',
  summary: '',
  tags: [],
  cover: '',
  content: ''
})

const rules = {
  title: [
    { required: true, message: '请输入文章标题', trigger: 'blur' },
    { max: 20, message: '标题不能超过 20 个字', trigger: 'blur' }
  ],
  categoryId: [{ required: true, message: '请选择所属分类', trigger: 'change' }],
  content: [{ required: true, message: '请输入文章内容', trigger: 'blur' }]
}

const contentCount = computed(() =>
  form.content.replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ').length
)

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

function setEditorContent(html) {
  nextTick(() => {
    if (editorRef.value) {
      editorRef.value.innerHTML = html || ''
    }
  })
}

watch(visible, (val) => {
  if (!val) return
  loadCategories()
  nextTick(() => {
    formRef.value?.clearValidate()
    if (props.article) {
      Object.assign(form, {
        title: props.article.title || '',
        categoryId: props.article.categoryId ?? '',
        summary: props.article.summary || '',
        tags: props.article.tags || [],
        cover: props.article.cover || '',
        content: props.article.content || ''
      })
    } else {
      Object.assign(form, { title: '', categoryId: '', summary: '', tags: [], cover: '', content: '' })
    }
    setEditorContent(form.content)
  })
})

function onEditorInput() {
  form.content = editorRef.value.innerHTML
}

function exec(cmd, value = null) {
  editorRef.value?.focus()
  document.execCommand(cmd, false, value)
}

function onFontSizeChange(val) {
  exec('fontSize', val)
  fontSize.value = '3'
}

function onFontNameChange(val) {
  exec('fontName', val)
  fontName.value = ''
}

async function onCoverChange(file) {
  const raw = file.raw
  if (!isValidImage(raw)) {
    ElMessage.warning('仅支持 JPG / PNG / WebP / GIF 格式的图片')
    return
  }
  if (raw.size > MAX_IMAGE_SIZE) {
    ElMessage.warning('图片大小不能超过 5MB')
    return
  }
  uploading.value = true
  try {
    const data = await uploadFile(raw)
    form.cover = data.url
  } catch (e) {
    if (!e?.handled) ElMessage.error(e.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

function removeCover() {
  form.cover = ''
}

async function handleSave() {
  if (contentCount.value > 1000) {
    ElMessage.warning('文章内容不能超过 1000 字')
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      title: form.title.trim(),
      categoryId: Number(form.categoryId),
      summary: form.summary.trim(),
      tags: form.tags,
      cover: form.cover,
      content: form.content
    }
    if (props.article) {
      await updateArticle({ articleId: props.article.articleId, ...payload })
      ElMessage.success('文章更新成功')
    } else {
      await saveArticle(payload)
      ElMessage.success('文章创建成功')
    }
    emit('update:modelValue', false)
    emit('saved')
  } catch (e) {
    if (!e?.handled) ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="article ? '编辑文章' : '新增文章'"
    width="720px"
    top="6vh"
    :close-on-click-modal="false"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="文章标题" prop="title">
        <el-input v-model="form.title" placeholder="请输入文章标题" maxlength="20" show-word-limit />
      </el-form-item>

      <el-form-item label="所属分类" prop="categoryId">
        <el-select v-model="form.categoryId" placeholder="请选择分类" filterable style="width: 100%">
          <el-option
            v-for="item in categoryOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="文章摘要" prop="summary">
        <el-input
          v-model="form.summary"
          type="textarea"
          :rows="3"
          placeholder="请输入文章摘要（可选）"
          maxlength="200"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="标签" prop="tags">
        <el-select
          v-model="form.tags"
          multiple
          filterable
          allow-create
          default-first-option
          :reserve-keyword="false"
          placeholder="请输入或选择标签"
          style="width: 100%"
        >
          <el-option
            v-for="tag in tagSuggestions"
            :key="tag"
            :label="tag"
            :value="tag"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="封面图片" prop="cover">
        <div class="cover-box">
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            :disabled="uploading"
            :on-change="onCoverChange"
          >
            <div v-if="!form.cover" v-loading="uploading" class="cover-placeholder">
              {{ uploading ? '上传中...' : '点击上传封面' }}
            </div>
            <img v-else :src="form.cover" class="cover-img" alt="封面" />
          </el-upload>
          <el-button v-if="form.cover" link type="danger" @click="removeCover">移除封面</el-button>
        </div>
      </el-form-item>

      <el-form-item label="文章内容" prop="content">
        <div class="editor">
          <div class="editor-toolbar">
            <el-select v-model="fontSize" size="small" class="toolbar-select" @change="onFontSizeChange">
              <el-option label="默认字号" value="3" />
              <el-option label="12px" value="1" />
              <el-option label="14px" value="2" />
              <el-option label="16px" value="3" />
              <el-option label="18px" value="4" />
              <el-option label="20px" value="5" />
              <el-option label="24px" value="6" />
              <el-option label="28px" value="7" />
            </el-select>
            <el-select v-model="fontName" size="small" class="toolbar-select" @change="onFontNameChange">
              <el-option label="默认字体" value="" />
              <el-option label="黑体" value="SimHei" />
              <el-option label="宋体" value="SimSun" />
              <el-option label="微软雅黑" value="Microsoft YaHei" />
              <el-option label="楷体" value="KaiTi" />
            </el-select>
            <button type="button" class="toolbar-btn" @click="exec('formatBlock', 'H1')">H1</button>
            <button type="button" class="toolbar-btn" @click="exec('formatBlock', 'H2')">H2</button>
            <button type="button" class="toolbar-btn" @click="exec('formatBlock', 'H3')">H3</button>
            <button type="button" class="toolbar-btn" title="加粗" @click="exec('bold')"><b>B</b></button>
            <button type="button" class="toolbar-btn" title="斜体" @click="exec('italic')"><i>I</i></button>
            <button type="button" class="toolbar-btn" title="下划线" @click="exec('underline')"><u>U</u></button>
            <button type="button" class="toolbar-btn" title="无序列表" @click="exec('insertUnorderedList')">• 列表</button>
            <button type="button" class="toolbar-btn" title="有序列表" @click="exec('insertOrderedList')">1. 列表</button>
          </div>
          <div
            ref="editorRef"
            class="editor-body"
            contenteditable="true"
            placeholder="请输入文章内容，支持富文本格式"
            @input="onEditorInput"
          />
          <div class="editor-count">{{ contentCount }} / 1000</div>
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.cover-box {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cover-placeholder {
  width: 180px;
  height: 100px;
  border: 1px dashed #d1d5db;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  font-size: 14px;
  cursor: pointer;
  background: #f9fafb;
  transition: border-color 0.2s;
}

.cover-placeholder:hover {
  border-color: #111111;
  color: #111111;
}

.cover-img {
  width: 180px;
  height: 100px;
  object-fit: cover;
  border-radius: 8px;
  display: block;
}

.editor {
  width: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  overflow: hidden;
}

.editor-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 8px;
  background: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
}

.toolbar-select {
  width: 110px;
}

.toolbar-btn {
  height: 28px;
  min-width: 32px;
  padding: 0 8px;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  background: #fff;
  color: #374151;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.toolbar-btn:hover {
  border-color: #111111;
  color: #111111;
}

.editor-body {
  min-height: 180px;
  max-height: 300px;
  overflow-y: auto;
  padding: 12px 14px;
  font-size: 14px;
  line-height: 1.8;
  outline: none;
  color: #1f2937;
}

.editor-body:empty::before {
  content: attr(placeholder);
  color: #9ca3af;
}

.editor-count {
  padding: 6px 12px;
  text-align: right;
  font-size: 12px;
  color: #9ca3af;
  border-top: 1px solid #e5e7eb;
  background: #fff;
}
</style>
