import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(import.meta.dirname, 'src'),
    },
  },
  server:{
    // 本地开发：/api 转发到本地后端（端口见 application.yml）。
    // /files/** 不在这个前缀下，静态资源基址见 src/config/index.js，两处要一起改。
    proxy: {
      '/api': {
      target: 'http://localhost:1236',
      changeOrigin: true
      }
    }
  }
})
