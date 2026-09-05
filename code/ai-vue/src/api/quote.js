import request from '@/utils/request'
import { API_MODE } from './config'

// 随机获取一条每日语句（可来自外部一言 API）
export function getRandomQuote() {
  if (API_MODE === 'mock') {
    return Promise.resolve({
      content: '不必匆忙，不必火花四溅，不必成为别人，只需做自己。',
      translation: "You don't have to be on fire. Just be yourself.",
      author: '弗吉尼亚·伍尔夫',
      source: null
    })
  }
  return request.get('/quote/random')
}
