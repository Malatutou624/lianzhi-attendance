// 统一请求封装 - Uni-app 版本
const BASE_URL = import.meta.env.BACKEND_BASE || 'http://localhost:8080'
console.log('[DEBUG] BASE_URL =', BASE_URL)

interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: any
  header?: any
  loading?: boolean
}

function getToken(): string {
  return uni.getStorageSync('h5Token') || ''
}

export function request<T = any>(opts: RequestOptions): Promise<T> {
  return new Promise((resolve, reject) => {
    const header: any = { 'Content-Type': 'application/json' }
    const token = getToken()
    if (token) header['Authorization'] = 'Bearer ' + token
    Object.assign(header, opts.header || {})

    const fullUrl = BASE_URL + opts.url
    console.log('[DEBUG] 📤', opts.method || 'GET', fullUrl, 'body=', JSON.stringify(opts.data || {}))

    if (opts.loading !== false) {
      uni.showLoading({ title: '加载中...', mask: true })
    }

    uni.request({
      url: fullUrl,
      method: opts.method || 'GET',
      data: opts.data,
      header,
      timeout: 10000,
      success(res: any) {
        if (opts.loading !== false) uni.hideLoading()
        console.log('[DEBUG] 📥 statusCode=', res.statusCode, 'body=', typeof res.data === 'string' ? res.data.substring(0, 200) : JSON.stringify(res.data).substring(0, 200))

        if (res.statusCode === 401) {
          uni.removeStorageSync('h5Token')
          uni.showToast({ title: '请先登录', icon: 'none' })
          uni.reLaunch({ url: '/pages/login/login' })
          reject(new Error('未登录'))
          return
        }

        const body = res.data
        if (body && body.code === 200) {
          resolve(body.data as T)
        } else {
          const msg = body?.msg || '请求失败'
          uni.showToast({ title: msg, icon: 'none' })
          reject(new Error(msg))
        }
      },
      fail(err: any) {
        if (opts.loading !== false) uni.hideLoading()
        console.error('[DEBUG] ❌ 请求失败:', JSON.stringify(err))
        uni.showToast({ title: '网络错误: ' + (err.errMsg || '未知'), icon: 'none', duration: 2500 })
        reject(err)
      }
    })
  })
}

export const http = {
  get: <T = any>(url: string, data?: any, opts?: Partial<RequestOptions>) => request<T>({ url, method: 'GET', data, ...opts }),
  post: <T = any>(url: string, data?: any, opts?: Partial<RequestOptions>) => request<T>({ url, method: 'POST', data, ...opts }),
  put: <T = any>(url: string, data?: any, opts?: Partial<RequestOptions>) => request<T>({ url, method: 'PUT', data, ...opts }),
  del: <T = any>(url: string, data?: any, opts?: Partial<RequestOptions>) => request<T>({ url, method: 'DELETE', data, ...opts })
}

export { BASE_URL }
