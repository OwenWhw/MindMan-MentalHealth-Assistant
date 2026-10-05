import { ref } from 'vue'
import { defineStore } from 'pinia'

// 跨页面共享：ConsultView 触发的最新 AI 分析结果
// GardenView 种花时可读取其中数据作为预填默认值
export const useEmotionStore = defineStore('emotion', () => {
  const latest = ref(null)

  function setLatest(data) {
    latest.value = data
  }

  function clear() {
    latest.value = null
  }

  return { latest, setLatest, clear }
})