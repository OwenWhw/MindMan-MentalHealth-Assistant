const BUILT_IN_ACTIONS = [
  {
    pattern: /^(?:请根据|请结合)我(?:最近|近)30天的情绪花园记录/,
    label: '回顾近30天的情绪花园'
  },
  {
    pattern: /^请结合我刚才分享的内容，给我一两个温和、可尝试的小建议/,
    label: '根据刚才的内容给我一些建议'
  },
  {
    pattern: /^按我们这段对话里我提到的主题，从 MindMan 心理阅读里找一篇已发布文章/,
    label: '按刚才聊到的主题找一篇文章'
  }
]

export function visibleChatMessageContent(role, content) {
  const text = String(content || '')
  if (role !== 'user') return text

  for (const action of BUILT_IN_ACTIONS) {
    if (action.pattern.test(text)) return action.label
  }

  const translation = text.match(/^请只依据本轮带入的文章《(.+?)》翻译并解读/)
  if (translation) return `翻译并解读文章：《${translation[1]}》`

  const discussion = text.match(/^请结合文章《(.+?)》和我接下来分享的情况/)
  if (discussion) return `结合文章聊聊：《${discussion[1]}》`

  return text
}
