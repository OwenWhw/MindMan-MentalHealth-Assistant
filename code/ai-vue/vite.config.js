import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vite.dev/config/
export default defineConfig({
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
        target: 'http://localhost:8080',
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
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
})
