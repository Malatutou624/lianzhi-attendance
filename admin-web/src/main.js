import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import 'element-plus/dist/index.css'
import './styles/global.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)
app.use(createPinia())          // Pinia 状态管理
app.use(router)                 // 路由
app.use(ElementPlus, { locale: zhCn })  // ElementPlus 组件库（中文）
app.mount('#app')
