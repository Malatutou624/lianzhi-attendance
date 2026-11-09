<template>
  <view class="login-container">
    <view class="logo-area">
      <image class="logo" src="/static/logo.png" mode="aspectFit" />
      <text class="title">联智考勤</text>
      <text class="subtitle">SMART ATTENDANCE</text>
    </view>

    <view class="form-area">
      <view class="input-group">
        <input v-model="username" class="input" placeholder="请输入账号" />
        <input v-model="password" class="input" placeholder="请输入密码" password />
      </view>

      <button class="login-btn" @click="pwdLogin">登 录</button>
      <view class="register-btn" @tap="goRegister">注册新账号</view>
    </view>

    <view class="footer">
      <text>© 2026 联智智能考勤系统</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { BASE_URL } from '@/utils/request'
const username = ref('')
const password = ref('')

function goRegister() {
  uni.showModal({
    title: '注册新账号',
    editable: true,
    placeholderText: '请输入账号（至少3位）',
    success: (r1) => {
      if (!r1.confirm || !r1.content) return
      const regUsername = r1.content.trim()
      if (regUsername.length < 3) {
        uni.showToast({ title: '账号至少3位', icon: 'none' })
        return
      }
      uni.showModal({
        title: '设置密码',
        editable: true,
        placeholderText: '请输入密码（至少6位）',
        success: async (r2) => {
          if (!r2.confirm || !r2.content) return
          const regPassword = r2.content.trim()
          if (regPassword.length < 6) {
            uni.showToast({ title: '密码至少6位', icon: 'none' })
            return
          }
          uni.showLoading({ title: '注册中...', mask: true })
          try {
            const res = await new Promise<any>((resolve, reject) => {
              ;(wx as any).request({
                url: BASE_URL + '/h5/auth/register',
                method: 'POST',
                data: JSON.stringify({ username: regUsername, password: regPassword }),
                header: { 'Content-Type': 'application/json' },
                timeout: 10000,
                success: (r: any) => resolve(r),
                fail: (e: any) => reject(e)
              })
            })
            uni.hideLoading()
            if (res.statusCode === 200) {
              const data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
              if (data.code === 200) {
                uni.showToast({ title: '注册成功，自动登录', icon: 'success' })
                uni.setStorageSync('h5Token', data.data.token)
                uni.setStorageSync('accountInfo', data.data)
                setTimeout(() => { uni.reLaunch({ url: '/pages/portal/portal' }) }, 600)
              } else {
                uni.showToast({ title: data?.msg || '注册失败', icon: 'none' })
              }
            } else {
              uni.showToast({ title: 'HTTP ' + res.statusCode, icon: 'none' })
            }
          } catch (err: any) {
            uni.hideLoading()
            uni.showToast({ title: '网络错误', icon: 'none' })
          }
        }
      })
    }
  })
}

function pwdLogin() {
  if (!username.value || !password.value) {
    uni.showToast({ title: '请输入账号密码', icon: 'none' })
    return
  }
  uni.showLoading({ title: '登录中...', mask: true })
  
  const url = BASE_URL + '/h5/auth/login'
  const body = JSON.stringify({ username: username.value, password: password.value })
  console.log('[LOGIN] 准备发请求 →', url)
  console.log('[LOGIN] 请求体 →', body)
  console.log('[LOGIN] wx 对象存在吗？', typeof wx, 'wx.request 存在吗？', typeof (wx as any).request)

  try {
    const task = (wx as any).request({
      url: url,
      method: 'POST',
      data: body,
      header: { 'Content-Type': 'application/json' },
      timeout: 10000,
      success(res: any) {
        uni.hideLoading()
        console.log('[LOGIN] ✅ success 回调触发！statusCode=', res.statusCode)
        console.log('[LOGIN] 原始响应 =', typeof res.data === 'string' ? res.data : JSON.stringify(res.data))
        if (res.statusCode === 200) {
          const data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
          if (data && data.code === 200) {
            uni.setStorageSync('h5Token', data.data.token)
            uni.setStorageSync('accountInfo', data.data)
            uni.showToast({ title: '登录成功', icon: 'success' })
            setTimeout(() => { uni.reLaunch({ url: '/pages/portal/portal' }) }, 500)
          } else {
            uni.showToast({ title: data?.msg || '登录失败', icon: 'none' })
          }
        } else {
          uni.showToast({ title: 'HTTP ' + res.statusCode, icon: 'none' })
        }
      },
      fail(err: any) {
        uni.hideLoading()
        console.error('[LOGIN] ❌ fail 回调触发！', JSON.stringify(err))
        uni.showModal({
          title: '登录失败',
          content: '网络错误：' + (err.errMsg || JSON.stringify(err)) + '\n\n请在开发者工具「详情 → 本地设置」里勾选「不校验合法域名」',
          showCancel: false
        })
      },
      complete(res: any) {
        console.log('[LOGIN] 🎯 complete 回调触发', JSON.stringify(res))
      }
    })
    console.log('[LOGIN] 🚀 wx.request 已调用！返回的 task=', task)
  } catch (e: any) {
    uni.hideLoading()
    console.error('[LOGIN] 💥 try 里抛异常了！', e?.message, e?.stack)
    uni.showModal({ title: '代码异常', content: e?.message || String(e), showCancel: false })
  }
}
</script>

<style lang="scss" scoped>
.login-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #1a365d 0%, #2b6cb0 50%, #805ad5 100%);
  display: flex;
  flex-direction: column;
  padding: 100rpx 60rpx 60rpx;

  .logo-area {
    text-align: center;
    margin-bottom: 80rpx;

    .logo {
      width: 160rpx;
      height: 160rpx;
      margin-bottom: 20rpx;
      border-radius: 30rpx;
    }
    .title {
      display: block;
      font-size: 56rpx;
      font-weight: 800;
      color: #fff;
      letter-spacing: 8rpx;
    }
    .subtitle {
      display: block;
      font-size: 24rpx;
      color: rgba(255, 255, 255, 0.7);
      margin-top: 10rpx;
      letter-spacing: 6rpx;
    }
  }

  .form-area {
    background: #fff;
    border-radius: 24rpx;
    padding: 50rpx 40rpx;

    .input-group {
      margin-bottom: 30rpx;
      .input {
        border-bottom: 1rpx solid #eee;
        padding: 24rpx 0;
        font-size: 28rpx;
        margin-bottom: 10rpx;
      }
    }

    .login-btn {
      background: #2b6cb0;
      color: #fff;
      border-radius: 50rpx;
      font-size: 32rpx;
      padding: 24rpx 0;
      margin-top: 20rpx;
    }

    .register-btn {
      background: transparent;
      color: #2b6cb0;
      font-size: 26rpx;
      margin-top: 20rpx;
      text-align: center;
      padding: 20rpx 0;
      min-height: 64rpx;
      line-height: 64rpx;
    }
  }

  .footer {
    text-align: center;
    color: rgba(255, 255, 255, 0.5);
    font-size: 22rpx;
    margin-top: auto;
  }
}
</style>
