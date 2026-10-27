import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Vite 配置：开发服务器端口 5173
// 代理：前端请求 /api/xxx 会被转发到后端 http://localhost:8080/xxx
// 这样前端代码里不用写死后端地址，也不会跨域
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: true,   // 监听所有网卡：手机连同一个WiFi后可通过电脑IP访问（手机打卡页需要）
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      // POST /upload（文件上传接口，单数）→ 后端
      '/upload': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // 上传的照片直接走后端，不摘前缀
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
