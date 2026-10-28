<template>
  <div>
    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-select v-model="venueId" placeholder="全部场所" clearable style="width: 180px">
        <el-option v-for="v in venues" :key="v.id" :label="v.name" :value="v.id" />
      </el-select>
      <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期"
        end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 260px" />
      <el-radio-group v-model="dayType">
        <el-radio-button value="all">全部</el-radio-button>
        <el-radio-button value="workday">工作日</el-radio-button>
        <el-radio-button value="weekend">休息日</el-radio-button>
      </el-radio-group>
      <el-button type="primary" @click="load">查询</el-button>
      <el-button @click="onExport">导出Excel</el-button>
    </div>

    <!-- 指标卡片 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="6" v-for="m in metricCards" :key="m.label">
        <el-card shadow="hover">
          <div class="card-num">{{ m.value }}</div>
          <div class="card-label">{{ m.label }}</div>
          <div class="card-sub">{{ m.sub }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 折线图：点击曲线上的点可查看该时段在场人员明细 -->
    <el-card shadow="hover" style="margin-top: 16px">
      <template #header><span>全天时段人流时序曲线（点击图中数据点查看在场人员明细）</span></template>
      <div ref="chartRef" style="height: 380px"></div>
    </el-card>

    <!-- 明细弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="760px">
      <el-table :data="detailList" border size="small" max-height="420">
        <el-table-column prop="empNo" label="工号" width="100" />
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="dept" label="部门" width="120" />
        <el-table-column prop="venueName" label="场所" width="140" />
        <el-table-column prop="signInTime" label="签到时间" width="170" />
        <el-table-column prop="signOutTime" label="签退时间" />
      </el-table>
      <div class="count-tip">共 {{ detailList.length }} 人</div>
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
const dayType = ref('all')
const now = new Date()
const daysAgo = new Date(now.getTime() - 6 * 86400000)
const fmt = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const dateRange = ref([fmt(daysAgo), fmt(now)])

const flowData = ref([])
const chartRef = ref(null)
let chart = null

const dialogVisible = ref(false)
const dialogTitle = ref('')
const detailList = ref([])

// 指标卡片：取第一个（或所选）场所的峰值/谷值/均值
const metricCards = computed(() => {
  const v = flowData.value.length ? flowData.value[0] : null
  return [
    { label: '峰值人数', value: v ? v.peak : '-', sub: v ? v.peakTime : '' },
    { label: '谷值人数', value: v ? v.valley : '-', sub: v ? v.valleyTime : '' },
    { label: '平均在场人数', value: v ? v.avg : '-', sub: '统计窗口 6:00-22:00' },
    { label: '场所数', value: flowData.value.length || '-', sub: '当前筛选范围' }
  ]
})

onMounted(async () => {
  const res = await request.get('/venue/list')
  venues.value = res.data
  load()
})

async function load() {
  const params = {
    venueId: venueId.value || undefined,
    start: dateRange.value?.[0],
    end: dateRange.value?.[1],
    dayType: dayType.value
  }
  const res = await request.get('/analysis/venueFlow', { params })
  flowData.value = res.data.venues
  await nextTick()
  render()
}

function render() {
  if (!chart) chart = echarts.init(chartRef.value)
  if (!flowData.value.length) {
    chart.setOption({ title: { text: '暂无数据', left: 'center', top: 'middle' }, xAxis: { show: false }, yAxis: { show: false }, series: [] })
    return
  }
  // 所有场所共用同一套时段 x 轴（后端已按时间对齐）
  const x = flowData.value[0].points.map(p => p.time)
  const series = flowData.value.map(v => ({
    name: v.venueName,
    type: 'line',
    smooth: true,
    data: v.points.map(p => p.count),
    symbolSize: 7
  }))
  chart.setOption({
    title: { text: '' },
    tooltip: { trigger: 'axis' },
    legend: { type: 'scroll' },
    grid: { left: 50, right: 30, bottom: 70 },
    xAxis: {
      type: 'category', data: x, name: '时段',
      axisLabel: { rotate: 45, formatter: (v) => v.slice(5) } // 只显示 10-28 08:00，点击穿透仍用完整值
    },
    yAxis: { type: 'value', name: '在场人数' },
    series
  }, true)
  // 点击数据点 → 穿透到该时段在场人员
  chart.off('click')
  chart.on('click', async (p) => {
    const v = flowData.value[p.seriesIndex]
    if (!v) return
    dialogTitle.value = `${v.venueName} · ${p.name} 在场人员`
    const res = await request.get('/analysis/venueFlow/detail', {
      params: { venueId: v.venueId, slot: p.name }
    })
    detailList.value = res.data
    dialogVisible.value = true
  })
}

function onExport() {
  download('/analysis/venueFlow/export', {
    venueId: venueId.value || undefined,
    start: dateRange.value?.[0],
    end: dateRange.value?.[1],
    dayType: dayType.value
  }, '场所人流分析.xlsx')
}

onBeforeUnmount(() => { chart && chart.dispose() })
</script>

<style scoped>
.filter-bar { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.card-num { font-size: 30px; font-weight: bold; color: #2b6cb0; text-align: center; }
.card-label { text-align: center; color: #718096; margin-top: 6px; }
.card-sub { text-align: center; color: #a0aec0; font-size: 12px; margin-top: 4px; }
.count-tip { margin-top: 10px; text-align: right; color: #718096; font-size: 13px; }
</style>
