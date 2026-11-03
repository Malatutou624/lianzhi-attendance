<template>
  <view class="portal">
    <!-- 渐变头图 + 用户信息 -->
    <view class="hero">
      <view class="hero-bg"></view>
      <view class="hero-content">
        <view v-if="accountInfo" class="user-info">
          <view class="avatar">
            {{ (accountInfo.employee?.name || accountInfo.username || 'L').charAt(0) }}
            <view class="avatar-ring"></view>
          </view>
          <view class="info">
            <text class="greeting">你好 👋</text>
            <text class="name">{{ accountInfo.employee?.name || accountInfo.username }}</text>
            <text class="dept">{{ accountInfo.employee?.dept || '待采集员工信息' }}</text>
          </view>
          <view class="logout" @click="logout">
            <text>退出</text>
          </view>
        </view>
        <view v-else class="not-login">
          <text class="welcome">欢迎使用联智考勤</text>
          <view class="btn-primary" @click="goLogin">立即登录</view>
        </view>
      </view>
    </view>

    <!-- 快捷统计卡片 -->
    <view v-if="stats" class="stats-row">
      <view class="stat-card blue">
        <text class="num">{{ stats.attendDays }}</text>
        <text class="label">出勤天数</text>
        <view class="stat-icon">📅</view>
      </view>
      <view class="stat-card orange">
        <text class="num">{{ stats.lateCount }}</text>
        <text class="label">迟到次数</text>
        <view class="stat-icon">⏰</view>
      </view>
      <view class="stat-card red">
        <text class="num">{{ stats.earlyCount }}</text>
        <text class="label">早退次数</text>
        <view class="stat-icon">🏃</view>
      </view>
      <view class="stat-card purple">
        <text class="num">{{ stats.totalHours }}</text>
        <text class="label">累计工时(h)</text>
        <view class="stat-icon">⚡</view>
      </view>
    </view>

    <!-- 功能入口 2×3 -->
    <view class="entries">
      <view class="entry-card collect" @click="goCollect">
        <view class="entry-icon-wrap">
          <text class="entry-icon">📋</text>
        </view>
        <text class="label">信息采集</text>
        <text class="sub">人脸 + 资料</text>
      </view>
      <view class="entry-card checkin" @click="goCheckin">
        <view class="entry-icon-wrap">
          <text class="entry-icon">⏰</text>
        </view>
        <text class="label">上班打卡</text>
        <text class="sub">人脸识别</text>
      </view>
      <view class="entry-card attendance" @click="goHome">
        <view class="entry-icon-wrap">
          <text class="entry-icon">📊</text>
        </view>
        <text class="label">个人考勤</text>
        <text class="sub">打卡明细</text>
      </view>
      <view class="entry-card ai" @click="goAI">
        <view class="entry-icon-wrap">
          <text class="entry-icon">🤖</text>
        </view>
        <text class="label">AI 问答</text>
        <text class="sub">智能助手</text>
      </view>
      <view class="entry-card notice" @click="goNotice">
        <view class="entry-icon-wrap">
          <text class="entry-icon">🔔</text>
          <view v-if="unreadCount > 0" class="badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</view>
        </view>
        <text class="label">消息中心</text>
        <text class="sub">
          <text v-if="unreadCount > 0" class="unread-hint">{{ unreadCount }} 条未读</text>
          <text v-else class="unread-hint">补卡/月报</text>
        </text>
      </view>
      <view class="entry-card report" @click="goReport">
        <view class="entry-icon-wrap">
          <text class="entry-icon">📈</text>
        </view>
        <text class="label">月度报告</text>
        <text class="sub">上月汇总</text>
      </view>
    </view>

    <!-- 底部 -->
    <view class="footer">
      <text>联智考勤 · 智能出勤管理系统</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { http } from '@/utils/request'

const accountInfo = ref<any>(null)
const stats = ref<any>(null)
const unreadCount = ref(0)

onShow(async () => {
  await refreshAccountInfo()
  if (accountInfo.value) {
    loadStats()
    loadUnread()
  }
})

async function refreshAccountInfo() {
  try {
    const info = await http.get('/h5/auth/info', undefined, { loading: false })
    accountInfo.value = info
    uni.setStorageSync('accountInfo', info)
  } catch {
    accountInfo.value = uni.getStorageSync('accountInfo') || null
  }
}

async function loadStats() {
  const now = new Date()
  const month = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  try { stats.value = await http.get('/h5/stats', { month }) } catch {}
}

async function loadUnread() {
  try {
    const c = await http.get('/h5/notice/unread', undefined, { loading: false })
    unreadCount.value = Number(c) || 0
  } catch {}
}

function goLogin() { uni.navigateTo({ url: '/pages/login/login' }) }
function goCollect() { uni.navigateTo({ url: '/pages/collect/collect' }) }
function goCheckin() { uni.switchTab({ url: '/pages/checkin/checkin' }) }
function goHome() { uni.switchTab({ url: '/pages/home/home' }) }
function goAI() { uni.switchTab({ url: '/pages/ai/ai' }) }
function goNotice() { uni.navigateTo({ url: '/pages/notice/notice' }) }
function goReport() { uni.navigateTo({ url: '/pages/report/report' }) }

function logout() {
  uni.removeStorageSync('h5Token')
  uni.removeStorageSync('accountInfo')
  uni.reLaunch({ url: '/pages/login/login' })
}
</script>

<style lang="scss" scoped>
.portal {
  min-height: 100vh;
  background: #f5f7fa;
}

/* ===== Hero 头图 ===== */
.hero {
  position: relative;
  padding: 60rpx 32rpx 80rpx;
  overflow: hidden;
}

.hero-bg {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: linear-gradient(135deg, #1e3a5f 0%, #2b6cb0 40%, #805ad5 100%);
}
.hero-bg::after {
  content: '';
  position: absolute;
  bottom: -40rpx; left: -20rpx; right: -20rpx; height: 100rpx;
  background: #f5f7fa;
  border-radius: 60rpx 60rpx 0 0;
}

.hero-content { position: relative; z-index: 1; }

/* 用户信息 */
.user-info {
  display: flex; align-items: center;
  gap: 20rpx;
}

.avatar {
  width: 96rpx; height: 96rpx;
  border-radius: 50%;
  background: rgba(255,255,255,0.25);
  backdrop-filter: blur(10px);
  display: flex; align-items: center; justify-content: center;
  font-size: 42rpx; font-weight: bold; color: #fff;
  position: relative;
}
.avatar-ring {
  position: absolute;
  inset: -4rpx;
  border-radius: 50%;
  border: 2rpx solid rgba(255,255,255,0.5);
}

.info { flex: 1; display: flex; flex-direction: column; gap: 4rpx; }
.greeting { font-size: 24rpx; color: rgba(255,255,255,0.8); }
.name { font-size: 36rpx; font-weight: 700; color: #fff; letter-spacing: 1px; }
.dept { font-size: 24rpx; color: rgba(255,255,255,0.7); }

.logout {
  padding: 10rpx 24rpx;
  background: rgba(255,255,255,0.2);
  border-radius: 30rpx;
  font-size: 24rpx;
  color: #fff;
  backdrop-filter: blur(4px);
}

/* 未登录 */
.not-login {
  display: flex; flex-direction: column; align-items: center;
  gap: 20rpx;
}
.welcome { color: #fff; font-size: 36rpx; font-weight: 600; }

/* ===== 统计卡片 ===== */
.stats-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20rpx;
  padding: 0 24rpx;
  margin-top: -40rpx;
  position: relative;
  z-index: 2;
}

.stat-card {
  background: #fff;
  border-radius: 20rpx;
  padding: 24rpx;
  position: relative;
  overflow: hidden;
  box-shadow: 0 4rpx 16rpx rgba(0,0,0,0.06);
  display: flex; flex-direction: column;
}
.stat-card::after {
  content: '';
  position: absolute; right: -20rpx; top: -20rpx;
  width: 80rpx; height: 80rpx; border-radius: 50%;
  opacity: 0.12;
}
.stat-card.blue::after { background: #4299e1; }
.stat-card.orange::after { background: #ed8936; }
.stat-card.red::after { background: #f56565; }
.stat-card.purple::after { background: #805ad5; }

.stat-card.blue .num { color: #2b6cb0; }
.stat-card.orange .num { color: #dd6b20; }
.stat-card.red .num { color: #e53e3e; }
.stat-card.purple .num { color: #805ad5; }

.num { font-size: 44rpx; font-weight: 700; line-height: 1; }
.label { font-size: 24rpx; color: #a0aec0; margin-top: 6rpx; }

.stat-icon {
  position: absolute; right: 24rpx; bottom: 20rpx;
  font-size: 36rpx; opacity: 0.4;
}

/* ===== 功能入口 ===== */
.entries {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 20rpx;
  padding: 32rpx 24rpx;
}

.entry-card {
  background: #fff;
  border-radius: 20rpx;
  padding: 28rpx 16rpx;
  display: flex; flex-direction: column; align-items: center;
  gap: 8rpx;
  box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.04);
  transition: transform 0.2s, box-shadow 0.2s;
  position: relative;
}
.entry-card:active { transform: scale(0.96); box-shadow: 0 1rpx 4rpx rgba(0,0,0,0.06); }

.entry-icon-wrap {
  width: 72rpx; height: 72rpx;
  border-radius: 18rpx;
  display: flex; align-items: center; justify-content: center;
  position: relative;
}
.entry-icon { font-size: 40rpx; }

.entry-card.collect .entry-icon-wrap { background: linear-gradient(135deg, #e6fffa, #b2f5ea); }
.entry-card.checkin .entry-icon-wrap { background: linear-gradient(135deg, #ebf8ff, #bee3f8); }
.entry-card.attendance .entry-icon-wrap { background: linear-gradient(135deg, #f0fff4, #c6f6d5); }
.entry-card.ai .entry-icon-wrap { background: linear-gradient(135deg, #faf5ff, #e9d8fd); }
.entry-card.notice .entry-icon-wrap { background: linear-gradient(135deg, #fffaf0, #feebc8); }
.entry-card.report .entry-icon-wrap { background: linear-gradient(135deg, #f0fff4, #9ae6b4); }

.label { font-size: 26rpx; font-weight: 600; color: #2d3748; margin-top: 6rpx; }
.sub { font-size: 20rpx; color: #a0aec0; }
.unread-hint { color: #ed8936; }

.badge {
  position: absolute;
  top: -8rpx; right: -8rpx;
  min-width: 32rpx; height: 32rpx;
  padding: 0 8rpx;
  background: #f56565;
  color: #fff;
  font-size: 20rpx;
  border-radius: 16rpx;
  line-height: 32rpx;
  text-align: center;
  box-shadow: 0 2rpx 6rpx rgba(245,101,101,0.5);
}

/* ===== 底部 ===== */
.footer {
  padding: 40rpx 0 60rpx;
  text-align: center;
  color: #cbd5e0;
  font-size: 22rpx;
}
</style>
