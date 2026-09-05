import request from '@/utils/request'
import { API_MODE } from './config'
import {
  mockDiaryPage,
  mockDeleteDiary,
  mockGetGarden,
  mockPlantFlower,
  mockUpdateFlower,
  mockDeleteFlower
} from './mock'

// 分页查询情绪日记
export function getDiaryPage(params) {
  if (API_MODE === 'mock') return mockDiaryPage(params)
  return request.get('/emotion/diary/page', { params })
}

// 删除情绪日记
export function deleteDiary(id) {
  if (API_MODE === 'mock') return mockDeleteDiary(id)
  return request.delete(`/emotion/diary/${id}`)
}

// 获取情绪花园（用户端）
export function getGarden() {
  if (API_MODE === 'mock') return mockGetGarden()
  return request.get('/emotion/garden')
}

// 种下今日心情（用户端）
export function plantFlower(data) {
  if (API_MODE === 'mock') return mockPlantFlower(data)
  return request.post('/emotion/garden', data)
}

// 编辑花朵
export function updateFlower(flowerId, data) {
  if (API_MODE === 'mock') return mockUpdateFlower(flowerId, data)
  return request.put(`/emotion/garden/${flowerId}`, data)
}

// 删除花朵
export function deleteFlower(flowerId) {
  if (API_MODE === 'mock') return mockDeleteFlower(flowerId)
  return request.delete(`/emotion/garden/${flowerId}`)
}

// 本周情绪洞察（首页 AI 本周洞察卡片使用）
export function getInsightThisWeek() {
  if (API_MODE === 'mock') return Promise.resolve(null)
  return request.get('/emotion/insight/this-week')
}
