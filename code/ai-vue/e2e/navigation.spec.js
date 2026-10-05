import { test, expect } from '@playwright/test'

test('未登录访问后台会跳转到登录页', async ({ page }) => {
  await page.goto('/#/back/index')

  await expect(page).toHaveURL(/#\/login/)
  await expect(page.getByRole('heading', { name: '登录您的账户' })).toBeVisible()
})

test('登录页显示账号和密码输入框', async ({ page }) => {
  await page.goto('/#/login')

  await expect(page.getByPlaceholder('请输入手机号或用户名')).toBeVisible()
  await expect(page.getByPlaceholder('请输入密码')).toBeVisible()
})

test('提交情绪记录后花园显示今日已种', async ({ page }) => {
  const now = new Date()
  const today = [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, '0'),
    String(now.getDate()).padStart(2, '0')
  ].join('-')
  let savedFlower = null

  await page.addInitScript(() => {
    localStorage.setItem('mha_token', 'playwright-test-token')
    localStorage.setItem('mha_user', JSON.stringify({ userId: 1, username: 'playwright', role: 'user' }))
  })

  // 用户设置组件会加载模型列表；假 token 不应请求真实后端并触发全局 401 退出。
  await page.route('**/api/chat/models', (route) =>
    route.fulfill({ json: { code: 200, data: [] } })
  )

  await page.route('**/api/emotion/garden', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({ json: { code: 200, data: savedFlower ? [savedFlower] : [] } })
      return
    }

    if (route.request().method() === 'POST') {
      savedFlower = {
        ...route.request().postDataJSON(),
        flowerId: 1,
        date: today,
        ratingSource: 'self_reported'
      }
      await route.fulfill({ json: { code: 200, data: null } })
      return
    }

    await route.continue()
  })

  await page.goto('/#/garden')
  await page.getByRole('button', { name: '种下今日心情' }).click()
  await page.getByRole('button', { name: '开心' }).click()
  await page.getByPlaceholder(/比如：和朋友聚餐/).fill('和朋友聊了很久，心情轻松了一些。')

  const ratingBlocks = page.locator('.rate-block')
  for (const label of ['情绪评分', '睡眠质量', '压力水平']) {
    await ratingBlocks
      .filter({ hasText: label })
      .locator('.el-rate__item')
      .nth(2)
      .click()
  }

  await page.getByRole('button', { name: '种下这朵花' }).click()

  await expect.poll(() => savedFlower?.content).toBe('和朋友聊了很久，心情轻松了一些。')
  expect(savedFlower).toMatchObject({ emotion: '开心', emotionScore: 3, sleepScore: 3, stressScore: 3 })
  await expect(page.getByText('今天已经种下啦，明天继续')).toBeVisible()
})
