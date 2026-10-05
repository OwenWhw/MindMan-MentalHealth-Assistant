import DOMPurify from 'dompurify'
import { marked } from 'marked'

const SENTENCE_CLOSERS = /[”’」』）】)\"']/
const STRUCTURED_SECTIONS = [
  '译文', '原文', '文章要点', '谨慎解读',
  '聊到的事情', '出现的感受', '可以继续留意', '记录事实', '可能的联系',
  '下一步', '行动建议', '本次回顾', '结论', '总结'
]
const SECTION_LABELS = STRUCTURED_SECTIONS.join('|')

function splitSentences(text) {
  const sentences = []
  let start = 0

  for (let index = 0; index < text.length; index += 1) {
    if (!/[。！？!?]/.test(text[index])) continue

    let end = index + 1
    while (end < text.length && SENTENCE_CLOSERS.test(text[end])) end += 1
    const sentence = text.slice(start, end).trim()
    if (sentence) sentences.push(sentence)
    start = end
    index = end - 1
  }

  const remainder = text.slice(start).trim()
  if (remainder) {
    const isEmojiOnly = /^[\p{Extended_Pictographic}\p{Emoji_Modifier}\uFE0F\u200D\s]+$/u.test(remainder)
    if (isEmojiOnly && sentences.length) sentences[sentences.length - 1] += remainder
    else sentences.push(remainder)
  }
  return sentences
}

function reflowLongParagraphs(markdown) {
  return markdown.split(/\n{2,}/).map((block) => {
    if (/^\s*(?:#{1,6}\s|[-*+]\s|\d+[.)]\s|>\s|```|~~~)/m.test(block)) return block

    const paragraph = block.replace(/\n+/g, ' ').replace(/[ \t]+/g, ' ').trim()
    const sentences = splitSentences(paragraph)
    if (paragraph.length < 100 || sentences.length < 3) return paragraph

    const paragraphs = []
    for (let index = 0; index < sentences.length; index += 2) {
      paragraphs.push(sentences.slice(index, index + 2).join(''))
    }
    return paragraphs.join('\n\n')
  }).join('\n\n')
}

/** Repairs common model formatting slips while preserving intentional Markdown. */
export function normalizeChatMarkdown(content) {
  let markdown = String(content || '').replace(/\r\n?/g, '\n').trim()
  if (!markdown) return ''

  markdown = markdown
    .replace(/(^|\n)(#{1,6})[ \t]*(?=\S)/g, '$1$2 ')
    .replace(/([。！？!?；;.]|\n)[ \t]*(#{1,6})[ \t]*(?=\S)/g, '$1\n\n$2 ')
    // Models may emit section titles inline instead of following the requested layout.
    .replace(
      new RegExp(`(^|[。！？!?；;.]\\s*|\\n\\s*)(?:#{1,6}\\s*)?(${SECTION_LABELS})[ \\t]*[:：]?[ \\t]*`, 'g'),
      (_, boundary, label) => `${boundary}${boundary ? '\n\n' : ''}## ${label}\n\n`
    )
    .replace(/^(#{1,6}[ \t]+.+?)(?=(?:这篇文章|本文|文章(?:主要|指出|强调|还提到|提到)|文中(?:提到|指出)|作者(?:指出|认为)|研究(?:发现|显示)|从文章来看))/gm, '$1\n\n')

  // Restore list structure when the model places numbered points in one paragraph.
  markdown = markdown.replace(
    new RegExp(`(## (?:文章要点|聊到的事情|出现的感受|可以继续留意|记录事实|可能的联系|行动建议)\\n\\n)([\\s\\S]*?)(?=\\n\\n## |$)`, 'g'),
    (_, heading, body) => heading + body
      .replace(/(^|\n)[ \t]*([1-9])[.)][ \t]*/g, '$1$2. ')
      .replace(/(?:[ \t]+|(?<=[。！？!?])[ \t]*)([2-9])[.)][ \t]+/g, '\n$1. ')
  )

  return reflowLongParagraphs(markdown)
}

/** Shared safe renderer for every AI message surface in the app. */
export function renderChatMarkdown(content) {
  const html = marked.parse(normalizeChatMarkdown(content), { gfm: true, breaks: true, async: false })
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: [
      'p', 'br', 'hr', 'h1', 'h2', 'h3', 'h4', 'strong', 'b', 'em', 'i', 'del',
      'ul', 'ol', 'li', 'blockquote', 'a', 'code', 'pre', 'table', 'thead', 'tbody', 'tr', 'th', 'td'
    ],
    ALLOWED_ATTR: ['href', 'title'],
    ALLOW_DATA_ATTR: false
  })
}
