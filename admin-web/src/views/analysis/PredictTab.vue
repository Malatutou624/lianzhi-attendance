<template>
  <div>
    <!-- 筛选栏：部门 / 场所 / 指标类型 / 预测天数，任一变化立即重新计算 -->
    <div class="filter-bar">
      <el-select v-model="dept" placeholder="全部部门" clearable style="width: 150px" @change="load">
        <el-option v-for="d in depts" :key="d" :label="d" :value="d" />
      </el-select>
      <el-select v-model="venueId" placeholder="全部场所" clearable style="width: 150px" @change="load">
        <el-option v-for="v in venues" :key="v.id" :label="v.name" :value="v.id" />
      </el-select>
      <el-radio-group v-model="type" @change="load">
        <el-radio-button value="absent">缺勤人数</el-radio-button>
        <el-radio-button value="leave">请假人次</el-radio-button>
        <el-radio-button value="overtime">加班时长</el-radio-button>
      </el-radio-group>
      <span class="slider-label">预测未来</span>
      <el-slider v-model="days" :min="1" :max="30" style="width: 180px" show-input :show-input-controls="false" input-size="small" @change="load" />
      <span class="slider-label">天</span>
      <el-button @click="onExport">导出Excel</el-button>
    </div>

    <!-- 说明 -->
    <el-alert type="info" :closable="false" style="margin-top: 12px"
      title="轻量预测模型：按「星期几」统计近4周同星期历史均值，对未来工作日逐日预测；已批准的未来请假/加班单据属于确定事件，会直接体现在预测曲线上。" />

    <!-- 历史 + 预测双折线 -->
    <el-card shadow="hover" style="margin-top: 16px">
      <template #header><span>{{ typeName }} · 历史真实值（实线）与预测值（虚线）对比</span></template>
      <div ref="chartRef" style="height: 400px"></div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import request from '../../api/request'
import { download } from '../../api/download'

const depts = ref([])
const venues = ref([])
const dept = ref(null)
const venueId = ref(null)
const type = ref('absent')
const days = ref(7)
const chartRef = ref(null)
let chart = null

const historyData = ref([])
const predictData = ref([])

const typeName = computed(() => ({ absent: '缺勤人数', leave: '请假人次', overtime: '加班时长(h)' }[type.value]))

onMounted(async () => {
  const [d, v] = await Promise.all([
    request.get('/analysis/depts'),
    request.get('/venue/list')
  ])
  depts.value = d.data
  venues.value = v.data
  load()
})

async function load() {
  const res = await request.get('/analysis/predict', {
    params: { dept: dept.value || undefined, venueId: venueId.value || undefined, type: type.value, days: days.value }
  })
  historyData.value = res.data.history
  predictData.value = res.data.predict
  await nextTick()
  render()
}

function render() {
  if (!chart) chart = echarts.init(chartRef.value)
  const x = [...historyData.value.map(p => p.date), ...predictData.value.map(p => p.date)]
  // 历史实线：历史区间有值，预测区间断开
  const histLine = historyData.value.map(p => p.value)
    .concat(predictData.value.map(() => null))
  // 预测虚线：历史区间为 null（对齐到 X 轴末尾的预测区间），起点接最后一个历史值使两条线相连
  const predictLine = historyData.value.map(() => null)
  if (historyData.value.length) {
    predictLine[predictLine.length - 1] = historyData.value[historyData.value.length - 1].value
  }
  predictLine.push(...predictData.value.map(p => p.value))

  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['历史真实值', '预测值'] },
    grid: { left: 60, right: 30, bottom: 70 },
    xAxis: { type: 'category', data: x, axisLabel: { rotate: 45, formatter: (v) => v.slice(5) } },
    yAxis: { type: 'value', name: typeName.value, minInterval: type.value === 'overtime' ? 0 : 1 },
    series: [
      { name: '历史真实值', type: 'line', smooth: true, data: histLine, connectNulls: false, itemStyle: { color: '#2b6cb0' } },
      { name: '预测值', type: 'line', smooth: true, data: predictLine, connectNulls: false, lineStyle: { type: 'dashed', color: '#dd6b20' }, itemStyle: { color: '#dd6b20' } }
    ]
  }, true)
}

function onExport() {
  download('/analysis/predict/export', {
    dept: dept.value || undefined, venueId: venueId.value || undefined, type: type.value, days: days.value
  }, '考勤趋势预测.xlsx')
}

onBeforeUnmount(() => { chart && chart.dispose() })
</script>

<style scoped>
.filter-bar { display: flex; gap: 14px; align-items: center; flex-wrap: wrap; }
.slider-label { color: #718096; font-size: 13px; }
</style>
