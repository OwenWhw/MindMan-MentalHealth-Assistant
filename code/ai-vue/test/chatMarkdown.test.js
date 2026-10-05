import test from 'node:test'
import assert from 'node:assert/strict'
import { marked } from 'marked'
import { normalizeChatMarkdown } from '../src/utils/chatMarkdown.js'
import { visibleChatMessageContent } from '../src/utils/chatActions.js'

test('repairs a stuck heading and breaks a long Chinese reply into paragraphs', () => {
  const reply = '###建立健康的边界：学会说“不”的艺术这篇文章强调了设定心理边界的必要性。心理边界就像一个心理围栏，帮助我们决定哪些行为可以接受，哪些不可以。健康的边界是灵活的，可以调整但不会消失。文章还提到，经常嘴上答应、心里拒绝，可能导致内疚和疲惫。那么，最近有没有一件让你感到疲惫、却难以拒绝的事？🌱'
  const normalized = normalizeChatMarkdown(reply)
  const html = marked.parse(normalized, { gfm: true, breaks: true })

  assert.match(html, /<h3>建立健康的边界/)
  assert.doesNotMatch(html, /###建立/)
  assert.equal((html.match(/<p>/g) || []).length, 3)
  assert.match(normalized, /事？🌱$/)
})

test('renders known legacy button prompts as short action labels', () => {
  const prompt = '请根据我最近30天的情绪花园记录，帮我温和回顾出现较多的情绪、评分和可能的情境联系。请区分记录事实与推测。'
  const articlePrompt = '请只依据本轮带入的文章《Daily boundaries》翻译并解读，不要根据标题补写正文。'

  assert.equal(visibleChatMessageContent('user', prompt), '回顾近30天的情绪花园')
  assert.equal(visibleChatMessageContent('user', articlePrompt), '翻译并解读文章：《Daily boundaries》')
  assert.equal(visibleChatMessageContent('assistant', prompt), prompt)
})

test('renders conversation summaries with Markdown headings and bullet lists', () => {
  const summary = '- 用户提到工作和睡眠中出现焦虑。\n\n### 出现的感受\n\n- 用户感到焦虑和担心。\n- 用户希望进一步了解触发情绪的具体情境。'
  const html = marked.parse(normalizeChatMarkdown(summary), { gfm: true, breaks: true })

  assert.match(html, /<h2>出现的感受<\/h2>/)
  assert.equal((html.match(/<ul>/g) || []).length, 2)
  assert.doesNotMatch(html, /### 出现的感受/)
})

test('separates inline article sections and restores numbered point layout', () => {
  const reply = '译文Fans across the world mourn the loss of country icon, described as “post-Parton depression”. ##文章要点1. 全球粉丝哀悼 Dolly Parton。2. 文中称这种感受为“post-Parton depression”。谨慎解读文章只提供了有限信息。'
  const normalized = normalizeChatMarkdown(reply)
  const html = marked.parse(normalized, { gfm: true, breaks: true })

  assert.match(html, /<h2>译文<\/h2>/)
  assert.match(html, /Fans across the world mourn the loss of country icon/)
  assert.match(html, /<h2>文章要点<\/h2>/)
  assert.match(html, /<ol>[\s\S]*<li>全球粉丝哀悼 Dolly Parton。<\/li>[\s\S]*<li>文中称这种感受为/)
  assert.match(html, /<h2>谨慎解读<\/h2>/)
  assert.doesNotMatch(html, /##文章要点/)
})
