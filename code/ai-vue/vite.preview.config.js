import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import { viteSingleFile } from 'vite-plugin-singlefile'

// 离线预览版构建：生成单个 index.html，可直接用浏览器打开（file://）
export default defineConfig({
  plugins: [vue(), viteSingleFile()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  build: {
    outDir: 'dist-preview',
    assetsInlineLimit: 100000000,
    chunkSizeWarningLimit: 100000
  }
})
