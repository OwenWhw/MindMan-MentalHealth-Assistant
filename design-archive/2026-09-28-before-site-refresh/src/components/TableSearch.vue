<script setup>
import { reactive, watch } from 'vue'

const props = defineProps({
  // 搜索字段配置，例如：
  // [{ prop: 'keyword', label: '关键词', type: 'input' },
  //  { prop: 'status', label: '状态', type: 'select', options: [{ label: '正常', value: 1 }] },
  //  { prop: 'dateRange', label: '创建时间', type: 'date' }]
  fields: {
    type: Array,
    default: () => []
  },
  // 查询参数（支持 v-model 双向绑定）
  modelValue: {
    type: Object,
    default: () => ({})
  },
  // 查询按钮加载状态
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'search', 'reset'])

const form = reactive({})

// 同步父组件传入的查询参数
watch(
  () => props.modelValue,
  (val) => {
    Object.keys(form).forEach((key) => delete form[key])
    Object.assign(form, val || {})
  },
  { deep: true, immediate: true }
)

function handleSearch() {
  emit('update:modelValue', { ...form })
  emit('search', { ...form })
}

function handleReset() {
  const defaults = {}
  props.fields.forEach((field) => {
    defaults[field.prop] = field.type === 'date' ? [] : ''
  })
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, defaults)
  emit('update:modelValue', { ...form })
  emit('reset', { ...form })
}
</script>

<template>
  <el-card shadow="never" class="table-search">
    <el-form inline class="search-form" @submit.prevent="handleSearch">
      <el-form-item v-for="field in fields" :key="field.prop" :label="field.label">
        <el-input
          v-if="field.type === 'input' || !field.type"
          v-model="form[field.prop]"
          :placeholder="field.placeholder || `请输入${field.label}`"
          clearable
          @keyup.enter="handleSearch"
        />
        <el-select
          v-else-if="field.type === 'select'"
          v-model="form[field.prop]"
          :placeholder="field.placeholder || `请选择${field.label}`"
          clearable
          class="search-select"
        >
          <el-option
            v-for="option in field.options"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-date-picker
          v-else-if="field.type === 'date'"
          v-model="form[field.prop]"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          class="search-date"
        />
      </el-form-item>
      <el-form-item class="search-actions">
        <el-button type="primary" :loading="loading" @click="handleSearch">
          <el-icon><Search /></el-icon>
          <span>查询</span>
        </el-button>
        <el-button @click="handleReset">
          <el-icon><Refresh /></el-icon>
          <span>重置</span>
        </el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<style scoped>
.table-search {
  margin-bottom: 16px;
}

.search-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0 12px;
}

.search-form :deep(.el-form-item) {
  margin-right: 0;
  margin-bottom: 12px;
}

.search-select {
  width: 160px;
}

.search-date {
  width: 240px;
}

.search-actions {
  margin-bottom: 12px;
}
</style>
