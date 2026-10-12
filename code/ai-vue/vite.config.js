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
    // 上线状态：/api 转发到远端后端。
    // /files/** 不在这个前缀下，静态资源基址见 src/config/index.js，两处要一起改。
    proxy: {
      '/api': {
      target: 'http://159.75.169.224:1235',
      changeOrigin: true
      }
    }
  }
})
