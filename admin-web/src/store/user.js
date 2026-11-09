import { defineStore } from 'pinia'

// 把 double-encoding 字符串修复回正确中文
// 原理：double-encoding 是 UTF-8 字节被当成 Latin-1 字符再编码了一次
// e.g. 原始 UTF-8 字节 [E5,88,98] → Latin-1 字符 [åˆ˜] → 再 UTF-8 编码 → C3A5 C288 C298
// 修复：先用 Latin-1 把这些 UTF-8 字节"当字符"解读回来，再用 UTF-8 重新编码
//
// 但浏览器 JS 字符串是 UTF-16，要模拟这个过程：
// new TextDecoder('utf-8').decode(new TextEncoder('latin1').encode(s))
function fixDoubleEncoding(s) {
  if (!s || typeof s !== 'string') return s
  try {
    const bytes = new TextEncoder('latin1').encode(s)
    const fixed = new TextDecoder('utf-8').decode(bytes)
    // 如果修复后字符串长度变短或完全不同，说明确实修好了
    return fixed.length < s.length ? fixed : s
  } catch (e) {
    return s
  }
}

// 深度修复整个 userInfo 对象里的所有字符串字段
function fixUserInfo(info) {
  if (!info) return info
  const fixed = { ...info }
  for (const key of Object.keys(fixed)) {
    if (typeof fixed[key] === 'string') {
      fixed[key] = fixDoubleEncoding(fixed[key])
    }
  }
  return fixed
}

// Pinia 用户状态仓库：存 token 和当前登录用户信息
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    // 读 localStorage 时自动修复（编码加固前登录的脏数据）
    userInfo: fixUserInfo(JSON.parse(localStorage.getItem('userInfo') || '{}'))
  }),
  actions: {
    setLogin(token, userInfo) {
      this.token = token
      // 新登录的数据理论上是对的，但保险也修一下
      this.userInfo = fixUserInfo(userInfo)
      localStorage.setItem('token', token)
      localStorage.setItem('userInfo', JSON.stringify(this.userInfo))
    },
    logout() {
      this.token = ''
      this.userInfo = {}
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
    }
  }
})
