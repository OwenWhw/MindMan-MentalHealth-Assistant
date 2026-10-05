import request from '@/utils/request'
import { API_MODE } from './config'
import {
  mockLogin,
  mockRegister,
  mockLogout,
  mockChangePassword,
  mockUpdateProfile
} from './mock'

// 登录
export function login(data) {
  if (API_MODE === 'mock') return mockLogin(data)
  return request.post('/auth/login', data)
}

// 用户注册
export function register(data) {
  if (API_MODE === 'mock') return mockRegister(data)
  return request.post('/auth/register', data)
}

// 退出登录
export function logout() {
  if (API_MODE === 'mock') return mockLogout()
  return request.post('/auth/logout')
}

// 修改密码
export function changePassword(data) {
  if (API_MODE === 'mock') return mockChangePassword(data)
  return request.put('/auth/password', data)
}

// 编辑当前用户资料
export function updateProfile(data) {
  if (API_MODE === 'mock') return mockUpdateProfile(data)
  return request.put('/auth/profile', data)
}
