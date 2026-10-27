<template>
  <div class="h5-page">
    <div class="header">
      <button class="back" @click="router.back()">←</button>
      <div class="title">{{ emp.name }} · 考勤日历</div>
    </div>

    <!-- 月份切换 -->
    <div class="month-nav">
      <button @click="changeMonth(-1)">‹</button>
      <div class="month">{{ year }}年{{ month + 1 }}月</div>
      <button @click="changeMonth(1)">›</button>
    </div>

    <!-- 日历网格 -->
    <div class="week-bar">
      <span v-for="w in weeks" :key="w">{{ w }}</span>
    </div>
    <div class="calendar">
      <div v-for="(d, idx) in days" :key="idx" class="day"
           :class="{ 'other-month': !d.current, [d.status]: true, selected: selectedDate === d.date }"
           @click="selectDate(d)">
        {{ d.num }}
        <span v-if="d.dot" class="dot"></span>
      </div>
    </div>

    <!-- 图例 -->
    <div class="legend">
      <span class="lg"><i class="bg-normal"></i>正常</span>
      <span class="lg"><i class="bg-late"></i>迟到</span>
      <span class="lg"><i class="bg-early"></i>早退</span>
      <span class="lg"><i class="bg-absent"></i>缺勤</span>
    </div>

    <!-- 选中日期详情 -->
    <div v-if="selectedRecord || selectedDate" class="card detail">
      <div class="detail-title">{{ selectedDateStr }}</div>
      <template v-if="selectedRecord">
        <div class="detail-row"><span>签到</span><span>{{ selectedRecord.signInTime ? selectedRecord.signInTime.slice(11,16) : '-' }}</span></div>
        <div class="detail-row"><span>签退</span><span>{{ selectedRecord.signOutTime ? selectedRecord.signOutTime.slice(11,16) : '未签退' }}</span></div>
        <div class="detail-row"><span>场所</span><span>{{ selectedRecord.venueName || '-' }}</span></div>
        <div class="detail-row"><span>状态</span>
          <el-tag size="small" :type="tagType">{{ statusText }}</el-tag>
        </div>
        <div v-if="selectedRecord.signInTime && selectedRecord.signOutTime" class="detail-row">
          <span>工时</span><span>{{ calcHours(selectedRecord) }}h</span>
        </div>
      </template>
      <template v-else>
        <div class="empty">当天无打卡记录</div>
      </template>
    </div>

    <!-- 月度汇总 -->
    <div class="card">
      <div class="detail-title">本月汇总</div>
      <div class="summary-row">
        <div class="sum-item"><div class="sum-num">{{ summary.attendDays }}</div><div class="sum-label">出勤</div></div>
        <div class="sum-item"><div class="sum-num">{{ summary.lateCount }}</div><div class="sum-label">迟到</div></div>
        <div class="sum-item"><div class="sum-num">{{ summary.earlyCount }}</div><div class="sum-label">早退</div></div>
        <div class="sum-item"><div class="sum-num">{{ summary.absentCount }}</div><div class="sum-label">缺勤</div></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../../api/request'
import { ElTag } from 'element-plus'

const route = useRoute()
const router = useRouter()
const emp = ref({ id: null, name: '' })
const empId = ref(null)
const year = ref(new Date().getFullYear())
const month = ref(new Date().getMonth())
const records = ref([])
const selectedDate = ref('')
const summary = ref({ attendDays: 0, lateCount: 0, earlyCount: 0, absentCount: 0 })

const weeks = ['日', '一', '二', '三', '四', '五', '六']

onMounted(async () => {
  await identify()
  await loadMonth()
})

async function identify() {
  try {
    const infoRes = await request.get('/h5/auth/info')
    empId.value = infoRes.data?.empId
    emp.value = infoRes.data?.employee || { id: null, name: '' }
    if (!emp.value || !empId.value) {
      router.replace('/h5/login?redirect=/h5/calendar')
      return
    }
  } catch (e) {
    router.replace('/h5/login?redirect=/h5/calendar')
    return
  }
}

async function loadMonth() {
  if (!empId.value) return
  const ym = `${year.value}-${String(month.value+1).padStart(2,'0')}`

  // 1) 月度明细（日历格子 + 选中日详情用）
  try {
    const res = await request.get('/h5/checkin/month', { params: { month: ym } })
    records.value = res.data || []
  } catch (e) {
    records.value = []
  }

  // 2) 月度汇总（本月汇总卡片用）— 与 Attendance.vue 完全同源，保证对齐
  try {
    const stats = await request.get('/h5/stats', { params: { month: ym } })
    const s = stats.data || {}
    summary.value.attendDays = s.attendDays ?? 0
    summary.value.lateCount = s.lateCount ?? 0
    summary.value.earlyCount = s.earlyCount ?? 0
    summary.value.absentCount = s.absentCount ?? 0
  } catch (e) {
    // 保持默认
  }
}

// 日历网格
const days = computed(() => {
  const firstDay = new Date(year.value, month.value, 1)
  const lastDay = new Date(year.value, month.value + 1, 0)
  const firstWeekday = firstDay.getDay()
  const totalDays = lastDay.getDate()
  const arr = []
  // 上月填充
  for (let i = firstWeekday - 1; i >= 0; i--) {
    const d = new Date(year.value, month.value, -i)
    arr.push(makeDay(d, false))
  }
  // 当月
  for (let i = 1; i <= totalDays; i++) {
    arr.push(makeDay(new Date(year.value, month.value, i), true))
  }
  // 下月填充
  const remain = (7 - arr.length % 7) % 7
  for (let i = 1; i <= remain; i++) {
    const d = new Date(year.value, month.value + 1, i)
    arr.push(makeDay(d, false))
  }
  return arr
})

function makeDay(d, current) {
  const dateStr = d.toISOString().slice(0, 10)
  const rec = records.value.find(r => r.signInTime && r.signInTime.slice(0, 10) === dateStr)
  let status = 'other'
  if (current && rec) {
    if (rec.status === 'late') status = 'late'
    else if (rec.status === 'early') status = 'early'
    else status = 'normal'
  } else if (current && d <= new Date() && d.getDay() !== 0 && d.getDay() !== 6) {
    status = 'absent'
  }
  return { num: d.getDate(), date: dateStr, current, status, dot: current && rec }
}

function changeMonth(delta) {
  month.value += delta
  if (month.value < 0) { month.value = 11; year.value-- }
  if (month.value > 11) { month.value = 0; year.value++ }
  loadMonth()
  selectedDate.value = ''
}

function selectDate(d) {
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
  return '正常'
})
const tagType = computed(() => {
  const s = selectedRecord.value?.status
  if (s === 'late') return 'danger'
  if (s === 'early') return 'warning'
  return 'success'
})
// 工时显示：直接用后端算好的 workHours（分钟精度，保留 2 位小数）
function calcHours(r) {
  if (r?.workHours != null) return Number(r.workHours).toFixed(2)
  return '0.00'
}
</script>

<style scoped>
.h5-page { min-height: 100vh; background: #f0f4f8; padding: 16px; padding-bottom: 40px; }
.header { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.back { border: none; background: none; font-size: 22px; color: #2b6cb0; cursor: pointer; }
.title { font-size: 18px; font-weight: bold; color: #1a365d; }

.month-nav { display: flex; justify-content: center; align-items: center; gap: 30px; margin-bottom: 12px; }
.month-nav button { border: none; background: #e2e8f0; width: 36px; height: 36px; border-radius: 50%; font-size: 18px; color: #2d3748; }
.month { font-size: 17px; font-weight: bold; color: #1a365d; }

.week-bar { display: grid; grid-template-columns: repeat(7, 1fr); text-align: center; font-size: 12px; color: #718096; margin-bottom: 6px; }
.calendar { display: grid; grid-template-columns: repeat(7, 1fr); gap: 2px; background: #fff; border-radius: 12px; padding: 10px; box-shadow: 0 2px 6px rgba(0,0,0,0.05); }
.day {
  aspect-ratio: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
  font-size: 15px; color: #2d3748; border-radius: 8px; position: relative; cursor: pointer;
}
.day.other-month { color: #cbd5e0; }
.day.bg-normal { background: #c6f6d5; color: #22543d; }
.day.bg-late { background: #fed7d7; color: #742a2a; }
.day.bg-early { background: #feebc8; color: #7b341e; }
.day.bg-absent { background: #e2e8f0; color: #4a5568; }
.day.selected { outline: 2px solid #2b6cb0; }
.dot { position: absolute; bottom: 4px; width: 5px; height: 5px; background: #2b6cb0; border-radius: 50%; }

.legend { display: flex; justify-content: center; gap: 12px; margin-top: 12px; font-size: 12px; color: #4a5568; }
.lg { display: flex; align-items: center; gap: 4px; }
.lg i { width: 14px; height: 14px; border-radius: 4px; display: inline-block; }
.bg-normal { background: #c6f6d5; }
.bg-late { background: #fed7d7; }
.bg-early { background: #feebc8; }
.bg-absent { background: #e2e8f0; }

.card { background: #fff; border-radius: 12px; padding: 16px; margin-top: 16px; box-shadow: 0 2px 6px rgba(0,0,0,0.05); }
.detail-title { font-size: 15px; font-weight: bold; color: #1a365d; margin-bottom: 10px; }
.detail-row { display: flex; justify-content: space-between; padding: 6px 0; font-size: 14px; color: #2d3748; }
.detail-row span:first-child { color: #718096; }
.empty { text-align: center; color: #a0aec0; padding: 10px 0; font-size: 13px; }

.summary-row { display: flex; justify-content: space-around; }
.sum-item { text-align: center; }
.sum-num { font-size: 22px; font-weight: bold; color: #2b6cb0; }
.sum-label { font-size: 12px; color: #718096; margin-top: 2px; }
</style>
