<template>
  <div>
    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-select v-model="venueId" placeholder="请选择场所" style="width: 180px">
        <el-option v-for="v in venues" :key="v.id" :label="v.name" :value="v.id" />
      </el-select>
      <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期"
        end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 260px" />
      <el-radio-group v-model="period">
        <el-radio-button value="all">全天</el-radio-button>
        <el-radio-button value="morning">上午</el-radio-button>
        <el-radio-button value="afternoon">下午</el-radio-button>
      </el-radio-group>
      <el-button type="primary" @click="load">查询</el-button>
      <el-button @click="onExport">导出Excel</el-button>
    </div>

    <!-- 指标 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="card-num">{{ points.length }}</div>
          <div class="card-label">活跃点位数</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="card-num">{{ maxWeight }}</div>
          <div class="card-label">单点最高人数</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="card-num">{{ totalWeight }}</div>
          <div class="card-label">覆盖打卡总人次</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 热力图：点击格子下钻到点位人员名单 -->
    <el-card shadow="hover" style="margin-top: 16px">
      <template #header><span>人员停留密度热力图（基于打卡GPS定位，点击色块查看该点位人员）</span></template>
      <div ref="chartRef" style="height: 420px"></div>
    </el-card>

    <!-- 场所时段热力图：星期几 × 小时 的平均在场密度 -->
    <el-card shadow="hover" style="margin-top: 16px">
      <template #header><span>场所时段热力图（所选日期范围内，各星期各时段平均在场人数）</span></template>
      <div ref="timeChartRef" style="height: 340px"></div>
    </el-card>

    <!-- 点位下钻弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="760px">
      <el-table :data="detailList" border size="small" max-height="400">
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="dept" label="部门" width="130" />
        <el-table-column prop="venueName" label="场所" width="140" />
        <el-table-column prop="signInTime" label="到达时间（签到）" width="170" />
        <el-table-column prop="signOutTime" label="离开时间（签退）" />
      </el-table>
      <div class="count-tip">共 {{ detailList.length }} 条停留记录</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import request from '../../api/request'
import { download } from '../../api/download'

const venues = ref([])
const venueId = ref(null)
const period = ref('all')
const now = new Date()
const daysAgo = new Date(now.getTime() - 6 * 86400000)
const fmt = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const dateRange = ref([fmt(daysAgo), fmt(now)])

const points = ref([])
const chartRef = ref(null)
const timeChartRef = ref(null)
let chart = null
let timeChart = null

const WEEKDAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

const dialogVisible = ref(false)
const dialogTitle = ref('')
const detailList = ref([])

const maxWeight = computed(() => points.value.reduce((m, p) => Math.max(m, p.weight), 0))
const totalWeight = computed(() => points.value.reduce((s, p) => s + p.weight, 0))

onMounted(async () => {
  const res = await request.get('/venue/list')
  venues.value = res.data
  if (venues.value.length) venueId.value = venues.value[0].id
  load()
})

async function load() {
  if (!venueId.value) {
    points.value = []
    render()
    renderTimeHeatmap({ data: [], max: 0, hourStart: 8, hourEnd: 19 })
    return
  }
  const params = { venueId: venueId.value, start: dateRange.value?.[0], end: dateRange.value?.[1] }
  const [heatRes, timeRes] = await Promise.all([
    request.get('/analysis/heatmap', { params: { ...params, period: period.value } }),
    request.get('/analysis/heatmap/timeHeatmap', { params })
  ])
  points.value = heatRes.data.points
  await nextTick()
  render()
  renderTimeHeatmap(timeRes.data)
}

// 场所时段热力图：X=小时(08~19) Y=周一~周日，颜色深浅=平均在场人数
function renderTimeHeatmap(d) {
  if (!timeChart) timeChart = echarts.init(timeChartRef.value)
  const hours = []
  for (let h = d.hourStart; h <= d.hourEnd; h++) hours.push(String(h).padStart(2, '0') + ':00')
  const data = (d.data || []).map(c => [c.hour - d.hourStart, c.day - 1, c.value])
  timeChart.setOption({
    tooltip: {
      formatter: (p) => `${WEEKDAYS[p.value[1]]} ${hours[p.value[0]]}<br/>平均在场：${p.value[2]} 人`
    },
    grid: { left: 70, right: 90, top: 20, bottom: 50 },
    xAxis: { type: 'category', data: hours, name: '时段', splitArea: { show: true } },
    yAxis: { type: 'category', data: WEEKDAYS, splitArea: { show: true } },
    visualMap: {
      min: 0,
      max: Math.max(d.max || 0, 1),
      calculable: true,
      orient: 'vertical',
      right: 10,
      top: 'center',
      text: ['高', '低'],
      inRange: { color: ['#f3efff', '#c9b8f5', '#9d7fea', '#6e4cd8', '#4a2baf'] }
    },
    series: [{
      type: 'heatmap',
      data,
      label: { show: true, fontSize: 10, formatter: (p) => p.value[2] > 0 ? p.value[2] : '' },
      itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 4 },
      emphasis: { itemStyle: { shadowBlur: 8, shadowColor: 'rgba(0,0,0,0.4)' } }
    }]
  }, true)
}

function render() {
  if (!chart) chart = echarts.init(chartRef.value)
  if (!points.value.length) {
    chart.setOption({ title: { text: '所选范围内暂无带定位的打卡数据', left: 'center', top: 'middle' }, xAxis: { show: false }, yAxis: { show: false }, series: [] }, true)
    return
  }
  // 经纬度各自排序作为网格轴，每个点落到 [x, y, weight]
  const lngs = [...new Set(points.value.map(p => p.lng))].sort((a, b) => a - b)
  const lats = [...new Set(points.value.map(p => p.lat))].sort((a, b) => a - b)
  const data = points.value.map(p => [lngs.indexOf(p.lng), lats.indexOf(p.lat), p.weight])

  chart.setOption({
    tooltip: {
      formatter: (p) => `坐标：${lngs[p.value[0]]}, ${lats[p.value[1]]}<br/>人数：${p.value[2]}`
    },
    grid: { left: 80, right: 100, top: 30, bottom: 80 },
    xAxis: { type: 'category', data: lngs.map(String), name: '经度', axisLabel: { rotate: 45, fontSize: 10 } },
    yAxis: { type: 'category', data: lats.map(String), name: '纬度', axisLabel: { fontSize: 10 } },
    visualMap: {
      min: 0,
      max: Math.max(maxWeight.value, 1),
      calculable: true,
      orient: 'vertical',
      right: 10,
      top: 'center',
      inRange: { color: ['#ebf8ff', '#63b3ed', '#3182ce', '#2b6cb0', '#1a365d'] }
    },
    series: [{
      type: 'heatmap',
      data,
      label: { show: true, formatter: (p) => p.value[2] > 0 ? p.value[2] : '' },
      emphasis: { itemStyle: { shadowBlur: 8, shadowColor: 'rgba(0,0,0,0.4)' } }
    }]
  }, true)

  // 点击色块 → 下钻查看该点位人员名单与停留时段
  chart.off('click')
  chart.on('click', async (p) => {
    const lng = lngs[p.value[0]]
    const lat = lats[p.value[1]]
    dialogTitle.value = `点位（${lng}, ${lat}）人员明细`
    const res = await request.get('/analysis/heatmap/detail', {
      params: {
        venueId: venueId.value, lng, lat,
        start: dateRange.value?.[0], end: dateRange.value?.[1], period: period.value
      }
    })
    detailList.value = res.data
    dialogVisible.value = true
  })
}

function onExport() {
  download('/analysis/heatmap/export', {
    venueId: venueId.value, start: dateRange.value?.[0], end: dateRange.value?.[1], period: period.value
  }, '场所热力点位.xlsx')
}

onBeforeUnmount(() => { chart && chart.dispose(); timeChart && timeChart.dispose() })
</script>

<style scoped>
.filter-bar { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.card-num { font-size: 30px; font-weight: bold; color: #2b6cb0; text-align: center; }
.card-label { text-align: center; color: #718096; margin-top: 6px; }
.count-tip { margin-top: 10px; text-align: right; color: #718096; font-size: 13px; }
</style>
