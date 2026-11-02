<template>
  <view class="page">
    <view class="header">
      <text class="title">个人考勤</text>
      <text class="subtitle">查看考勤、发起申请、跟进审批</text>
    </view>

    <template v-if="emp">
      <view class="card overview">
        <text class="greet">你好，{{ emp.name }} 👋</text>
        <text class="greet-sub">{{ emp.dept }} · {{ emp.position }}</text>
        <view class="stat-row">
          <view class="stat-item">
            <text class="stat-num">{{ summary.attendDays }}</text>
            <text class="stat-label">出勤天数</text>
          </view>
          <view class="stat-item">
            <text class="stat-num warn">{{ summary.lateCount }}</text>
            <text class="stat-label">迟到次数</text>
          </view>
          <view class="stat-item">
            <text class="stat-num warn2">{{ summary.earlyCount }}</text>
            <text class="stat-label">早退次数</text>
          </view>
          <view class="stat-item">
            <text class="stat-num">{{ summary.totalHours }}h</text>
            <text class="stat-label">累计工时</text>
          </view>
        </view>
      </view>

      <view class="menu-grid">
        <view class="menu-btn" @click="navigate('/pages/checkin/checkin', true)">
          <text class="icon">📍</text>
          <text class="label">去打卡</text>
        </view>
        <view class="menu-btn" @click="navigate('/pages/apply/apply')">
          <text class="icon">📝</text>
          <text class="label">发起申请</text>
        </view>
        <view class="menu-btn" @click="navigate('/pages/approvals/my')">
          <text class="icon">📋</text>
          <text class="label">我的申请</text>
          <view v-if="summary.pendingCount > 0" class="badge">{{ summary.pendingCount }}</view>
        </view>

        <view class="menu-btn" @click="navigate('/pages/calendar/calendar')">
          <text class="icon">📅</text>
          <text class="label">考勤日历</text>
        </view>
      </view>

      <view class="card shortcut">
        <text class="shortcut-title">快捷操作</text>
        <view class="shortcut-row">
          <view class="shortcut-btn" @click="navigate('/pages/checkin/checkin', true)">📍 去打卡</view>
          <view class="shortcut-btn" @click="navigate('/pages/apply/apply')">🔧 补卡申请</view>
        </view>
      </view>

      <view class="card">
        <text class="shortcut-title">本月打卡记录</text>
        <view v-if="records.length === 0" class="empty">暂无打卡记录</view>
        <view v-for="r in records" :key="r.id" class="record-item">
          <view class="record-date">
            <text class="day">{{ formatDay(r.checkTime) }}</text>
            <text class="date">{{ formatDate(r.checkTime) }}</text>
          </view>
          <view class="record-info">
            <text class="time">{{ formatTime(r.checkTime) }}</text>
            <text class="status" :class="r.status">{{ r.statusText }}</text>
          </view>
        </view>
      </view>
    </template>

    <view v-else class="card">
      <text class="no-emp">未绑定员工信息，请先完成采集</text>
      <button class="btn-primary" @click="navigate('/pages/collect/collect', true)">去采集</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { http } from '@/utils/request'

const emp = ref<any>(null)
const summary = reactive({ attendDays: 0, lateCount: 0, earlyCount: 0, totalHours: 0, pendingCount: 0, todoCount: 0 })
const records = ref<any[]>([])

onShow(async () => {
  try {
    const info = await http.get<any>('/h5/auth/info', undefined, { loading: false })
    emp.value = info?.employee || null
    if (!emp.value) return
  } catch {
    emp.value = null
    return
  }
  await loadSummary()
  await loadRecords()
})

async function loadSummary() {
  try {
    const s = await http.get<any>('/h5/stats', undefined, { loading: false })
    summary.attendDays = s?.attendDays ?? 0
    summary.lateCount = s?.lateCount ?? 0
    summary.earlyCount = s?.earlyCount ?? 0
    summary.totalHours = s?.totalHours ?? 0
  } catch {}

  try {
    const r = await http.get<any>('/h5/approval/my?current=1&size=1&status=pending', undefined, { loading: false })
    summary.pendingCount = r?.total || 0
  } catch {}

  try {
    const r = await http.get<any>('/h5/approval/todo?current=1&size=1', undefined, { loading: false })
    summary.todoCount = r?.total || 0
  } catch {}
}

async function loadRecords() {
  try {
    const now = new Date()
    const month = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
    const list = await http.get<any[]>('/h5/checkin/month', { month }, { loading: false })
    records.value = (list || []).sort((a, b) => b.checkTime.localeCompare(a.checkTime)).slice(0, 20)
  } catch {}
}

function navigate(url: string, switchTab = false) {
  if (switchTab) {
    uni.switchTab({ url, fail: () => uni.navigateTo({ url }) })
  } else {
    uni.navigateTo({ url, fail: () => uni.showToast({ title: '页面不存在', icon: 'none' }) })
  }
}

function formatDate(t: string) { return t ? t.substring(0, 10) : '' }
function formatDay(t: string) { return t ? new Date(t).getDate() : '' }
function formatTime(t: string) { return t ? t.substring(11, 16) : '' }
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; background: #f0f4f8; padding: 40rpx 32rpx; padding-bottom: 80rpx; }
.header { margin-bottom: 32rpx; }
.title { font-size: 44rpx; font-weight: bold; color: #1a365d; display: block; }
.subtitle { font-size: 26rpx; color: #718096; margin-top: 8rpx; display: block; }
.card { background: #fff; border-radius: 24rpx; padding: 40rpx; margin-bottom: 32rpx; box-shadow: 0 4rpx 12rpx rgba(0,0,0,0.05); }
.overview { background: linear-gradient(135deg, #1a365d, #2b6cb0); color: #fff; }
.greet { font-size: 40rpx; font-weight: bold; display: block; }
.greet-sub { font-size: 26rpx; opacity: 0.85; margin-top: 8rpx; margin-bottom: 32rpx; display: block; }
.stat-row { display: flex; justify-content: space-around; }
.stat-item { text-align: center; }
.stat-num { font-size: 48rpx; font-weight: bold; display: block; }
.stat-num.warn { color: #fbd38d; }
.stat-num.warn2 { color: #fed7d7; }
.stat-label { font-size: 24rpx; opacity: 0.85; margin-top: 8rpx; display: block; }
.menu-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24rpx; margin-bottom: 32rpx; }
.menu-btn { background: #fff; border-radius: 24rpx; padding: 48rpx; display: flex; flex-direction: column; align-items: center; gap: 12rpx; box-shadow: 0 4rpx 12rpx rgba(0,0,0,0.05); position: relative; }
.menu-btn .icon { font-size: 56rpx; }
.menu-btn .label { font-size: 28rpx; color: #2d3748; font-weight: 500; }
.badge { position: absolute; top: 16rpx; right: 20rpx; background: #e53e3e; color: #fff; font-size: 22rpx; padding: 4rpx 14rpx; border-radius: 20rpx; min-width: 36rpx; text-align: center; }
.shortcut .shortcut-title { font-size: 28rpx; color: #4a5568; margin-bottom: 20rpx; font-weight: 500; display: block; }
.shortcut-row { display: flex; gap: 20rpx; }
.shortcut-btn { flex: 1; padding: 20rpx; border: 2rpx solid #e2e8f0; border-radius: 16rpx; background: #f7fafc; font-size: 26rpx; color: #2d3748; text-align: center; }
.shortcut-title { font-size: 28rpx; color: #4a5568; margin-bottom: 20rpx; font-weight: 500; display: block; }
.record-item { display: flex; align-items: center; padding: 20rpx 0; border-bottom: 2rpx solid #edf2f7; }
.record-item:last-child { border-bottom: none; }
.record-date { width: 120rpx; }
.record-date .day { font-size: 36rpx; font-weight: bold; color: #2b6cb0; display: block; }
.record-date .date { font-size: 22rpx; color: #a0aec0; display: block; }
.record-info { flex: 1; display: flex; justify-content: space-between; align-items: center; }
.record-info .time { font-size: 30rpx; color: #2d3748; }
.record-info .status { font-size: 24rpx; padding: 4rpx 16rpx; border-radius: 16rpx; }
.status.normal { background: #c6f6d5; color: #22543d; }
.status.late { background: #fed7d7; color: #742a2a; }
.status.early { background: #feebc8; color: #7b341e; }
.status.lateEarly { background: #fed7d7; color: #742a2a; }
.status.leave { background: #bee3f8; color: #2a4365; }
.status.absent { background: #e2e8f0; color: #4a5568; }
.empty { text-align: center; color: #a0aec0; padding: 40rpx 0; font-size: 28rpx; }
.no-emp { display: block; text-align: center; color: #4a5568; margin-bottom: 24rpx; font-size: 28rpx; }
.btn-primary { width: 100%; padding: 28rpx; border-radius: 16rpx; background: #2b6cb0; color: #fff; font-size: 30rpx; font-weight: bold; border: none; }
</style>
