import request from '@/utils/request'
import { API_MODE } from './config'
import {
  mockCategoryTree,
  mockSaveCategory,
  mockUpdateCategory,
  mockDeleteCategory,
  mockArticlePage,
  mockArticleDetail,
  mockSaveArticle,
  mockUpdateArticle,
  mockUpdateArticleStatus,
  mockDeleteArticle
} from './mock'

// 获取分类树
export function getCategoryTree() {
  if (API_MODE === 'mock') return mockCategoryTree()
  return request.get('/knowledge/category/tree')
}

// 新增分类
export function saveCategory(data) {
  if (API_MODE === 'mock') return mockSaveCategory(data)
  return request.post('/knowledge/category', data)
}

// 更新分类
export function updateCategory(data) {
  if (API_MODE === 'mock') return mockUpdateCategory(data)
  return request.put('/knowledge/category', data)
}

// 删除分类
export function deleteCategory(id) {
  if (API_MODE === 'mock') return mockDeleteCategory(id)
  return request.delete(`/knowledge/category/${id}`)
}

// 知识文章列表（分页）
export function getArticlePage(params) {
  if (API_MODE === 'mock') return mockArticlePage(params)
  return request.get('/knowledge/article/page', { params })
}

// 文章新增/编辑
export function saveArticle(data) {
  if (API_MODE === 'mock') return mockSaveArticle(data)
  return request.post('/knowledge/article', data)
}

// 更新知识文章
export function updateArticle(data) {
  if (API_MODE === 'mock') return mockUpdateArticle(data)
  return request.put('/knowledge/article', data)
}

// 更新文章状态
export function updateArticleStatus(data) {
  if (API_MODE === 'mock') return mockUpdateArticleStatus(data)
  return request.put('/knowledge/article/status', data)
}

// 获取文章详情
export function getArticleDetail(id) {
  if (API_MODE === 'mock') return mockArticleDetail(id)
  return request.get(`/knowledge/article/${id}`)
}

// 删除知识文章
export function deleteArticle(id) {
  if (API_MODE === 'mock') return mockDeleteArticle(id)
  return request.delete(`/knowledge/article/${id}`)
}

// AI 推荐文章（首页 / 推荐位使用）
export function getRecommendArticles(limit = 3) {
  if (API_MODE === 'mock') return Promise.resolve([])
  return request.get('/articles/recommend', { params: { limit } })
}
