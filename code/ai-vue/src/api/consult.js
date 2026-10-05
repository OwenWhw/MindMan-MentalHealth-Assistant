import request from '@/utils/request'
import { API_MODE } from './config'
import {
  mockCreateSession,
  mockSessionPage,
  mockMySessions,
  mockMessageList,
  mockDeleteSession,
  mockArchiveSession,
  mockRestoreSession,
  mockSendChatMessage,
  mockAnalyzeEmotion,
  mockAvailableModels
} from './mock'

// 发起咨询会话（用户端新建会话）
export function createSession(data) {
  if (API_MODE === 'mock') return mockCreateSession(data).then((session) => ({ ...session, id: session.sessionId }))
  return request.post('/chat/sessions', data || {})
}

// 获取我的会话列表（用户端）
export function getMySessions(params) {
  if (API_MODE === 'mock') return mockMySessions(params).then((page) => ({
    ...page,
    list: page.list.map((session) => ({ ...session, id: session.sessionId }))
  }))
  return request.get('/chat/sessions', { params })
}

// 获取会话消息列表
export function getMessageList(sessionId) {
  if (API_MODE === 'mock') return mockMessageList(sessionId)
  return request.get(`/chat/sessions/${sessionId}/messages`)
}

// 总结当前登录用户拥有的会话；mock 模式下明确提示该功能需要真实 AI 后端。
export function summarizeSession(sessionId) {
  if (API_MODE === 'mock') return Promise.reject(new Error('AI 总结需要连接真实后端'))
  return request.post(`/chat/sessions/${sessionId}/summary`, {}, { timeout: 60000 })
}

// 删除咨询会话
export function deleteSession(id) {
  if (API_MODE === 'mock') return mockDeleteSession(id)
  return request.delete(`/chat/sessions/${id}`)
}

// ======================== 管理端咨询会话（无归属校验） ========================

// 分页查询全部咨询会话（管理端）
export function getAdminSessionPage(params) {
  if (API_MODE === 'mock') return mockSessionPage(params)
  return request.get('/admin/consult/sessions', { params })
}

// 查看会话消息（管理端）
export function getAdminMessages(sessionId) {
  if (API_MODE === 'mock') return mockMessageList(sessionId)
  return request.get(`/admin/consult/sessions/${sessionId}/messages`)
}

// 删除会话（管理端）
export function deleteAdminSession(id) {
  if (API_MODE === 'mock') return mockDeleteSession(id)
  return request.delete(`/admin/consult/sessions/${id}`)
}

// 归档会话（结束当前会话，移入已归档）
export function archiveSession(id) {
  if (API_MODE === 'mock') return mockArchiveSession(id)
  return request.put(`/chat/sessions/${id}/archive`)
}

// 从已归档列表恢复会话后继续对话
export function restoreSession(id) {
  if (API_MODE === 'mock') return mockRestoreSession(id)
  return request.put(`/chat/sessions/${id}/restore`)
}

// AI 情绪分析：根据用户倾诉内容分析压力值 / 焦虑指数 / 睡眠风险
export function analyzeEmotion(content) {
  if (API_MODE === 'mock') return mockAnalyzeEmotion(content)
  return request.post('/consult/emotion/analyze', { content })
}

// 获取可用 AI 模型列表
export function getAvailableModels() {
  if (API_MODE === 'mock') return mockAvailableModels()
  return request.get('/chat/models')
}

/**
 * 流式发送咨询消息（SSE / fetch+ReadableStream）。
 *
 * ## 工作模式
 * - **mock 模式**：按字符切片模拟流式输出（逐字显示打字机效果）
 * - **真实模式**：使用 `fetch` + `ReadableStream` 读取后端 SSE (`text/event-stream`)，
 *   每个 chunk 包含 `{ text, done, emotion }` 结构的 JSON 片段
 *
 * ## 后端 SSE 数据格式
 * ```
 * event: message
 * data: {"text":"我听到了","done":false}
 *
 * event: message
 * data: {"text":"你的分享","done":false}
 *
 * event: done
 * data: {"text":"","done":true,"emotion":"焦虑"}
 * ```
 *
 * ## 使用方式（async generator，for-await-of 消费）
 * ```js
 * for await (const chunk of streamChatMessage(sid, content)) {
 *   reply.content = chunk.text        // 累加文本片段
 *   if (chunk.done) {
 *     reply.cards = chunk.cards || []
 *     reply.streaming = false         // 结束标记
 *   }
 * }
 * ```
 */
export async function* streamChatMessage(sessionId, content, model, contextOptions = {}) {
  // ── Mock 模式：模拟流式输出 ──
  if (API_MODE === 'mock') {
    const data = await mockSendChatMessage(sessionId, content)
    const text = String(data.content || '')
    const total = text.length
    if (!total) {
      yield { text: '', done: true, cards: data.cards || [] }
      return
    }
    let i = 0
    while (i < total) {
      await new Promise((r) => setTimeout(r, 26 + Math.random() * 30))
      const previous = i
      i = Math.min(total, i + 1 + Math.floor(Math.random() * 2))
      yield { text: text.slice(previous, i), done: i >= total, cards: data.cards || [] }
    }
    return
  }

  // ── 真实模式：fetch + ReadableStream 消费 SSE ──
  const token = localStorage.getItem('mha_token')
  const baseUrl = import.meta.env.VITE_API_BASE_URL || ''

  try {
    const response = await fetch(`${baseUrl}/api/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({
        sessionId,
        content,
        model,
        displayContent: contextOptions.displayContent,
        includeGardenContext: !!contextOptions.includeGardenContext,
        referenceArticleId: contextOptions.referenceArticleId || undefined,
        articleTranslationMode: !!contextOptions.articleTranslationMode
      })
    })

    // HTTP 错误处理
    if (!response.ok) {
      const errBody = await response.json().catch(() => ({}))
      const msg = errBody?.message || `请求失败 (${response.status})`
      throw new Error(msg, { cause: { handled: false, code: response.status } })
    }

    // 校验响应 Content-Type 是否为 SSE 流
    const contentType = response.headers.get('content-type') || ''
    if (!contentType.includes('text/event-stream')) {
      console.warn('[SSE] 响应非 event-stream 类型:', contentType)
      // 降级为普通 JSON 响应
      const data = await response.json().catch(() => null)
      const text = String(data?.content || '')
      yield { text, done: true, emotion: data?.emotion || '平静' }
      return
    }

    // 通过 ReadableStream 读取 SSE 数据流
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let serverDone = false

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      // 按 SSE 协议分割：每个消息以 \n\n 结尾
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''  // 最后一行可能不完整，保留到下次

      for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed || trimmed.startsWith(':')) continue  // 跳过注释行和空行

        // 解析 "data: {...}" 行（兼容 Spring SseEmitter 输出的 "data:{...}" 无空格格式）
        if (trimmed.startsWith('data:')) {
          const payload = trimmed.substring(5).trim()

          // [DONE] 标记 — 流结束
          if (payload === '[DONE]') {
            serverDone = true
            yield { text: '', done: true }
            return
          }

          // 解析 JSON payload
          try {
            const parsed = JSON.parse(payload)

            // error 事件
            if (parsed.error) {
              throw new Error(parsed.error, { cause: { handled: false } })
            }

            // 正常文本片段
            yield {
              text: parsed.text || '',
              done: !!parsed.done,
              emotion: parsed.emotion || undefined
            }

            // done=true 时结束
            if (parsed.done) {
              serverDone = true
              return
            }
          } catch (e) {
            // JSON 解析失败 → 可能是非标准数据，跳过
            if (e instanceof SyntaxError) {
              console.warn('[SSE] 无法解析 payload:', payload)
              continue
            }
            throw e  // 重新抛出其他错误
          }
        }
      }
    }

    if (!serverDone) throw new Error('对话连接中断，未收到完整回复')

  } catch (e) {
    // 标记错误是否已被处理（避免上层重复提示）
    const wrappedError = new Error(e.message || 'SSE 连接中断', {
      cause: { handled: e.cause?.handled || false }
    })
    throw wrappedError
  }
}
