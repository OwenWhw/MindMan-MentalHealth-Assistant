import request from '@/utils/request'
import { API_MODE } from './config'
import { mockUploadFile } from './mock'

// 上传文件（图片等），返回文件访问地址
export function uploadFile(file) {
  if (API_MODE === 'mock') return mockUploadFile(file)
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/file/upload', formData)
}
