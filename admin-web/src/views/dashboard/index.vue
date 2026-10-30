<template>
  <div>
    <!-- 全局场所筛选：默认所有场所 -->
    <div class="filter-bar">
      <span class="filter-label">场所：</span>
      <el-select v-model="venueId" style="width: 200px" @change="loadAll">
        <el-option label="所有场所" :value="null" />
        <el-option v-for="v in venues" :key="v.id" :label="v.name" :value="v.id" />
      </el-select>
    </div>

    <!-- 今日概览卡片 -->
    <el-row :gutter="16">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <el-card shadow="hover" class="stat-card" @click="showPeople(card.type)">
          <div class="card-num">{{ card.value }}</div>
          <div class="card-label">{{ card.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区：打卡趋势 + 今日打卡状态分布 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>打卡趋势</span>
              <el-radio-group v-model="trendType" size="small" @change="loadTrend">
                <el-radio-button value="day">日</el-radio-button>
                <el-radio-button value="week">周</el-radio-button>
                <el-radio-button value="month">月</el-radio-button>
                <el-radio-button value="year">年</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="trendChart" style="height: 320px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header><span>今日打卡状态分布</span></template>
          <div ref="pieChart" style="height: 320px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 部门工时报告：按日期范围筛选 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>部门工作时长报告</span>
              <el-date-picker v-model="workDateRange" type="daterange" range-separator="至"
                start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD"
                style="width: 260px" @change="loadWorkHours" />
            </div>
          </template>
          <el-table :data="workHoursList" border size="small" max-height="300">
            <el-table-column prop="dept" label="部门" />
            <el-table-column prop="empCount" label="打卡人数" width="90" />
            <el-table-column prop="totalHours" label="总工时（小时）" width="120">
              <template #default="{ row }">{{ row.totalHours.toFixed(2) }}h</template>
            </el-table-column>
            <el-table-column prop="overtimeHours" label="加班时长（小时）" width="130">
              <template #default="{ row }">{{ row.overtimeHours.toFixed(2) }}h</template>
            </el-table-column>
            <el-table-column prop="avgHours" label="人均工时（小时）" width="130">
              <template #default="{ row }">{{ row.avgHours.toFixed(2) }}h</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 人员列表弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px" top="8vh">
      <el-table :data="peopleList" border size="small" max-height="400">
        <!-- 工时排名 -->
        <template v-if="dialogType === 'workHoursRank'">
          <el-table-column prop="rank" label="排名" width="60" />
          <el-table-column prop="empNo" label="工号" width="100" />
          <el-table-column prop="name" label="姓名" width="90" />
          <el-table-column prop="dept" label="部门" />
          <el-table-column prop="hours" label="今日工时（小时）" width="120">
            <template #default="{ row }">{{ row.hours }}h</template>
          </el-table-column>
          <el-table-column prop="checkinTime" label="签到时间" width="150" />
        </template>
        <!-- 其它卡片 -->
        <template v-else>
          <el-table-column prop="empNo" label="工号" width="90" />
          <el-table-column prop="name" label="姓名" width="80" />
          <el-table-column prop="dept" label="部门" width="100" />
          <el-table-column prop="position" label="职位" width="100" />
          <el-table-column v-if="dialogType === 'active'" prop="venueName" label="所属场所" />
          <el-table-column v-if="dialogType === 'notAttended' || dialogType === 'abnormal'" label="异常状态" width="110">
            <template #default="{ row }">
              <el-tag :type="STATUS_TAG[row.status] || 'info'" size="small">{{ row.statusText }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="dialogType === 'abnormal'" prop="checkinTime" label="签到时间" width="150">
            <template #default="{ row }">{{ row.checkinTime || '-' }}</template>
          </el-table-column>
        </template>
      </el-table>
      <div class="people-count">
        <el-button v-if="dialogType === 'notAttended' || dialogType === 'abnormal'"
          type="primary" size="small" @click="exportPeople">导出Excel</el-button>
        共 {{ peopleList.length }} 人
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import request from '../../api/request'
import { download } from '../../api/download'

// 场所筛选（默认所有场所）
const venues = ref([])
const venueId = ref(null)

const cards = ref([
  { label: '在职员工', value: '-', type: 'active' },
  { label: '出勤率', value: '-', type: 'notAttended' },
  { label: '平均有效工时', value: '-', type: 'workHoursRank' },
  { label: '异常率', value: '-', type: 'abnormal' }
])

// 弹窗标题与导出
const DIALOG_TITLE = {
  active: '在职人员',
  notAttended: '未出勤人员（未请假且未打卡）',
  workHoursRank: '员工工时排名（实时）',
  abnormal: '出勤异常人员'
}
// 异常状态标签颜色
const STATUS_TAG = {
  normal: 'success', late: 'danger', early: 'warning', lateEarly: 'danger',
  leave: 'info', absent: 'danger', other: 'info'
}
const trendChart = ref(null)
const pieChart = ref(null)
let trend = null, pie = null

// 打卡趋势：日/周/月/年
const trendType = ref('day')
const TREND_NAME = { day: '近7天打卡趋势', week: '近4周打卡趋势', month: '近6个月打卡趋势', year: '近3年打卡趋势' }

// 弹窗相关
const dialogVisible = ref(false)
const dialogTitle = ref('')
const dialogType = ref('')
const peopleList = ref([])

// 部门工时报告（日期范围，默认本月1号至今天）
const now = new Date()
const pad = (n) => String(n).padStart(2, '0')
const monthStart = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-01`
const today = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
const workDateRange = ref([monthStart, today])
const workHoursList = ref([])

function venueParam() {
  return venueId.value != null ? { venueId: venueId.value } : {}
}

async function showPeople(type) {
  dialogType.value = type
  dialogTitle.value = DIALOG_TITLE[type] || '人员列表'
  try {
    const res = await request.get('/stats/people', { params: { type, ...venueParam() } })
    peopleList.value = res.data
  } catch (e) {
    peopleList.value = []
  }
  dialogVisible.value = true
}

// 导出未出勤/出勤异常明细
function exportPeople() {
  download('/stats/people/export', { type: dialogType.value, ...venueParam() },
    DIALOG_TITLE[dialogType.value] + '.xlsx')
}

onMounted(async () => {
  const res = await request.get('/venue/list')
  venues.value = res.data
  loadAll()
})

// 场所切换时全量刷新
async function loadAll() {
  await loadToday()
  await nextTick()
  loadTrend()
  loadStatusPie()
  loadWorkHours()
}

async function loadToday() {
  const res = await request.get('/stats/today', { params: venueParam() })
  const d = res.data
  cards.value = [
    { label: '在职员工', value: d.empTotal, type: 'active' },
    { label: '出勤率', value: d.attendanceRate + '%', type: 'notAttended' },
    { label: '平均有效工时', value: d.avgWorkHours + 'h', type: 'workHoursRank' },
    { label: '异常率', value: d.abnormalRate + '%', type: 'abnormal' }
  ]
}

// 打卡趋势（日/周/月/年）
async function loadTrend() {
  const res = await request.get('/stats/trend', { params: { type: trendType.value, ...venueParam() } })
  await nextTick()
  if (!trend) trend = echarts.init(trendChart.value)
  trend.setOption({
    title: { text: TREND_NAME[trendType.value], textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 30, bottom: 50, top: 50 },
    xAxis: { type: 'category', data: res.data.xAxis, axisLabel: { rotate: res.data.xAxis.length > 7 ? 30 : 0 } },
    yAxis: { type: 'value', name: '打卡人次', minInterval: 1 },
    series: [{ type: 'line', smooth: true, data: res.data.data, areaStyle: { opacity: 0.15 }, itemStyle: { color: '#2b6cb0' } }]
  }, true)
}

// 今日打卡状态分布（正常/迟到/早退/未打卡，真实数据）
async function loadStatusPie() {
  const res = await request.get('/stats/todayStatus', { params: venueParam() })
  await nextTick()
  if (!pie) pie = echarts.init(pieChart.value)
  pie.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 人次' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: ['35%', '60%'],
      data: res.data,
      label: { formatter: '{b}: {c}' },
      itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 }
    }],
    color: ['#48bb78', '#e53e3e', '#dd6b20', '#9b2c2c', '#4299e1', '#a0aec0', '#cbd5e0']
  }, true)
}

// 部门工时（日期范围）
async function loadWorkHours() {
  if (!workDateRange.value?.length) return
  try {
    const res = await request.get('/stats/workHours', {
      params: { start: workDateRange.value[0], end: workDateRange.value[1], ...venueParam() }
    })
    workHoursList.value = res.data
  } catch (e) {
    workHoursList.value = []
  }
}

onBeforeUnmount(() => { trend && trend.dispose(); pie && pie.dispose() })
</script>

<style scoped>
.filter-bar { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
.filter-label { color: #4a5568; font-size: 14px; }
.stat-card { cursor: pointer; transition: transform 0.2s; }
.stat-card:hover { transform: translateY(-2px); }
.card-num { font-size: 32px; font-weight: bold; color: #2b6cb0; text-align: center; }
.card-label { text-align: center; color: #718096; margin-top: 8px; }
.people-count { margin-top: 12px; display: flex; justify-content: space-between; align-items: center; color: #718096; font-size: 13px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
