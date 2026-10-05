import request from '@/utils/request'
import { API_MODE } from './config'
import { mockAnalysisOverview } from './mock'

// 获取综合分析数据
export function getAnalysisOverview(params) {
  if (API_MODE === 'mock') return mockAnalysisOverview(params)
  return request.get('/analysis/overview', { params })
}
