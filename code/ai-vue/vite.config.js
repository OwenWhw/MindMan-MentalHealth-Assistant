import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // 本地默认连接 8080；端口被其他项目占用时可在 .env 中覆盖。
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const apiProxyTarget = env.VITE_API_PROXY_TARGET || 'http://localhost:8080'

  return {
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // 允许通过任意 Host 访问（含 Cloudflare Tunnel / 内网穿透域名）
    allowedHosts: true,
    proxy: {
      // 开发环境下将 /api 请求转发到后端服务
      '/api': {
        target: apiProxyTarget,
        changeOrigin: true
      }
    }
  },
  preview: {
    // 允许通过任意 Host 访问（生产构建预览，供隧道/内网穿透使用）
    allowedHosts: true,
    proxy: {
      // 预览环境下同样将 /api 请求转发到后端服务
      '/api': {
        target: apiProxyTarget,
        changeOrigin: true
      }
    }
  },
  }
})
