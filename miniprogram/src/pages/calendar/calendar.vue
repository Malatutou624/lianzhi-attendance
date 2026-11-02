<template>
  <view class="page">
    <!-- 月份切换 -->
    <view class="month-nav">
      <view class="nav-btn" @tap="changeMonth(-1)">‹</view>
      <text class="month-text">{{ year }}年{{ month + 1 }}月</text>
      <view class="nav-btn" @tap="changeMonth(1)">›</view>
    </view>

    <!-- 日历网格 -->
    <view class="week-bar">
      <text v-for="w in weeks" :key="w" class="week-day">{{ w }}</text>
    </view>
    <view class="calendar">
      <view v-for="(d, idx) in days" :key="idx" class="day"
            :class="[
              d.current ? '' : 'other-month',
              'bg-' + d.status,
              selectedDate === d.date ? 'selected' : ''
            ]"
            @tap="selectDate(d)">
        <text class="day-num">{{ d.num }}</text>
        <text v-if="d.dot" class="dot"></text>
      </view>
    </view>

    <!-- 图例 -->
    <view class="legend">
      <text class="lg"><text class="bg-normal"></text>正常</text>
      <text class="lg"><text class="bg-late"></text>迟到</text>
      <text class="lg"><text class="bg-early"></text>早退</text>
      <text class="lg"><text class="bg-absent"></text>缺勤</text>
    </view>

    <!-- 选中日期详情 -->
    <view v-if="selectedDate" class="card detail">
      <text class="detail-title">{{ selectedDateStr }}</text>
      <template v-if="selectedRecord">
        <view class="detail-row"><text class="label">签到</text><text class="val">{{ selectedRecord.signInTime ? formatTime(selectedRecord.signInTime) : '-' }}</text></view>
        <view class="detail-row"><text class="label">签退</text><text class="val">{{ selectedRecord.signOutTime ? formatTime(selectedRecord.signOutTime) : '未签退' }}</text></view>
        <view class="detail-row"><text class="label">场所</text><text class="val">{{ selectedRecord.venueName || '-' }}</text></view>
        <view class="detail-row"><text class="label">状态</text>
          <text class="status-tag" :class="'tag-' + selectedRecord.status">{{ statusText }}</text>
        </view>
        <view v-if="selectedRecord.signInTime && selectedRecord.signOutTime" class="detail-row">
          <text class="label">工时</text><text class="val">{{ calcHours(selectedRecord) }}h</text>
        </view>
      </template>
      <view v-else class="empty">当天无打卡记录</view>
    </view>

    <!-- 月度汇总 -->
    <view class="card">
      <text class="detail-title">本月汇总</text>
      <view class="summary-row">
        <view class="sum-item">
          <text class="sum-num">{{ summary.attendDays }}</text>
          <text class="sum-label">出勤</text>
        </view>
        <view class="sum-item">
          <text class="sum-num">{{ summary.lateCount }}</text>
          <text class="sum-label">迟到</text>
        </view>
        <view class="sum-item">
          <text class="sum-num">{{ summary.earlyCount }}</text>
          <text class="sum-label">早退</text>
        </view>
        <view class="sum-item">
          <text class="sum-num">{{ summary.absentCount }}</text>
          <text class="sum-label">缺勤</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { http } from '@/utils/request'

const year = ref(new Date().getFullYear())
const month = ref(new Date().getMonth())
const records = ref<any[]>([])
const selectedDate = ref('')
const summary = reactive({ attendDays: 0, lateCount: 0, earlyCount: 0, absentCount: 0 })
const weeks = ['日', '一', '二', '三', '四', '五', '六']

onMounted(async () => {
  await loadMonth()
})

async function loadMonth() {
  const ym = `${year.value}-${String(month.value + 1).padStart(2, '0')}`

  try {
    const list = await http.get<any[]>('/h5/checkin/month', { month: ym }, { loading: false })
    records.value = list || []
  } catch {
    records.value = []
  }

  try {
    const s = await http.get<any>('/h5/stats', { month: ym }, { loading: false })
    summary.attendDays = s?.attendDays ?? 0
    summary.lateCount = s?.lateCount ?? 0
    summary.earlyCount = s?.earlyCount ?? 0
    summary.absentCount = s?.absentCount ?? 0
  } catch {}
}

const days = computed(() => {
  const firstDay = new Date(year.value, month.value, 1)
  const lastDay = new Date(year.value, month.value + 1, 0)
  const firstWeekday = firstDay.getDay()
  const totalDays = lastDay.getDate()
  const arr: any[] = []
  for (let i = firstWeekday - 1; i >= 0; i--) {
    const d = new Date(year.value, month.value, -i)
    arr.push(makeDay(d, false))
  }
  for (let i = 1; i <= totalDays; i++) {
    arr.push(makeDay(new Date(year.value, month.value, i), true))
  }
  const remain = (7 - arr.length % 7) % 7
  for (let i = 1; i <= remain; i++) {
    const d = new Date(year.value, month.value + 1, i)
    arr.push(makeDay(d, false))
  }
  return arr
})

function makeDay(d: Date, current: boolean) {
  const dateStr = d.toISOString().slice(0, 10)
  const rec = records.value.find(r => r.signInTime && r.signInTime.slice(0, 10) === dateStr)
  let status = 'other'
  if (current && rec) {
    if (rec.status === 'late') status = 'late'
    else if (rec.status === 'early') status = 'early'
    else if (rec.status === 'lateEarly') status = 'early'   // 既迟到又早退 → 日历显示早退
    else if (rec.status === 'leave') status = 'normal'      // 请假（日历用绿色）
    else if (rec.status === 'absent') status = 'absent'
    else status = 'normal'
  } else if (current && d <= new Date() && d.getDay() !== 0 && d.getDay() !== 6) {
    status = 'absent'
  }
  return { num: d.getDate(), date: dateStr, current, status, dot: current && rec }
}

function changeMonth(delta: number) {
  month.value += delta
  if (month.value < 0) { month.value = 11; year.value-- }
  if (month.value > 11) { month.value = 0; year.value++ }
  loadMonth()
  selectedDate.value = ''
}

function selectDate(d: any) {
  if (!d.current) return
  selectedDate.value = d.date
}

const selectedRecord = computed(() => {
  return records.value.find(r => r.signInTime && r.signInTime.slice(0, 10) === selectedDate.value)
})
const selectedDateStr = computed(() => selectedDate.value || '')
const statusText = computed(() => {
  const s = selectedRecord.value?.status
  if (s === 'late') return '迟到'
  if (s === 'early') return '早退'
  if (s === 'lateEarly') return '迟到且早退'
  if (s === 'leave') return '请假'
  if (s === 'absent') return '旷工'
  return '正常'
})

function formatTime(t: string) { return t ? t.substring(11, 16) : '' }
function calcHours(r: any) {
  if (r?.workHours != null) return Number(r.workHours).toFixed(2)
  return '0.00'
}
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; background: #f0f4f8; padding: 24rpx; padding-bottom: 80rpx; }

.month-nav {
  display: flex; justify-content: center; align-items: center; gap: 60rpx; margin-bottom: 24rpx;
}
.nav-btn {
  width: 72rpx; height: 72rpx; border-radius: 50%; background: #e2e8f0;
  display: flex; align-items: center; justify-content: center;
  font-size: 36rpx; color: #2d3748; font-weight: bold;
}
.month-text { font-size: 34rpx; font-weight: bold; color: #1a365d; }

.week-bar {
  display: grid; grid-template-columns: repeat(7, 1fr);
  text-align: center; font-size: 24rpx; color: #718096; margin-bottom: 12rpx;
}
.week-day { text-align: center; }

.calendar {
  display: grid; grid-template-columns: repeat(7, 1fr);
  background: #fff; border-radius: 24rpx; padding: 20rpx; gap: 4rpx;
  box-shadow: 0 4rpx 12rpx rgba(0,0,0,0.05);
}
.day {
  aspect-ratio: 1;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  border-radius: 16rpx; position: relative; padding: 8rpx 0;
}
.day-num { font-size: 28rpx; color: #2d3748; }
.day.other-month .day-num { color: #cbd5e0; }
.day.bg-normal { background: #c6f6d5; }
.day.bg-normal .day-num { color: #22543d; }
.day.bg-late { background: #fed7d7; }
.day.bg-late .day-num { color: #742a2a; }
.day.bg-early { background: #feebc8; }
.day.bg-early .day-num { color: #7b341e; }
.day.bg-absent { background: #e2e8f0; }
.day.bg-absent .day-num { color: #4a5568; }
.day.selected { outline: 4rpx solid #2b6cb0; }
.dot {
  position: absolute; bottom: 6rpx; width: 10rpx; height: 10rpx;
  background: #2b6cb0; border-radius: 50%;
}

.legend {
  display: flex; justify-content: center; gap: 24rpx; margin-top: 24rpx;
  font-size: 22rpx; color: #4a5568;
}
.lg { display: flex; align-items: center; gap: 8rpx; }
.lg text { width: 24rpx; height: 24rpx; border-radius: 8rpx; display: inline-block; }
.bg-normal { background: #c6f6d5; }
.bg-late { background: #fed7d7; }
.bg-early { background: #feebc8; }
.bg-absent { background: #e2e8f0; }

.card {
  background: #fff; border-radius: 24rpx; padding: 32rpx; margin-top: 24rpx;
  box-shadow: 0 4rpx 12rpx rgba(0,0,0,0.05);
}
.detail-title { font-size: 30rpx; font-weight: bold; color: #1a365d; margin-bottom: 16rpx; display: block; }
.detail-row { display: flex; justify-content: space-between; padding: 12rpx 0; font-size: 28rpx; color: #2d3748; }
.detail-row .label { color: #718096; }
.detail-row .val { color: #2d3748; font-weight: 500; }
.status-tag { font-size: 22rpx; padding: 4rpx 16rpx; border-radius: 16rpx; }
.tag-normal { background: #c6f6d5; color: #22543d; }
.tag-late { background: #fed7d7; color: #742a2a; }
.tag-early { background: #feebc8; color: #7b341e; }
.tag-lateEarly { background: #fed7d7; color: #742a2a; }
.tag-leave { background: #bee3f8; color: #2a4365; }
.tag-absent { background: #e2e8f0; color: #4a5568; }
.empty { text-align: center; color: #a0aec0; padding: 20rpx 0; font-size: 26rpx; }

.summary-row { display: flex; justify-content: space-around; }
.sum-item { text-align: center; }
.sum-num { font-size: 44rpx; font-weight: bold; color: #2b6cb0; display: block; }
.sum-label { font-size: 24rpx; color: #718096; margin-top: 4rpx; display: block; }
</style>
