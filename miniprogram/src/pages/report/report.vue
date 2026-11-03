<template>
  <view class="report-page">
    <!-- 月份选择 -->
    <view class="month-bar">
      <text class="label">选择月份：</text>
      <picker mode="date" :value="selectedMonth" :start="'2024-01'" :end="'2030-12'" @change="onMonthChange">
        <view class="picker">{{ selectedMonth }} ▾</view>
      </picker>
    </view>

    <view v-if="report" class="report">
      <!-- 头部 -->
      <view class="header">
        <text class="month-title">{{ report.reportMonth }}</text>
        <text class="sub">{{ report.empName || '我' }} · 考勤月报</text>
      </view>

      <!-- 四宫格统计 -->
      <view class="stats-grid">
        <view class="stat green">
          <text class="num">{{ report.workDays || 0 }}</text>
          <text class="label">出勤天数</text>
        </view>
        <view class="stat red">
          <text class="num">{{ report.absentCount || 0 }}</text>
          <text class="label">旷工天数</text>
        </view>
        <view class="stat orange">
          <text class="num">{{ report.lateCount || 0 }}</text>
          <text class="label">迟到次数</text>
        </view>
        <view class="stat yellow">
          <text class="num">{{ report.earlyCount || 0 }}</text>
          <text class="label">早退次数</text>
        </view>
      </view>

      <!-- 详细卡片 -->
      <view class="detail-card">
        <view class="detail-title">📋 详细数据</view>
        <view class="detail-row">
          <text class="key">请假天数</text>
          <text class="val">{{ report.leaveCount || 0 }} 天</text>
        </view>
        <view class="detail-row">
          <text class="key">加班小时</text>
          <text class="val">{{ report.overtimeHours || 0 }} h</text>
        </view>
        <view class="detail-row">
          <text class="key">累计工时</text>
          <text class="val highlight">{{ report.totalHours || 0 }} h</text>
        </view>
      </view>

      <!-- 所在场所 -->
      <view v-if="report.venueName" class="detail-card venue-card">
        <text class="detail-title">🏢 所在场所</text>
        <text class="venue-name">{{ report.venueName }}</text>
      </view>

      <!-- 评级 -->
      <view class="rating" :class="ratingLevel">
        <text class="rating-icon">{{ ratingIcon }}</text>
        <text class="rating-text">{{ ratingText }}</text>
      </view>
    </view>

    <!-- 无数据 -->
    <view v-else class="empty">
      <text class="empty-icon">📈</text>
      <text class="empty-text">{{ noDataTip }}</text>
      <text class="empty-hint">{{ noDataHint }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { http } from '@/utils/request'

const report = ref<any>(null)
const selectedMonth = ref('')

onShow(() => {
  // 默认选上月
  const now = new Date()
  now.setMonth(now.getMonth() - 1)
  selectedMonth.value = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  load()
})

async function load() {
  try {
    report.value = await http.get('/h5/report/my', { month: selectedMonth.value }, { loading: false })
  } catch {
    report.value = null
  }
}

function onMonthChange(e: any) {
  // picker mode="date" 返回 "2026-11"
  selectedMonth.value = e.detail.value
  load()
}

const noDataTip = computed(() => `${selectedMonth.value} 暂无月报`)
const noDataHint = computed(() => {
  const now = new Date()
  const monthBefore = now.getMonth()
  if (monthBefore === 11) return '每年 1 号凌晨自动生成上月月报，请耐心等待'
  return '每月 1 号凌晨由系统自动生成，或稍后再试'
})

// 简易评级
const ratingLevel = computed(() => {
  if (!report.value) return ''
  const r = report.value
  const issues = (r.lateCount || 0) + (r.earlyCount || 0) + (r.absentCount || 0) * 3
  if (issues === 0) return 'excellent'
  if (issues <= 2) return 'good'
  if (issues <= 5) return 'fair'
  return 'needs-attention'
})

const ratingIcon = computed(() => {
  switch (ratingLevel.value) {
    case 'excellent': return '🌟'
    case 'good': return '👍'
    case 'fair': return '😊'
    default: return '💪'
  }
})

const ratingText = computed(() => {
  switch (ratingLevel.value) {
    case 'excellent': return '全勤模范，继续保持！'
    case 'good': return '出勤良好，再接再厉'
    case 'fair': return '略有瑕疵，注意守时'
    default: return '需要加油啦 💪'
  }
})
</script>

<style lang="scss" scoped>
.report-page {
  padding: 20rpx;
  min-height: 100vh;
  background: #f5f7fa;

  .month-bar {
    display: flex; align-items: center;
    padding: 20rpx 24rpx;
    background: #fff;
    border-radius: 16rpx;
    margin-bottom: 20rpx;

    .label { font-size: 26rpx; color: #718096; margin-right: 12rpx; }
    .picker {
      padding: 12rpx 24rpx;
      background: #ebf8ff;
      border-radius: 8rpx;
      font-size: 26rpx;
      color: #2b6cb0;
      font-weight: 500;
    }
  }

  .report {
    display: flex; flex-direction: column; gap: 20rpx;
  }

  .header {
    background: linear-gradient(135deg, #2b6cb0, #805ad5);
    border-radius: 20rpx;
    padding: 40rpx 30rpx;
    color: #fff;

    .month-title {
      font-size: 48rpx; font-weight: bold;
      display: block; margin-bottom: 8rpx;
    }
    .sub { font-size: 26rpx; opacity: 0.85; }
  }

  .stats-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16rpx;

    .stat {
      background: #fff;
      border-radius: 16rpx;
      padding: 28rpx 20rpx;
      text-align: center;

      .num { display: block; font-size: 44rpx; font-weight: bold; }
      .label { display: block; font-size: 22rpx; color: #718096; margin-top: 6rpx; }

      &.green .num { color: #48bb78; }
      &.red .num { color: #f56565; }
      &.orange .num { color: #ed8936; }
      &.yellow .num { color: #ecc94b; }
    }
  }

  .detail-card {
    background: #fff;
    border-radius: 16rpx;
    padding: 28rpx 24rpx;

    .detail-title {
      font-size: 26rpx; color: #4a5568;
      font-weight: 500;
      margin-bottom: 16rpx;
      padding-bottom: 12rpx;
      border-bottom: 1rpx solid #e2e8f0;
    }

    .detail-row {
      display: flex; justify-content: space-between; align-items: center;
      padding: 12rpx 0;

      .key { font-size: 26rpx; color: #718096; }
      .val { font-size: 26rpx; color: #2d3748; font-weight: 500; }
      .val.highlight { color: #2b6cb0; font-size: 30rpx; }
    }

    &.venue-card { text-align: center; }
    .venue-name { font-size: 28rpx; color: #2d3748; font-weight: 500; }
  }

  .rating {
    background: #fff;
    border-radius: 16rpx;
    padding: 40rpx;
    text-align: center;

    .rating-icon { font-size: 80rpx; display: block; margin-bottom: 12rpx; }
    .rating-text { font-size: 28rpx; color: #4a5568; }

    &.excellent { background: linear-gradient(135deg, #f0fff4, #c6f6d5); }
    &.good { background: linear-gradient(135deg, #ebf8ff, #bee3f8); }
    &.fair { background: linear-gradient(135deg, #fffaf0, #feebc8); }
    &.needs-attention { background: linear-gradient(135deg, #fff5f5, #fed7d7); }
  }

  .empty {
    display: flex; flex-direction: column; align-items: center;
    padding-top: 200rpx;

    .empty-icon { font-size: 120rpx; margin-bottom: 24rpx; }
    .empty-text { font-size: 30rpx; color: #4a5568; margin-bottom: 12rpx; font-weight: 500; }
    .empty-hint { font-size: 24rpx; color: #a0aec0; }
  }
}
</style>
