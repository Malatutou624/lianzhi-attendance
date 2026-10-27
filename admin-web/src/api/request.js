import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 统一 axios 实例：
 * - 管理端：localStorage['token']      → 后端拦截器识别为管理端登录
 * - H5 员工端：localStorage['h5Token'] → 后端拦截器识别为 H5 登录
 * 后端拦截器按 URI 前缀区分：/api/** 用管理端 token，/h5/** 用 h5 token
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

request.interceptors.request.use((config) => {
  // 自动带 token：当前页面是 H5 页面，或者 URL 以 /h5/ 开头 → 带 h5Token；否则带管理端 token
  const uri = config.url || ''
  const isH5Page = typeof window !== 'undefined' && window.location.pathname.startsWith('/h5')
  if (uri.startsWith('/h5/') || isH5Page) {
    const h5Token = localStorage.getItem('h5Token')
    if (h5Token) config.headers.Authorization = h5Token
  } else {
    const token = localStorage.getItem('token')
    if (token) config.headers.Authorization = token
  }
  return config
})

request.interceptors.response.use(
  (res) => {
    const data = res.data
    if (data.code !== 200) {
      ElMessage.error(data.msg || '请求失败')
      return Promise.reject(data)
    }
    return data
  },
  (err) => {
    if (err.response && err.response.status === 401) {
      const uri = err.config?.url || ''
      // 判断依据：请求 URL 以 /h5/ 开头 → H5；或当前页面路径以 /h5/ 开头（H5 页面调管理端接口也应该跳 H5 登录）
      const isH5Req = uri.startsWith('/h5/')
      const isH5Page = window.location.pathname.startsWith('/h5')
      if (isH5Req || isH5Page) {
        localStorage.removeItem('h5Token')
        ElMessage.error('请先登录')
        window.location.href = '/h5/login'
      } else {
        localStorage.removeItem('token')
        ElMessage.error('登录已过期，请重新登录')
        window.location.href = '/login'
      }
    } else {
      ElMessage.error('网络错误：' + (err.message || '未知'))
    }
    return Promise.reject(err)
  }
)

export default request
