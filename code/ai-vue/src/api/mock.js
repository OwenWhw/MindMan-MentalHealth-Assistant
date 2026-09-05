// 本地模拟数据：返回结构与接口文档保持一致，供后端未就绪时开发预览

const delay = (ms = 200) => new Promise((resolve) => setTimeout(resolve, ms))

const now = () => {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// ---------- 认证 ----------
const DEFAULT_ADMIN = { username: 'admin', password: '123456' }
const DEFAULT_USER = { username: 'demo', password: '123456', nickname: '预览用户' }

// 内存缓存：无 localStorage 的环境（如 Node 测试）也能保持改密后的状态
let adminCache = null

// 读取管理员账号（模拟数据持久化，修改密码后刷新不丢失）
function getAdminAccount() {
  if (adminCache) return { ...adminCache }
  if (typeof localStorage !== 'undefined') {
    const saved = localStorage.getItem('mha_mock_admin')
    if (saved) {
      try {
        adminCache = JSON.parse(saved)
        return { ...adminCache }
      } catch (e) {
        /* 忽略解析错误，使用默认账号 */
      }
    }
  }
  return { ...DEFAULT_ADMIN }
}

function saveAdminAccount(account) {
  adminCache = { ...account }
  if (typeof localStorage !== 'undefined') {
    localStorage.setItem('mha_mock_admin', JSON.stringify(account))
  }
}

// ---------- 注册用户 ----------
let usersCache = null

function getUsers() {
  if (usersCache) return usersCache
  if (typeof localStorage !== 'undefined') {
    const saved = localStorage.getItem('mha_mock_users')
    if (saved) {
      try {
        usersCache = JSON.parse(saved)
        return usersCache
      } catch (e) {
        /* 忽略解析错误 */
      }
    }
  }
  usersCache = []
  return usersCache
}

function saveUsers(users) {
  usersCache = users
  if (typeof localStorage !== 'undefined') {
    localStorage.setItem('mha_mock_users', JSON.stringify(users))
  }
}

export async function mockLogin(data) {
  await delay(300)
  const account = String(data.username || '').trim()
  const password = String(data.password || '')
  const admin = getAdminAccount()

  // 系统管理员登录
  if (account === admin.username && password === admin.password) {
    return {
      token: 'mock-token-' + Date.now(),
      userId: 1,
      username: admin.username,
      nickname: '管理员',
      avatar: '',
      role: 'admin'
    }
  }

  // 内置演示用户（预览版默认账号）
  if (account === DEFAULT_USER.username && password === DEFAULT_USER.password) {
    return {
      token: 'mock-token-' + Date.now(),
      userId: 20001,
      username: DEFAULT_USER.username,
      nickname: DEFAULT_USER.nickname,
      avatar: '',
      role: 'user'
    }
  }

  // 注册用户登录（支持用户名或邮箱）
  const user = getUsers().find((item) => item.username === account || item.email === account)
  if (user && user.password === password) {
    return {
      token: 'mock-token-' + Date.now(),
      userId: user.userId,
      username: user.username,
      nickname: user.nickname,
      avatar: '',
      role: 'user'
    }
  }

  throw new Error('账号或密码错误')
}

export async function mockRegister(data) {
  await delay(300)
  const account = String(data.account || '').trim()
  const email = String(data.email || '').trim()
  const users = getUsers()
  const admin = getAdminAccount()

  if (!account) throw new Error('用户名不能为空')
  if (account === admin.username) throw new Error('该用户名已被占用')
  if (account === DEFAULT_USER.username) throw new Error('该用户名已被占用')
  if (users.some((item) => item.username === account)) throw new Error('用户名已存在')
  if (email && users.some((item) => item.email === email)) throw new Error('邮箱已被注册')

  const user = {
    userId: 100 + users.length + 1,
    username: account,
    email,
    nickname: data.nickname || account,
    phone: data.phone || '',
    password: String(data.password || ''),
    role: 'user',
    createdAt: now()
  }
  users.push(user)
  saveUsers(users)
  return { userId: user.userId }
}

export async function mockLogout() {
  await delay(100)
  return null
}

export async function mockChangePassword(data) {
  await delay(200)
  const admin = getAdminAccount()
  // 管理员改密
  const current = getCurrentMockUser()
  if (current && current.role === 'admin') {
    if (data.oldPassword !== admin.password) {
      throw new Error('原密码不正确')
    }
    if (data.newPassword === data.oldPassword) {
      throw new Error('新密码不能与原密码相同')
    }
    saveAdminAccount({ ...admin, password: data.newPassword })
    return null
  }
  // 内置演示用户（预览版 demo / 123456）
  if (current && current.username === DEFAULT_USER.username) {
    const saved = typeof localStorage !== 'undefined' ? localStorage.getItem('mha_demo_password') : null
    const old = saved || DEFAULT_USER.password
    if (data.oldPassword !== old) {
      throw new Error('原密码不正确')
    }
    if (data.newPassword === data.oldPassword) {
      throw new Error('新密码不能与原密码相同')
    }
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem('mha_demo_password', String(data.newPassword))
    }
    return null
  }
  // 注册用户改密
  const users = getUsers()
  const user = users.find((item) => item.username === current?.username)
  if (!user) throw new Error('账号不存在')
  if (user.password !== data.oldPassword) {
    throw new Error('原密码不正确')
  }
  if (data.newPassword === data.oldPassword) {
    throw new Error('新密码不能与原密码相同')
  }
  user.password = String(data.newPassword)
  saveUsers(users)
  return null
}

// 读取当前登录的 mock 用户
function getCurrentMockUser() {
  if (typeof localStorage === 'undefined') return null
  try {
    return JSON.parse(localStorage.getItem('mha_user') || 'null')
  } catch (e) {
    return null
  }
}

// 编辑当前用户资料（昵称 / 手机号 / 邮箱 / 头像）
export async function mockUpdateProfile(data = {}) {
  await delay(200)
  const current = getCurrentMockUser()
  if (!current) throw new Error('请先登录')
  const next = { ...current }
  if (data.nickname != null && String(data.nickname).trim()) {
    next.nickname = String(data.nickname).trim()
  }
  if (data.phone != null) next.phone = String(data.phone).trim()
  if (data.email != null) next.email = String(data.email).trim()
  if (data.avatar != null) next.avatar = String(data.avatar).trim()

  // 注册用户同步到用户缓存（改密/资料保持登录可用）
  const users = getUsers()
  const u = users.find((item) => item.username === current.username)
  if (u) {
    if (next.nickname) u.nickname = next.nickname
    if (next.phone !== undefined) u.phone = next.phone
    if (next.email !== undefined) u.email = next.email
    saveUsers(users)
  }
  localStorage.setItem('mha_user', JSON.stringify(next))
  return next
}

// ---------- 文件上传 ----------
export async function mockUploadFile(file) {
  await delay(400)
  try {
    if (typeof Blob !== 'undefined' && file instanceof Blob && typeof URL !== 'undefined' && typeof URL.createObjectURL === 'function') {
      return { url: URL.createObjectURL(file) }
    }
  } catch (e) {
    /* 降级为 base64 */
  }
  // 兜底：读取为 base64 数据地址，任何环境都能正常预览
  return new Promise((resolve) => {
    const reader = new FileReader()
    reader.onload = () => resolve({ url: reader.result })
    reader.onerror = () => resolve({ url: '' })
    reader.readAsDataURL(file)
  })
}

// ---------- 分类 ----------
const categoryDb = [
  { categoryId: 1, categoryName: '人际关系', description: '人际交往和关系处理', sortOrder: 10, status: 1, statusText: '启用', articleCount: 1, createdAt: '2025-09-01 12:59:03', updatedAt: '2025-09-13 11:48:16', children: [] },
  { categoryId: 2, categoryName: '压力缓解', description: '压力疏导与放松方法', sortOrder: 20, status: 1, statusText: '启用', articleCount: 2, createdAt: '2025-09-01 12:59:03', updatedAt: '2025-09-13 11:48:16', children: [] },
  { categoryId: 3, categoryName: '情绪管理', description: '情绪识别与调节方法', sortOrder: 30, status: 1, statusText: '启用', articleCount: 3, createdAt: '2025-09-01 12:59:03', updatedAt: '2025-09-13 11:48:16', children: [] },
  { categoryId: 4, categoryName: '心理健康基础', description: '心理健康科普知识', sortOrder: 40, status: 1, statusText: '启用', articleCount: 2, createdAt: '2025-09-01 12:59:03', updatedAt: '2025-09-13 11:48:16', children: [] }
]

export async function mockCategoryTree() {
  await delay()
  return JSON.parse(
    JSON.stringify(
      categoryDb.map((category) => ({
        ...category,
        articleCount: articleDb.filter((item) => item.categoryId === category.categoryId).length
      }))
    )
  )
}

export async function mockSaveCategory(data) {
  await delay(200)
  const name = String(data.categoryName || '').trim()
  if (!name) throw new Error('分类名称不能为空')
  if (categoryDb.some((item) => item.categoryName === name)) {
    throw new Error('分类名称已存在')
  }
  const maxId = Math.max(0, ...categoryDb.map((item) => item.categoryId))
  const category = {
    categoryId: maxId + 1,
    categoryName: name,
    description: data.description || '',
    sortOrder: Number(data.sortOrder) || 0,
    status: Number(data.status) || 1,
    statusText: Number(data.status) === 0 ? '停用' : '启用',
    articleCount: 0,
    createdAt: now(),
    updatedAt: now(),
    children: []
  }
  categoryDb.push(category)
  return { categoryId: category.categoryId }
}

export async function mockUpdateCategory(data) {
  await delay(200)
  const category = categoryDb.find((item) => item.categoryId === Number(data.categoryId))
  if (!category) throw new Error('分类不存在')
  const name = String(data.categoryName || '').trim()
  if (categoryDb.some((item) => item.categoryName === name && item.categoryId !== Number(data.categoryId))) {
    throw new Error('分类名称已存在')
  }
  Object.assign(category, {
    categoryName: name || category.categoryName,
    description: data.description ?? category.description,
    sortOrder: Number(data.sortOrder) || category.sortOrder,
    status: data.status === undefined ? category.status : Number(data.status),
    statusText: Number(data.status) === 0 ? '停用' : '启用',
    updatedAt: now()
  })
  return null
}

export async function mockDeleteCategory(id) {
  await delay(100)
  const index = categoryDb.findIndex((item) => item.categoryId === Number(id))
  if (index === -1) throw new Error('分类不存在')
  categoryDb.splice(index, 1)
  return null
}

// ---------- 知识文章 ----------
const pad2 = (n) => String(n).padStart(2, '0')

function formatDate(date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}`
}

function buildArticles() {
  const base = [
    { articleId: 1, title: '测试233', categoryId: 4, categoryName: '心理健康基础', status: 1, author: 'MindMan', reads: 0, cover: '', summary: '了解心理健康的基础概念是开启自我关怀的第一步。', tags: ['心理科普', '入门'], content: '心理健康是整体健康的重要组成部分。它不仅指没有心理疾病，还包括积极的心理状态——能够应对日常压力、发挥个人潜力、有效工作并为社区做出贡献。保持良好的心理健康，需要关注情绪变化、建立健康的社交关系、保持规律的运动和充足的睡眠。', publishTime: '2026-01-20 09:45:37', createdAt: '2026-01-20 09:45:37', updatedAt: '2026-01-20 09:45:37' },
    { articleId: 2, title: '学生心理压力应对策略', categoryId: 2, categoryName: '压力缓解', status: 1, author: 'MindMan', reads: 35, cover: '', summary: '帮助学生识别和应对学习生活中的各种心理压力。', tags: ['学业压力', '青少年心理'], content: '学生时期面临着学业、人际、未来规划等多重压力。掌握有效的应对策略对学生心理健康至关重要。\n\n首先，合理安排时间是缓解压力的关键。制定学习计划、避免拖延、保持充足的休息时间。其次，培养积极的社交关系，与同学、老师、家人保持良好沟通。\n\n此外，适当的体育锻炼、正念冥想、兴趣爱好等都能有效缓解压力。如果压力持续影响日常生活，建议寻求专业心理咨询。', publishTime: '2025-09-07 08:30:00', createdAt: '2025-09-07 08:30:00', updatedAt: '2025-09-07 08:30:00' },
    { articleId: 3, title: '正念练习入门指南', categoryId: 3, categoryName: '情绪管理', status: 1, author: 'MindMan', reads: 27, cover: '', summary: '正念练习能帮助你更好地觉察和管理情绪。', tags: ['正念', '冥想'], content: '正念是一种有意识的、不评判的觉察当下的练习。它能够帮助我们减少焦虑、改善注意力和增强情绪调适能力。\n\n入门练习可以从呼吸觉察开始：找一个安静的地方，舒适地坐下，将注意力集中在呼吸上。感受空气进入鼻腔，充满肺部，再缓缓呼出。当注意力游离时，温柔地将它带回来。每天坚持5-10分钟，你会发现自己越来越能够觉察情绪的起伏，而不被它所控制。', publishTime: '2025-09-06 13:10:00', createdAt: '2025-09-06 13:10:00', updatedAt: '2025-09-06 13:10:00' },
    { articleId: 4, title: '睡眠质量与心理健康', categoryId: 4, categoryName: '心理健康基础', status: 1, author: 'MindMan', reads: 18, cover: '', summary: '良好睡眠是心理健康的基础，了解睡眠与情绪的关系。', tags: ['睡眠', '生活建议'], content: '睡眠与情绪紧密相关。长期睡眠不足会导致情绪不稳定、焦虑和注意力下降。研究表明，每晚7-8小时的优质睡眠对情绪调节至关重要。\n\n改善睡眠质量的方法包括：保持规律的作息、睡前一小时减少屏幕使用、创造舒适的睡眠环境、避免咖啡因和酒精。如果失眠问题持续存在，建议寻求专业帮助。', publishTime: '2025-09-05 11:45:00', createdAt: '2025-09-05 11:45:00', updatedAt: '2025-09-05 11:45:00' },
    { articleId: 5, title: '建立健康的人际关系', categoryId: 1, categoryName: '人际关系', status: 1, author: 'MindMan', reads: 31, cover: '', summary: '健康的人际关系是心理健康的重要支撑。', tags: ['社交', '沟通技巧'], content: '人际关系质量直接影响我们的心理健康。健康的人际关系基于相互尊重、坦诚沟通和边界意识。\n\n建立良好关系的第一步是学会倾听——不仅要听到对方说了什么，更要理解对方的情感和需求。学会表达自己的感受和需求同样重要。设定健康边界，既不过度依赖也不过分疏远，是维护长久关系的核心。', publishTime: '2025-09-04 16:20:00', createdAt: '2025-09-04 16:20:00', updatedAt: '2025-09-04 16:20:00' },
    { articleId: 6, title: '职场压力管理指南', categoryId: 2, categoryName: '压力缓解', status: 1, author: 'MindMan', reads: 23, cover: '', summary: '学会在工作中管理压力，提升工作效率和幸福感。', tags: ['职场', '压力管理'], content: '职场压力是现代人面临的常见挑战。适当压力可以激发动力，但长期高压会损害身心健康。有效的压力管理包括：明确优先级、合理分配任务、学会说"不"。\n\n此外，建立工作之外的兴趣爱好和社交圈，保持运动习惯，定期进行"数字排毒"（暂时远离电子设备），都是有效的压力缓解方法。', publishTime: '2025-09-03 09:15:00', createdAt: '2025-09-03 09:15:00', updatedAt: '2025-09-03 09:15:00' },
    { articleId: 7, title: '情绪调节的五个有效策略', categoryId: 3, categoryName: '情绪管理', status: 1, author: 'MindMan', reads: 15, cover: '', summary: '掌握这些情绪调节策略，更好地掌控自己的内心世界。', tags: ['情绪调节', '实用技巧'], content: '情绪调节不是压抑情绪，而是学会健康地表达和管理情绪。以下是五个实用策略：\n\n1. 命名情绪——准确地识别并命名自己的情绪\n2. 呼吸调节——通过深呼吸激活副交感神经系统\n3. 认知重构——重新审视引发情绪的想法是否合理\n4. 身体运动——通过运动释放紧张能量\n5. 社交支持——找到可以信任的人倾诉\n\n每天练习这些方法，你会发现自己的情绪调适能力逐渐增强。', publishTime: '2025-09-02 14:30:00', createdAt: '2025-09-02 14:30:00', updatedAt: '2025-09-02 14:30:00' },
    { articleId: 8, title: '如何识别和管理焦虑情绪', categoryId: 3, categoryName: '情绪管理', status: 1, author: 'MindMan', reads: 12, cover: '', summary: '了解焦虑的成因和表现，掌握科学的管理方法。', tags: ['焦虑', '情绪管理'], content: '焦虑是一种常见的情绪反应，但当它严重影响生活时，就需要学习和运用管理方法。焦虑的可能表现包括：持续的紧张感、心悸、失眠、难以集中注意力。\n\n管理焦虑的关键步骤：首先学会识别触发因素，然后通过渐进式肌肉放松、正念冥想、规律运动等方式缓解症状。如果焦虑感持续超过两周，请及时寻求专业帮助。', publishTime: '2025-09-01 10:00:00', createdAt: '2025-09-01 10:00:00', updatedAt: '2025-09-01 10:00:00' }
  ]

  const extraTitles = [
    '如何建立稳定的日常作息', '缓解考试焦虑的小技巧', '正念呼吸练习指南', '认识抑郁症的早期信号',
    '与家人有效沟通的方法', '社交焦虑的自我调节', '培养积极心态的七个习惯', '冥想入门：每天十分钟',
    '如何应对职场倦怠', '亲子沟通中的倾听艺术', '走出情绪低谷的实用方法', '睡眠卫生：改善失眠的第一步',
    '焦虑时的身体放松练习', '提升自信心的日常练习', '亲密关系中的边界感', '独处与自我关怀',
    '运动对心理健康的益处', '如何应对突如其来的负面情绪', '年轻人的心理自助指南', '工作与生活的平衡之道',
    '认识强迫思维', '心理咨询常见问题解答'
  ]

  const categoryIds = [1, 2, 3, 4]
  const extras = extraTitles.map((title, i) => {
    const categoryId = categoryIds[i % categoryIds.length]
    const category = categoryDb.find((item) => item.categoryId === categoryId)
    const date = new Date(2025, 7, 31 - i, 10, 0, 0)
    const publishTime = formatDate(date)
    return {
      articleId: 9 + i,
      title,
      categoryId,
      categoryName: category?.categoryName || '',
      status: i % 6 === 3 ? 0 : 1,
      author: '系统管理员',
      reads: 5 + ((i * 7) % 60),
      cover: '',
      summary: '',
      tags: [],
      content: '',
      publishTime,
      createdAt: publishTime,
      updatedAt: publishTime
    }
  })

  return [...base, ...extras]
}

const articleDb = buildArticles()

export async function mockArticlePage(params = {}) {
  await delay()
  const { page = 1, pageSize = 10, title = '', keyword = '', categoryId, status } = params
  const kw = String(keyword || '').trim()
  let list = articleDb.filter((item) => {
    const matchTitle = !title || item.title.includes(title.trim())
    const matchKeyword = !kw || item.title.includes(kw) || (item.summary || '').includes(kw)
    const matchCategory = categoryId === undefined || categoryId === null || categoryId === '' || item.categoryId === Number(categoryId)
    const matchStatus = status === undefined || status === null || status === '' || item.status === Number(status)
    return matchTitle && matchKeyword && matchCategory && matchStatus
  })
  const total = list.length
  const start = (page - 1) * pageSize
  list = list.slice(start, start + pageSize)
  return { total, page, pageSize, list }
}

export async function mockArticleDetail(id) {
  await delay()
  const article = articleDb.find((item) => item.articleId === Number(id))
  if (!article) throw new Error('文章不存在')
  return { ...article, content: article.content || '这是文章正文示例内容，后端接通后返回真实内容。' }
}

export async function mockSaveArticle(data) {
  await delay()
  const maxId = Math.max(0, ...articleDb.map((item) => item.articleId))
  const article = {
    articleId: maxId + 1,
    categoryName: categoryDb.find((c) => c.categoryId === Number(data.categoryId))?.categoryName || '',
    status: 1,
    author: '系统管理员',
    reads: 0,
    cover: '',
    summary: '',
    tags: [],
    content: '',
    publishTime: now(),
    createdAt: now(),
    updatedAt: now(),
    ...data
  }
  articleDb.unshift(article)
  return { articleId: article.articleId }
}

export async function mockUpdateArticle(data) {
  await delay()
  const article = articleDb.find((item) => item.articleId === Number(data.articleId))
  if (!article) throw new Error('文章不存在')
  Object.assign(article, data, { updatedAt: now() })
  return null
}

export async function mockUpdateArticleStatus(data) {
  await delay(100)
  const article = articleDb.find((item) => item.articleId === Number(data.articleId))
  if (!article) throw new Error('文章不存在')
  article.status = Number(data.status)
  article.updatedAt = now()
  return null
}

export async function mockDeleteArticle(id) {
  await delay(100)
  const index = articleDb.findIndex((item) => item.articleId === Number(id))
  if (index > -1) articleDb.splice(index, 1)
  return null
}

// ---------- 咨询会话 ----------
const avatarUrl =
  'data:image/png;base64,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'

const sessionDb = [
  { sessionId: 10001, userId: 52, userName: 'allen', emotion: '难过', status: 2, statusText: '已结束', messageCount: 2, lastSender: '宁渡AI助手', lastTime: '2026-02-11 11:53:47', lastMessage: '哎呀，摔跤受伤了肯定很疼吧？先让我陪着你处理一下伤口好吗？首先要用清水轻轻冲洗伤口，如果有碘伏或酒精可以简单消毒，贴上创可贴保护伤口。你现在感觉怎么样？', startedAt: '2026-02-11 11:51:00', endedAt: '2026-02-11 11:53:47' },
  { sessionId: 10002, userId: 52, userName: 'allen', emotion: '开心', status: 2, statusText: '已结束', messageCount: 4, lastSender: '宁渡AI助手', lastTime: '2026-02-11 11:53:27', lastMessage: '先用清水轻轻冲洗伤口，如果有碘伏或酒精可以简单消毒。', startedAt: '2026-02-11 11:51:52', endedAt: '2026-02-11 11:53:27' },
  { sessionId: 10003, userId: 52, userName: 'allen', emotion: '开心', status: 2, statusText: '已结束', messageCount: 4, lastSender: '宁渡AI助手', lastTime: '2026-02-11 11:52:41', lastMessage: '哇！三轮面试都坚持下来了真的超厉害！很多人发挥一般时可能会心态不稳，但你不光完成了全部面试，还成功拿到了 offer，这说明你的综合能力一定很出色呢。', startedAt: '2026-02-11 11:51:52', endedAt: '2026-02-11 11:52:41' },
  { sessionId: 10004, userId: 52, userName: 'allen', emotion: '开心', status: 2, statusText: '已结束', messageCount: 2, lastSender: '宁渡AI助手', lastTime: '2026-01-26 16:42:47', lastMessage: '真为你感到高兴！阳光明媚的好天气确实能让人心情愉悦呢～要不要趁着好天气做些让自己更快乐的事呀？比如去校园里散散步、晒晒太阳。', startedAt: '2026-01-26 16:35:27', endedAt: '2026-01-26 16:42:47' },
  { sessionId: 10005, userId: 52, userName: 'allen', emotion: '平静', status: 2, statusText: '已结束', messageCount: 1, lastSender: 'allen', lastTime: '2026-01-26 16:35:27', lastMessage: '今天天气不错，心情很好', startedAt: '2026-01-26 16:35:27', endedAt: '2026-01-26 16:35:27' },
  { sessionId: 10006, userId: 52, userName: 'allen', emotion: '焦虑', status: 2, statusText: '已结束', messageCount: 5, lastSender: '宁渡AI助手', lastTime: '2026-01-26 10:40:17', lastMessage: '亲爱的同学，听到你这样说，我真的特别担心你。你现在一定承受着巨大的痛苦和煎熬。能不能告诉我你现在在哪里？有没有安全的环境？我强烈建议你...', startedAt: '2026-01-26 10:36:00', endedAt: '2026-01-26 10:40:17' },
  { sessionId: 10007, userId: 52, userName: 'allen', emotion: '开心', status: 2, statusText: '已结束', messageCount: 2, lastSender: '宁渡AI助手', lastTime: '2026-01-25 09:24:16', lastMessage: '哇！太棒了！看到你这么开心我也跟着高兴呢。考满分真是一件很值得骄傲的事情！看得出你一定付出了很多努力，这份成绩是你平时认真学习的最好回报。', startedAt: '2026-01-25 09:22:00', endedAt: '2026-01-25 09:24:16' }
]

sessionDb.forEach((item) => {
  item.avatar = item.userName === 'allen' ? avatarUrl : ''
})

// 发起咨询会话（新建会话）
export async function mockCreateSession(data = {}) {
  await delay()
  const maxId = Math.max(0, ...sessionDb.map((item) => item.sessionId))
  let userId = 0
  let userName = '用户'
  if (typeof localStorage !== 'undefined') {
    try {
      const user = JSON.parse(localStorage.getItem('mha_user') || 'null')
      if (user) {
        userId = user.userId || userId
        userName = user.nickname || user.username || userName
      }
    } catch (e) {
      /* 忽略解析错误 */
    }
  }
  const session = {
    sessionId: maxId + 1,
    userId,
    userName,
    emotion: data.emotion || '',
    status: 1,
    statusText: '进行中',
    messageCount: 0,
    lastSender: '',
    lastTime: now(),
    lastMessage: '',
    startedAt: now(),
    endedAt: null,
    avatar: ''
  }
  sessionDb.push(session)
  return { sessionId: session.sessionId }
}

const messageTemplates = {
  user: [
    '今天摔了一跤，膝盖受伤了，好疼。',
    '三轮面试都坚持下来了，今天收到了 offer，太开心了！',
    '今天天气不错，心情很好，出来晒晒太阳。',
    '最近压力有点大，晚上总是睡不着。',
    '考了满分，忍不住想和你分享这个好消息！',
    '和朋友闹了点矛盾，心里有点难受。'
  ],
  assistant: [
    '我听到你说这些，真的很关心你。可以多和我聊聊当时的情况吗？',
    '谢谢你愿意和我分享，我们一步一步来，慢慢梳理。',
    '这种感觉很真实，你已经做得很好了。',
    '可以试着深呼吸，我们一起慢慢放松下来。',
    '我很高兴你愿意告诉我这些，我一直在你身边。'
  ]
}

// 部分会话使用手写的完整对话（更贴近真实场景）
const messageOverrides = {
  10003: [
    { role: 'user', content: '一共 3 面，前两面感觉面的一般，但是最后还是通过了', createdAt: '2026-02-11 11:51:53' },
    { role: 'assistant', content: '恭喜你呀！这真是个值得开心的事情呢！能通过面试说明你的能力得到了认可，真的很为你感到高兴～可以和我分享一下这次面试的经历吗？比如：为了这次面试你做了哪些准备呢？', createdAt: '2026-02-11 11:51:59' },
    { role: 'user', content: '面试通过了', createdAt: '2026-02-11 11:52:32' },
    { role: 'assistant', content: '哇！三轮面试都坚持下来了真的超厉害！很多人发挥一般时可能会心态不稳，但你不光完成了全部面试，还成功拿到了 offer，这说明你的综合能力一定很出色呢。', createdAt: '2026-02-11 11:52:41' }
  ]
}

export async function mockSessionPage(params = {}) {
  await delay()
  const { page = 1, pageSize = 10, keyword = '', status } = params
  let list = sessionDb.filter((item) => {
    const kw = String(keyword || '').trim()
    const matchKeyword =
      !kw || item.userName.includes(kw) || String(item.sessionId).includes(kw) || String(item.userId).includes(kw)
    const matchStatus = status === undefined || status === null || status === '' || item.status === Number(status)
    return matchKeyword && matchStatus
  })
  const total = list.length
  const start = (page - 1) * pageSize
  return { total, page, pageSize, list: list.slice(start, start + pageSize) }
}

// 获取我的会话列表（用户端）
export async function mockMySessions(params = {}) {
  await delay()
  const { page = 1, pageSize = 20 } = params
  let userId = null
  let userName = null
  if (typeof localStorage !== 'undefined') {
    try {
      const user = JSON.parse(localStorage.getItem('mha_user') || 'null')
      if (user) {
        userId = user.userId
        userName = user.nickname || user.username
      }
    } catch (e) {
      /* 忽略解析错误 */
    }
  }
  let list = sessionDb
  if (userName) {
    list = list.filter((item) => item.userName === userName)
  } else if (userId !== null) {
    list = list.filter((item) => item.userId === userId)
  }
  // 最近的在前面
  const sorted = [...list].sort((a, b) =>
    (b.lastTime || b.startedAt || '').localeCompare(a.lastTime || a.startedAt || '')
  )
  const total = sorted.length
  return { total, page, pageSize, list: sorted.slice((page - 1) * pageSize, page * pageSize) }
}

export async function mockMessageList(sessionId) {
  await delay(150)
  const session = sessionDb.find((item) => item.sessionId === Number(sessionId))
  if (!session) return []
  // 尚未发送过消息的新会话没有聊天记录
  if (!session.messageCount) return []
  if (messageOverrides[session.sessionId]) {
    return messageOverrides[session.sessionId].map((message, i) => ({
      messageId: session.sessionId * 100 + i,
      sessionId: session.sessionId,
      ...message
    }))
  }
  const messages = []
  const count = Math.max(1, session.messageCount)
  for (let i = 0; i < count; i++) {
    const role = i % 2 === 0 ? 'user' : 'assistant'
    const isLast = i === count - 1
    const content = isLast
      ? session.lastMessage
      : role === 'user'
        ? messageTemplates.user[i % messageTemplates.user.length]
        : messageTemplates.assistant[i % messageTemplates.assistant.length]
    const minutesAgo = (count - i) * 2
    const d = new Date(session.lastTime.replace(' ', 'T'))
    d.setMinutes(d.getMinutes() - minutesAgo)
    const pad2 = (n) => String(n).padStart(2, '0')
    const createdAt = `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
    messages.push({
      messageId: session.sessionId * 100 + i,
      sessionId: session.sessionId,
      role,
      content,
      createdAt
    })
  }
  return messages
}

export async function mockDeleteSession(id) {
  await delay(100)
  const index = sessionDb.findIndex((item) => item.sessionId === Number(id))
  if (index === -1) throw new Error('会话不存在')
  sessionDb.splice(index, 1)
  return null
}

// 归档会话（结束当前会话，移入已归档）
export async function mockArchiveSession(id) {
  await delay(120)
  const session = sessionDb.find((item) => item.sessionId === Number(id))
  if (!session) throw new Error('会话不存在')
  session.status = 2
  session.statusText = '已结束'
  session.endedAt = now()
  return null
}

// ---------- AI 咨询回复 ----------
const aiReplies = [
  '我听到你的分享了，这种感觉一定很不好受。可以再和我多说一点吗？',
  '谢谢你愿意告诉我这些，我一直在这里陪着你。',
  '听起来这段时间确实不容易，我们不妨一起慢慢梳理一下？',
  '我能感觉到你在努力面对这些，这本身就很了不起。',
  '嗯，我在认真听。接下来你希望我从哪方面帮你呢？'
]

const aiKeywordReplies = [
  {
    keywords: ['失眠', '睡不', '睡不着', '睡眠'],
    reply: '睡眠问题常常和压力、情绪有关。可以试试睡前半小时远离手机、做几次缓慢的深呼吸，或者和我聊聊最近让你牵挂的事情？'
  },
  {
    keywords: ['焦虑', '紧张', '压力', '撑不住'],
    reply: '焦虑和压力是很多人都会经历的。试着把眼前的事情拆小一点，先完成一小步。要不要和我具体说说，最让你感到压力的那件事是什么？'
  },
  {
    keywords: ['难过', '伤心', '哭', '吵架', '矛盾'],
    reply: '和重要的人产生矛盾确实会让人很难过。你现在心里是什么感受？我陪着你，我们慢慢说。'
  },
  {
    keywords: ['开心', '高兴', '兴奋', '分享'],
    reply: '真为你感到开心！可以多和我分享一些细节吗？这些快乐的时刻也值得被好好记住。'
  },
  {
    keywords: ['活不下去', '自杀', '不想活', '结束生命', '轻生'],
    reply: '听到你这样说，我很担心你。你的感受非常重要，请一定不要独自承受。如果可以，请拨打 24 小时心理援助热线 400-161-9995，或者告诉身边信任的人，好吗？我一直在这里陪着你。'
  }
]

export async function mockSendChatMessage(sessionId, content) {
  await delay(600 + Math.random() * 800)
  const text = String(content || '')
  const matched = aiKeywordReplies.find((item) => item.keywords.some((kw) => text.includes(kw)))
  const reply = matched ? matched.reply : aiReplies[Math.floor(Math.random() * aiReplies.length)]
  // 按关键词附加玻璃卡片（心情分析 / 今日建议 / 睡眠预测）
  const cards = []
  if (/失眠|睡不/.test(text)) {
    cards.push({ type: 'sleep', label: '睡眠预测', emoji: '😴', title: '预计恢复', percent: 85 })
  } else if (/焦虑|紧张|压力|撑不住/.test(text)) {
    cards.push({ type: 'mood', label: '心情分析', emoji: '🙂', title: '平静', percent: 82 })
  } else if (/难过|伤心|哭|吵架|矛盾/.test(text)) {
    cards.push({ type: 'tip', label: '今日建议', emoji: '🌿', title: '呼吸练习', duration: '5 min' })
  }
  // 更新会话的最后消息与时间，保证会话列表预览实时准确
  const session = sessionDb.find((item) => item.sessionId === Number(sessionId))
  if (session) {
    session.lastMessage = reply
    session.lastSender = '宁渡AI助手'
    session.lastTime = now()
    session.messageCount = (session.messageCount || 0) + 2
  }
  const d = new Date()
  const pad2 = (n) => String(n).padStart(2, '0')
  return {
    messageId: Date.now(),
    sessionId: Number(sessionId),
    role: 'assistant',
    content: reply,
    cards,
    createdAt: `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
  }
}

// ---------- 情绪日记 ----------
const diaryDb = [
  { diaryId: 3001, sessionId: 10008, userId: 52, userName: 'allen', emotion: '平静', emotionScore: 3, sleepScore: 3, stressScore: 1, trigger: '无事件发生', content: '风平浪静', recordDate: '2026-01-29', createdAt: '2026-01-29 22:10:00' },
  { diaryId: 3002, sessionId: 10009, userId: 52, userName: 'allen', emotion: '开心', emotionScore: 5, sleepScore: 4, stressScore: 3, trigger: '彩票中奖了', content: '开心', recordDate: '2026-01-21', createdAt: '2026-01-21 20:30:00' },
  { diaryId: 3003, sessionId: 10010, userId: 38, userName: '阿哲', emotion: '焦虑', emotionScore: 2, sleepScore: 2, stressScore: 4, trigger: '工作压力大', content: '项目上线前很焦虑，睡不好。', recordDate: '2026-01-18', createdAt: '2026-01-18 23:05:00' },
  { diaryId: 3004, sessionId: 10011, userId: 52, userName: 'allen', emotion: '开心', emotionScore: 4, sleepScore: 4, stressScore: 2, trigger: '和朋友聚会', content: '和朋友们一起吃饭很开心。', recordDate: '2026-01-15', createdAt: '2026-01-15 21:40:00' },
  { diaryId: 3005, sessionId: 10012, userId: 71, userName: '晴晴', emotion: '平静', emotionScore: 3, sleepScore: 3, stressScore: 2, trigger: '做了正念练习', content: '正念冥想后心情很平静。', recordDate: '2026-01-12', createdAt: '2026-01-12 22:00:00' },
  { diaryId: 3006, sessionId: 10013, userId: 52, userName: 'allen', emotion: '难过', emotionScore: 1, sleepScore: 2, stressScore: 5, trigger: '和好朋友吵架', content: '因为小事和朋友吵架了，很难过。', recordDate: '2026-01-08', createdAt: '2026-01-08 23:30:00' },
  { diaryId: 3007, sessionId: 10014, userId: 52, userName: 'allen', emotion: '开心', emotionScore: 5, sleepScore: 5, stressScore: 1, trigger: '面试通过', content: '拿到 offer 了，特别开心！', recordDate: '2026-01-05', createdAt: '2026-01-05 21:00:00' },
  { diaryId: 3008, sessionId: 10015, userId: 38, userName: '阿哲', emotion: '平静', emotionScore: 4, sleepScore: 4, stressScore: 1, trigger: '周末休息', content: '睡到自然醒，精神很好。', recordDate: '2026-01-02', createdAt: '2026-01-02 10:30:00' }
]

diaryDb.forEach((item) => {
  item.avatar = item.userName === 'allen' ? avatarUrl : ''
})

export async function mockDiaryPage(params = {}) {
  await delay()
  const { page = 1, pageSize = 10, userId, emotion, scoreRange } = params
  let minScore = 0
  let maxScore = 5
  if (scoreRange === '1-2') {
    minScore = 1
    maxScore = 2
  } else if (scoreRange === '3') {
    minScore = 3
    maxScore = 3
  } else if (scoreRange === '4-5') {
    minScore = 4
    maxScore = 5
  }
  let list = diaryDb.filter((item) => {
    const kw = String(userId || '').trim()
    const matchUser =
      userId === undefined || userId === null || userId === '' || String(item.userId).includes(kw)
    const matchEmotion = !emotion || item.emotion === emotion
    const matchScore = item.emotionScore >= minScore && item.emotionScore <= maxScore
    return matchUser && matchEmotion && matchScore
  })
  const total = list.length
  const start = (page - 1) * pageSize
  return { total, page, pageSize, list: list.slice(start, start + pageSize) }
}

export async function mockDeleteDiary(id) {
  await delay(100)
  const index = diaryDb.findIndex((item) => item.diaryId === Number(id))
  if (index > -1) diaryDb.splice(index, 1)
  return null
}

// ---------- 综合分析 ----------
export async function mockAnalysisOverview() {
  await delay(300)
  return {
    userTotal: 3,
    activeUsers: 2,
    newUsersToday: 0,
    diaryTotal: 1,
    diaryToday: 0,
    sessionTotal: 11,
    sessionToday: 0,
    emotionHealth: 7,
    avgDuration: 18.6,
    emotionTrend: [
      { date: '2026-01-15', avgScore: 3.5, count: 5 },
      { date: '2026-01-18', avgScore: 4.2, count: 8 },
      { date: '2026-01-21', avgScore: 3.8, count: 6 },
      { date: '2026-01-25', avgScore: 4.5, count: 9 },
      { date: '2026-01-29', avgScore: 4.0, count: 7 },
      { date: '2026-02-02', avgScore: 3.2, count: 10 },
      { date: '2026-02-06', avgScore: 4.1, count: 6 }
    ],
    consultActivity: [
      { date: '2026-01-20', sessions: 8, users: 3 },
      { date: '2026-01-25', sessions: 12, users: 4 },
      { date: '2026-01-30', sessions: 6, users: 2 },
      { date: '2026-02-04', sessions: 15, users: 5 },
      { date: '2026-02-09', sessions: 10, users: 4 }
    ],
    sessionStats: [
      { date: '2026-01-15', sessions: 8 },
      { date: '2026-01-21', sessions: 3 },
      { date: '2026-01-27', sessions: 2.5 },
      { date: '2026-02-02', sessions: 2 },
      { date: '2026-02-08', sessions: 0 }
    ],
    activityTrend: [
      { date: '2026-01-15', activeUsers: 2, newUsers: 0, diaryUsers: 1, consultUsers: 1 },
      { date: '2026-01-21', activeUsers: 3, newUsers: 1, diaryUsers: 2, consultUsers: 2 },
      { date: '2026-01-27', activeUsers: 2, newUsers: 0, diaryUsers: 1, consultUsers: 1 },
      { date: '2026-02-02', activeUsers: 3, newUsers: 1, diaryUsers: 2, consultUsers: 2 },
      { date: '2026-02-08', activeUsers: 2, newUsers: 0, diaryUsers: 1, consultUsers: 2 }
    ]
  }
}

// ---------- AI 情绪分析（对话后自动分析） ----------
// 基于消息关键词的轻量模拟分析，返回压力值 / 焦虑指数 / 睡眠风险（0-100）
const EMOTION_KEYWORDS = {
  stress: ['累', '压力', '加班', '工作', '忙', '赶', '紧张', '疲惫', '烦', '崩溃', '扛不住', '项目', '任务', '业绩', '难', '加班', '繁琐', '负担'],
  anxiety: ['焦虑', '担心', '害怕', '慌', '不安', '未来', '面试', '选择', '纠结', '胡思乱想', '心跳', '喘不过气', '怕', '迷茫', '不确定', '慌张'],
  sleep: ['睡不着', '失眠', '睡觉', '凌晨', '噩梦', '半夜', '醒来', '熬夜', '睡眠', '困', '睡不好', '惊醒', '多梦', '辗转反侧'],
  low: ['难过', '伤心', '哭', '失落', '沮丧', '低落', '孤独', '无助', '委屈', '失望', '吵架', '矛盾', '分手', '失去', '想哭', '提不起劲'],
  happy: ['开心', '高兴', '顺利', '分享', '好消息', '太棒', '放松', '舒服', '轻松', '愉快', '感谢', '温暖', '喜欢', '幸福', '期待']
}

const clampValue = (n, min = 5, max = 97) => Math.min(max, Math.max(min, n))

const emotionLevel = (v) => (v >= 60 ? '偏高' : v >= 30 ? '中等' : '平稳')

export async function mockAvailableModels() {
  return {
    'Qwen3.8-Max（旗舰）': 'qwen3.8-max',
    'Qwen3.7-Max': 'qwen3.7-max',
    'Qwen3.7-Plus': 'qwen3.7-plus',
    'Qwen3.6-Plus': 'qwen3.6-plus',
    'Qwen3.6-Max Preview': 'qwen3.6-max-preview',
    'DeepSeek-V4-Pro': 'deepseek-v4-pro',
    'DeepSeek-V4-Flash': 'deepseek-v4-flash',
    'DeepSeek-V3.2': 'deepseek-v3.2',
    'GLM-5.2': 'glm-5.2'
  }
}

export async function mockAnalyzeEmotion(content = '') {
  await delay(700)
  const text = String(content || '')
  const hit = (words) => words.reduce((n, w) => n + (text.includes(w) ? 1 : 0), 0)
  const sHit = hit(EMOTION_KEYWORDS.stress)
  const aHit = hit(EMOTION_KEYWORDS.anxiety)
  const slHit = hit(EMOTION_KEYWORDS.sleep)
  const lHit = hit(EMOTION_KEYWORDS.low)
  const hHit = hit(EMOTION_KEYWORDS.happy)

  // 基准值，命中负面关键词逐步抬升，正面情绪词会缓和
  let stress = 58
  let anxiety = 38
  let sleepRisk = 24

  if (!sHit && !aHit && !slHit && !lHit) {
    stress = 22 + Math.round(Math.random() * 12)
    anxiety = 14 + Math.round(Math.random() * 12)
    sleepRisk = 8 + Math.round(Math.random() * 10)
    if (hHit > 0) {
      stress = Math.max(5, stress - hHit * 6)
      anxiety = Math.max(5, anxiety - hHit * 6)
      sleepRisk = Math.max(5, sleepRisk - hHit * 4)
    }
  } else {
    stress += sHit * 8 + (sHit > 1 ? 5 : 0) + lHit * 5
    anxiety += aHit * 10 + (aHit > 1 ? 6 : 0) + lHit * 4
    sleepRisk += slHit * 11 + (slHit > 1 ? 6 : 0)
    // 正面情绪词缓和负面指标
    const ease = hHit * 6
    stress -= ease
    anxiety -= ease
    sleepRisk -= ease * 0.6
    // 轻微随机扰动，让每次分析略有差异
    stress += Math.round(Math.random() * 7 - 3)
    anxiety += Math.round(Math.random() * 7 - 3)
    sleepRisk += Math.round(Math.random() * 7 - 3)
  }

  stress = clampValue(stress)
  anxiety = clampValue(anxiety)
  sleepRisk = clampValue(sleepRisk)

  // 主导情绪识别
  let emotion = '平稳'
  let emotionIcon = '😌'
  if (hHit > 0 && stress < 45 && anxiety < 45 && sleepRisk < 45) {
    emotion = '开心'
    emotionIcon = '😄'
  } else {
    const max = Math.max(stress, anxiety, sleepRisk)
    if (lHit > 0 && max <= 55) {
      emotion = '低落'
      emotionIcon = '🌥️'
    } else if (max === sleepRisk && sleepRisk >= 45) {
      emotion = '睡眠困扰'
      emotionIcon = '😴'
    } else if (max === anxiety) {
      emotion = '焦虑'
      emotionIcon = '😰'
    } else if (max === stress) {
      emotion = '压力'
      emotionIcon = '😮‍💨'
    }
  }

  // 情绪健康度：越高越健康
  const emotionScore = clampValue(100 - Math.round((stress + anxiety + sleepRisk) / 3), 5, 100)

  // 与后台「情绪日记」一致的 1-5 星制数据：emotionScore/sleepScore/stressScore
  // 情绪评分：综合情绪健康度转星（5=最好，1=最差）
  const emotionStar = clampValue(Math.round((emotionScore / 100) * 4) + 1, 1, 5)
  // 睡眠评分：sleepRisk 越低越好（5=睡得最好，1=失眠严重）
  const sleepStar = clampValue(Math.round(((100 - sleepRisk) / 100) * 4) + 1, 1, 5)
  // 压力评分：stress 越高代表压力越大（5=压力爆表，1=毫无压力）
  const stressStar = clampValue(Math.round((stress / 100) * 4) + 1, 1, 5)

  // 建议列表：按最突出的维度给出 2 条
  const tips = {
    stress: '试试 4-7-8 深呼吸，先给大脑 5 分钟暂停',
    anxiety: '把担心写下来，拆成一件件小事，逐件完成',
    sleep: '睡前 1 小时放下手机，泡杯温牛奶助眠',
    low: '允许自己休息一下，找信任的人说说话',
    happy: '好心情值得记录，把今天的快乐存进花园里'
  }
  const suggestions = []
  const rank = [
    { k: 'stress', v: stress },
    { k: 'anxiety', v: anxiety },
    { k: 'sleep', v: sleepRisk },
    { k: 'low', v: lHit > 0 ? 50 : 0 }
  ].sort((a, b) => b.v - a.v)
  rank.slice(0, 2).forEach((item) => {
    if (item.v >= 30 && tips[item.k]) suggestions.push(tips[item.k])
  })
  if (hHit > 0 && !suggestions.length) suggestions.push(tips.happy)
  if (!suggestions.length) suggestions.push('目前情绪很平稳，继续保持好心情')

  return {
    stress,
    anxiety,
    sleepRisk,
    stressLevel: emotionLevel(stress),
    anxietyLevel: emotionLevel(anxiety),
    sleepLevel: emotionLevel(sleepRisk),
    emotion,
    emotionIcon,
    emotionScore,
    emotionStar,
    sleepStar,
    stressStar,
    suggestions,
    analyzedAt: now()
  }
}

// ---------- 用户管理（管理端） ----------
const userDb = [
  { id: 1, username: 'admin', nickname: '管理员', avatar: '', email: 'admin@mindman.com', phone: '13800000000', role: 'admin', status: 1, createdAt: '2025-08-01 09:00:00', updatedAt: '2025-08-01 09:00:00' },
  { id: 52, username: 'allen', nickname: '阿伦', avatar: '', email: 'allen@example.com', phone: '13900000001', role: 'user', status: 1, createdAt: '2025-09-12 20:11:00', updatedAt: '2025-11-03 14:22:00' },
  { id: 38, username: 'azhe', nickname: '阿哲', avatar: '', email: 'azhe@example.com', phone: '13900000002', role: 'user', status: 1, createdAt: '2025-10-01 11:00:00', updatedAt: '2025-10-01 11:00:00' },
  { id: 71, username: 'qingqing', nickname: '晴晴', avatar: '', email: 'qq@example.com', phone: '13900000003', role: 'user', status: 0, createdAt: '2025-10-15 18:30:00', updatedAt: '2025-12-20 09:10:00' }
]

export async function mockAdminUsers(params = {}) {
  await delay()
  const { page = 1, pageSize = 10, keyword = '', role = '', status } = params
  const kw = String(keyword || '').trim()
  let list = userDb.filter((item) => {
    const matchKw = !kw || item.username.includes(kw) || item.nickname.includes(kw) || String(item.id).includes(kw)
    const matchRole = !role || item.role === role
    const matchStatus = status === undefined || status === null || status === '' || item.status === Number(status)
    return matchKw && matchRole && matchStatus
  })
  const total = list.length
  const start = (page - 1) * pageSize
  return { total, page, pageSize, list: list.slice(start, start + pageSize) }
}

export async function mockUpdateUserStatus(id, status) {
  await delay(120)
  const user = userDb.find((item) => item.id === Number(id))
  if (!user) throw new Error('用户不存在')
  user.status = Number(status)
  user.updatedAt = now()
  return null
}

export async function mockUpdateUserRole(id, role) {
  await delay(120)
  const user = userDb.find((item) => item.id === Number(id))
  if (!user) throw new Error('用户不存在')
  user.role = role
  user.updatedAt = now()
  return null
}

export async function mockDeleteUser(id) {
  await delay(120)
  const index = userDb.findIndex((item) => item.id === Number(id))
  if (index === -1) throw new Error('用户不存在')
  userDb.splice(index, 1)
  return null
}

// ---------- 情绪花园 ----------
const GARDEN_KEY = 'mha_garden'
let gardenCache = null

function getGardenRecords() {
  if (gardenCache) return JSON.parse(JSON.stringify(gardenCache))
  if (typeof localStorage !== 'undefined') {
    const saved = localStorage.getItem(GARDEN_KEY)
    if (saved) {
      try {
        gardenCache = JSON.parse(saved)
        return JSON.parse(JSON.stringify(gardenCache))
      } catch (e) {
        /* 忽略解析错误 */
      }
    }
  }
  // 首次使用：种下几朵示例花，让花园不至于空荡荡
  const demo = []
  const today = new Date()
  const emotions = [
    { e: '开心',  s: 5, sl: 4, st: 2, c: '和朋友们聚餐，聊得很开心' },
    { e: '平静',  s: 4, sl: 4, st: 2, c: '周末在家安静地看书' },
    { e: '还不错', s: 4, sl: 3, st: 3, c: '天气好，出去散了步' },
    { e: '有点低落', s: 2, sl: 2, st: 4, c: '今天有点提不起劲' },
    { e: '开心',  s: 5, sl: 5, st: 1, c: '睡到自然醒，开启元气满满的一天' }
  ]
  for (let i = 0; i < emotions.length; i++) {
    const d = new Date(today)
    d.setDate(d.getDate() - (emotions.length - i) * 2)
    const pad2 = (n) => String(n).padStart(2, '0')
    const date = `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
    const meta = emotions[i]
    demo.push({
      flowerId: 9000 + i,
      emotion: meta.e,
      content: meta.c,
      emotionScore: meta.s,
      sleepScore: meta.sl,
      stressScore: meta.st,
      date,
      createdAt: `${date} 10:00:00`
    })
  }
  if (typeof localStorage !== 'undefined') {
    localStorage.setItem(GARDEN_KEY, JSON.stringify(demo))
  }
  gardenCache = demo
  return demo
}

function saveGardenRecords(list) {
  gardenCache = list
  if (typeof localStorage !== 'undefined') {
    localStorage.setItem(GARDEN_KEY, JSON.stringify(list))
  }
}

export async function mockGetGarden() {
  await delay(150)
  return JSON.parse(JSON.stringify(getGardenRecords()))
}

export async function mockPlantFlower(data = {}) {
  await delay(200)
  const list = getGardenRecords()
  const flowerId = Date.now()
  const flower = {
    flowerId,
    emotion: String(data.emotion || '平静'),
    content: String(data.content || ''),
    emotionScore: Number(data.emotionScore) || 3,
    sleepScore: Number(data.sleepScore) || 3,
    stressScore: Number(data.stressScore) || 3,
    date: now().slice(0, 10),
    createdAt: now()
  }
  list.push(flower)
  saveGardenRecords(list)
  return { flowerId }
}

export async function mockUpdateFlower(flowerId, data = {}) {
  await delay(180)
  const list = getGardenRecords()
  const idx = list.findIndex((f) => f.flowerId === flowerId)
  if (idx < 0) throw new Error('花朵不存在')
  list[idx] = { ...list[idx], ...data }
  saveGardenRecords(list)
  return { flowerId }
}

export async function mockDeleteFlower(flowerId) {
  await delay(150)
  const list = getGardenRecords().filter((f) => f.flowerId !== flowerId)
  saveGardenRecords(list)
  return { flowerId }
}
