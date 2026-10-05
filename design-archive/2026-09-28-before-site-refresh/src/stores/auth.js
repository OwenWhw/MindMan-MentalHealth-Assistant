import { ref, computed } from 'vue'
import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', () => {
  // 登录凭证，持久化到本地，刷新页面不丢失
  const token = ref(localStorage.getItem('mha_token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('mha_user') || 'null'))

  // 是否为系统管理员（兼容旧数据中的"超级管理员"角色名）
  const isAdmin = computed(
    () => userInfo.value?.role === 'admin' || userInfo.value?.role === '超级管理员'
  )

  function setLogin({ token: newToken, userInfo: newUserInfo }) {
    token.value = newToken
    userInfo.value = newUserInfo
    localStorage.setItem('mha_token', newToken)
    localStorage.setItem('mha_user', JSON.stringify(newUserInfo))
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('mha_token')
    localStorage.removeItem('mha_user')
  }

  return { token, userInfo, isAdmin, setLogin, logout }
})
