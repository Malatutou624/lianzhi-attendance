import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// 后端地址
// 方式 A：本地调试（开发者工具模拟器）→ http://10.65.251.53:8080 （电脑局域网 IP）
// 方式 B：内网穿透真机测试 → https://xxx.trycloudflare.com
// 方式 C：正式部署 → https://你的域名
const BACKEND_BASE = 'http://10.65.251.33:8080'

export default defineConfig({
  plugins: [uni()],
  define: {
    'import.meta.env.BACKEND_BASE': JSON.stringify(BACKEND_BASE)
  }
})
