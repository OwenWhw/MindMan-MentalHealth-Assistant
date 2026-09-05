import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import { join } from 'node:path'

// 离线预览版静态服务：任何路径都返回 dist-preview/index.html
const file = join(process.cwd(), 'dist-preview', 'index.html')
const port = Number(process.env.PREVIEW_PORT || 8088)

createServer(async (req, res) => {
  try {
    const data = await readFile(file)
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' })
    res.end(data)
  } catch (e) {
    res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' })
    res.end('预览文件未生成，请先构建 dist-preview/index.html')
  }
}).listen(port, '0.0.0.0', () => {
  console.log(`preview serving on http://0.0.0.0:${port}`)
})
