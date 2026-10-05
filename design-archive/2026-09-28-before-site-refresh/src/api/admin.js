import request from '@/utils/request'
import { API_MODE } from './config'
import {
  mockAdminUsers,
  mockUpdateUserStatus,
  mockUpdateUserRole,
  mockDeleteUser
} from './mock'

// 分页查询用户（管理端）
export function getAdminUsers(params) {
  if (API_MODE === 'mock') return mockAdminUsers(params)
  return request.get('/admin/users', { params })
}

// 启用/禁用用户（管理端）
export function updateUserStatus(id, status) {
  if (API_MODE === 'mock') return mockUpdateUserStatus(id, status)
  return request.put(`/admin/users/${id}/status`, { status })
}

// 修改用户角色（管理端）
export function updateUserRole(id, role) {
  if (API_MODE === 'mock') return mockUpdateUserRole(id, role)
  return request.put(`/admin/users/${id}/role`, { role })
}

// 删除用户（管理端）
export function deleteUser(id) {
  if (API_MODE === 'mock') return mockDeleteUser(id)
  return request.delete(`/admin/users/${id}`)
}
