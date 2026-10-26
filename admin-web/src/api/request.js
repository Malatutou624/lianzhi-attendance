import axios from 'axios'
import { ElMessage } from 'element-plus'

// axios 实例：统一加 baseURL、token 请求头、错误处理
// 所有页面 import request from '@/api/request' 后直接用，不用每个页面重复写这些逻辑
const request = axios.create({
  baseURL: '/api',   // 经 vite 代理转发到后端 8080
  timeout: 10000
})

// 请求拦截器：每次请求自动带上 token
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = token
  }
  return config
})

// 响应拦截器：统一处理错误
request.interceptors.response.use(
  (res) => {
    const data = res.data
    if (data.code !== 200) {
      ElMessage.error(data.msg || '请求失败')
      return Promise.reject(data)
    }
    return data   // 页面里拿到的直接是 {code,msg,data}
  },
  (err) => {
    if (err.response && err.response.status === 401) {
      ElMessage.error('登录已过期，请重新登录')
      localStorage.removeItem('token')
      window.location.href = '/login'
    } else {
      ElMessage.error('网络错误：' + err.message)
    }
    return Promise.reject(err)
  }
)

export default request
