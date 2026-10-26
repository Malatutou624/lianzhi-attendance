<template>
  <div>
    <!-- 今日概览卡片：数据来自 /stats/today -->
    <el-row :gutter="16">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <el-card shadow="hover">
          <div class="card-num">{{ card.value }}</div>
          <div class="card-label">{{ card.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区：第2周 D11-D12 接入真实接口 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card shadow="hover">
          <div ref="trendChart" style="height: 320px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <div ref="pieChart" style="height: 320px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import * as echarts from 'echarts'
import request from '../../api/request'

const cards = ref([
  { label: '在职员工', value: '-' },
  { label: '今日打卡', value: '-' },
  { label: '今日迟到', value: '-' },
  { label: '未打卡', value: '-' }
])
const trendChart = ref(null)
const pieChart = ref(null)

onMounted(async () => {
  // 真实数据：今日概览
  const res = await request.get('/stats/today')
  cards.value = [
    { label: '在职员工', value: res.data.empTotal },
    { label: '今日打卡', value: res.data.todayCheckin },
    { label: '今日迟到', value: res.data.todayLate },
    { label: '未打卡', value: res.data.notCheckin }
  ]

  // TODO(D11): 换成 /stats/trend 接口的真实数据
  echarts.init(trendChart.value).setOption({
    title: { text: '近7天打卡趋势' },
    xAxis: { type: 'category', data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'] },
    yAxis: { type: 'value' },
    series: [{ type: 'line', smooth: true, data: [0, 0, 0, 0, 0, 0, 0], areaStyle: {} }]
  })

  // TODO(D11): 接入 /stats/dept 部门分布接口
  echarts.init(pieChart.value).setOption({
    title: { text: '打卡状态分布' },
    series: [{
      type: 'pie', radius: '55%',
      data: [
        { name: '正常', value: res.data.todayCheckin - res.data.todayLate },
        { name: '迟到', value: res.data.todayLate },
        { name: '未打卡', value: res.data.notCheckin }
      ]
    }]
  })
})
</script>

<style scoped>
.card-num { font-size: 32px; font-weight: bold; color: #2b6cb0; text-align: center; }
.card-label { text-align: center; color: #718096; margin-top: 8px; }
</style>
