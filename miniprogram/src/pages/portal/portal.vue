<template>
  <view class="portal">
    <!-- 头部用户信息 -->
    <view class="header">
      <view v-if="accountInfo" class="user-info">
        <view class="avatar">{{ (accountInfo.employee?.name || accountInfo.username || 'L').charAt(0) }}</view>
        <view class="info">
          <text class="name">{{ accountInfo.employee?.name || accountInfo.username }}</text>
          <text class="dept">{{ accountInfo.employee?.dept || '待采集员工信息' }}</text>
        </view>
        <button class="logout-btn" size="mini" @click="logout">退出</button>
      </view>
      <view v-else class="not-login">
        <text>欢迎使用联智考勤</text>
        <button @click="goLogin">立即登录</button>
      </view>
    </view>

    <!-- 快捷统计卡片 -->
    <view v-if="stats" class="stats-row">
      <view class="stat-card">
        <text class="num">{{ stats.attendDays }}</text>
        <text class="label">出勤天数</text>
      </view>
      <view class="stat-card">
        <text class="num">{{ stats.lateCount }}</text>
        <text class="label">迟到次数</text>
      </view>
      <view class="stat-card">
        <text class="num">{{ stats.earlyCount }}</text>
        <text class="label">早退次数</text>
      </view>
      <view class="stat-card">
        <text class="num">{{ stats.totalHours }}</text>
        <text class="label">累计工时(h)</text>
      </view>
    </view>

    <!-- 4 大功能入口 -->
    <view class="entries">
      <view class="entry-card collect" @click="goCollect">
        <view class="icon">📋</view>
        <text class="label">信息采集</text>
      </view>
      <view class="entry-card checkin" @click="goCheckin">
        <view class="icon">⏰</view>
        <text class="label">上班打卡</text>
      </view>
      <view class="entry-card attendance" @click="goHome">
        <view class="icon">📊</view>
        <text class="label">个人考勤</text>
      </view>
      <view class="entry-card ai" @click="goAI">
        <view class="icon">🤖</view>
        <text class="label">AI 问答</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { http } from '@/utils/request'

const accountInfo = ref<any>(null)
const stats = ref<any>(null)

onShow(async () => {
  await refreshAccountInfo()
  if (accountInfo.value) loadStats()
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
  try {
    stats.value = await http.get('/h5/stats', { month })
  } catch {}
}

function goLogin() { uni.navigateTo({ url: '/pages/login/login' }) }
function goCollect() { uni.navigateTo({ url: '/pages/collect/collect' }) }
function goCheckin() { uni.switchTab({ url: '/pages/checkin/checkin' }) }
function goHome() { uni.switchTab({ url: '/pages/home/home' }) }
function goAI() { uni.switchTab({ url: '/pages/ai/ai' }) }

function logout() {
  uni.removeStorageSync('h5Token')
  uni.removeStorageSync('accountInfo')
  uni.reLaunch({ url: '/pages/login/login' })
}
</script>

<style lang="scss" scoped>
.portal {
  padding: 30rpx;

  .header {
    background: linear-gradient(135deg, #2b6cb0, #805ad5);
    border-radius: 24rpx;
    padding: 40rpx 30rpx;
    color: #fff;
    margin-bottom: 30rpx;

    .avatar {
      width: 100rpx; height: 100rpx;
      border-radius: 50%;
      background: rgba(255,255,255,0.3);
      text-align: center; line-height: 100rpx;
      font-size: 44rpx; font-weight: bold;
      margin-right: 20rpx;
    }
    .user-info { display: flex; align-items: center; }
    .info { flex: 1; display: flex; flex-direction: column; }
    .name { font-size: 32rpx; font-weight: bold; }
    .dept { font-size: 24rpx; opacity: 0.8; margin-top: 6rpx; }
    .logout-btn { background: rgba(255,255,255,0.2); color: #fff; font-size: 22rpx; }
  }

  .stats-row {
    display: flex;
    justify-content: space-between;
    margin-bottom: 30rpx;

    .stat-card {
      background: #fff;
      border-radius: 16rpx;
      flex: 1;
      margin: 0 8rpx;
      padding: 24rpx 10rpx;
      text-align: center;
      box-shadow: 0 2rpx 10rpx rgba(0,0,0,0.05);

      .num { display: block; font-size: 36rpx; font-weight: bold; color: #2b6cb0; }
      .label { display: block; font-size: 22rpx; color: #999; margin-top: 6rpx; }
    }
  }

  .entries {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 24rpx;

    .entry-card {
      background: #fff;
      border-radius: 20rpx;
      padding: 50rpx 30rpx;
      text-align: center;
      box-shadow: 0 2rpx 10rpx rgba(0,0,0,0.05);

      .icon { font-size: 80rpx; margin-bottom: 16rpx; }
      .label { font-size: 28rpx; color: #333; font-weight: 500; }

      &.collect { background: linear-gradient(135deg, #e6fffa, #b2f5ea); }
      &.checkin { background: linear-gradient(135deg, #ebf8ff, #bee3f8); }
      &.attendance { background: linear-gradient(135deg, #f0fff4, #c6f6d5); }
      &.ai { background: linear-gradient(135deg, #faf5ff, #e9d8fd); }
    }
  }
}
</style>
