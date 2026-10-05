import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

// axios 二次封装：统一 baseURL、超时、token 注入、响应解包、错误处理
const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截器：自动携带登录凭证
request.interceptors.request.use((config) => {
  const authStore = useAuthStore()
  if (authStore.token) {
    config.headers.Authorization = 'Bearer ' + authStore.token
  }
  return config
})

// 统一从响应体里取消息：后端用 `message`，但兼容旧的 `msg` 字段
const pickMsg = (body, fallback) => (body?.message || body?.msg || fallback)

// 全局去重：1.2 秒内同一条错误提示只弹一次
const _lastToastKey = { key: '', at: 0 }
const safeError = (msg) => {
  const k = String(msg || '')
  const now = Date.now()
  if (_lastToastKey.key === k && now - _lastToastKey.at < 1200) return
  _lastToastKey.key = k
  _lastToastKey.at = now
  ElMessage.error(k)
}

// 全局 401 守卫：同一会话里只处理一次"登录已过期" + 跳转
let _401Handled = false
const mark401Handled = () => {
  _401Handled = true
  // 30 秒后允许再次提示（兜底，避免永久屏蔽）
  setTimeout(() => { _401Handled = false }, 30000)
}
// 任意接口 200 成功都重置两个守卫
const resetGuards = () => {
  _401Handled = false
  _lastToastKey.key = ''
}
// 登录成功后重置
export const reset401Guard = resetGuards

// 响应拦截器：统一解包 code/message/data，统一错误提示
request.interceptors.response.use(
  (response) => {
    const res = response.data || {}
    if (res.code === 200) {
      resetGuards()
      return res.data
    }
    if (res.code === 401) {
      const isLoginApi = (response.config?.url || '').includes('/auth/login')
      if (isLoginApi) {
        // 登录失败：把后端的真实消息交给调用方显示，不弹 toast、不清 token、不跳转
        const err = new Error(pickMsg(res, '登录失败'))
        err.handled = false
        return Promise.reject(err)
      }
      if (!_401Handled) {
        mark401Handled()
        const authStore = useAuthStore()
        authStore.logout()
        safeError(pickMsg(res, '登录已过期，请重新登录'))
        if (!window.location.pathname.startsWith('/login')) {
          window.location.href = '/login'
        }
      }
      const err = new Error(pickMsg(res, '未登录'))
      err.handled = true
      return Promise.reject(err)
    }
    // 其他业务错误（400/403/500 等）：统一去重地弹后端的真实 message
    safeError(pickMsg(res, '请求失败'))
    const err = new Error(pickMsg(res, '请求失败'))
    err.handled = true
    return Promise.reject(err)
  },
  (error) => {
    const status = error.response?.status
    const isLoginApi = (error.config?.url || '').includes('/auth/login')
    // 优先用后端返回的 message（如果存在）
    let msg = pickMsg(error.response?.data, null) || '网络异常，请检查后端服务是否已启动'
    if (status === 401) {
      msg = pickMsg(error.response?.data, '登录已过期，请重新登录')
      if (!isLoginApi && !_401Handled) {
        mark401Handled()
        const authStore = useAuthStore()
        authStore.logout()
        if (!window.location.pathname.startsWith('/login')) {
          window.location.href = '/login'
        }
      }
      if (!isLoginApi) safeError(msg)
    } else if (status === 403) {
      msg = pickMsg(error.response?.data, '没有操作权限')
      if (!isLoginApi) safeError(msg)
    } else if (status === 404) {
      msg = pickMsg(error.response?.data, '请求的接口不存在')
      if (!isLoginApi) safeError(msg)
    } else if (status === 500) {
      msg = pickMsg(error.response?.data, '服务器内部错误')
      if (!isLoginApi) safeError(msg)
    } else if (error.code === 'ECONNABORTED') {
      msg = '请求超时，请稍后重试'
      if (!isLoginApi) safeError(msg)
    } else if (!isLoginApi) {
      safeError(msg)
    }
    error.message = msg
    // 登录接口的错误交给 LoginForm 自己弹（handled=false），其他接口已被弹过 toast
    error.handled = !isLoginApi
    return Promise.reject(error)
  }
)

export default request