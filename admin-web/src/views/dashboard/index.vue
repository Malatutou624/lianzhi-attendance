<template>
  <div>
    <!-- 今日概览卡片：数据来自 /stats/today -->
    <el-row :gutter="16">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <el-card shadow="hover" class="stat-card" @click="showPeople(card.type)">
          <div class="card-num">{{ card.value }}</div>
          <div class="card-label">{{ card.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区 -->
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

    <!-- 部门工时报告 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>部门工作时长报告</span>
              <el-date-picker v-model="workMonth" type="month" placeholder="选择月份" value-format="YYYY-MM" style="width: 140px" @change="loadWorkHours" />
            </div>
          </template>
          <el-table :data="workHoursList" border size="small" max-height="300">
            <el-table-column prop="dept" label="部门" />
            <el-table-column prop="empCount" label="打卡人数" width="100" />
            <el-table-column prop="totalHours" label="总工时（小时）" width="130">
              <template #default="{ row }">{{ row.totalHours }}h</template>
            </el-table-column>
            <el-table-column prop="avgHours" label="人均工时（小时）" width="140">
              <template #default="{ row }">{{ row.avgHours }}h</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 人员列表弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px" top="8vh">
      <el-table :data="peopleList" border size="small" max-height="400">
        <el-table-column prop="empNo" label="工号" width="100" />
        <el-table-column prop="name" label="姓名" width="90" />
        <el-table-column prop="dept" label="部门" width="110" />
        <el-table-column prop="position" label="职位" width="110" />
        <el-table-column prop="venueName" label="所属场所" />
        <el-table-column label="打卡状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.checked" :type="row.status === 'late' ? 'danger' : 'success'" size="small">
              {{ row.status === 'late' ? '迟到' : '正常' }}
            </el-tag>
            <el-tag v-else type="info" size="small">未打卡</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="dialogType !== 'unchecked'" prop="checkinTime" label="打卡时间" width="160" />
      </el-table>
      <div class="people-count">共 {{ peopleList.length }} 人</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import * as echarts from 'echarts'
import request from '../../api/request'

const cards = ref([
  { label: '在职员工', value: '-', type: 'active' },
  { label: '今日打卡', value: '-', type: 'checked' },
  { label: '今日迟到', value: '-', type: 'late' },
  { label: '未打卡', value: '-', type: 'unchecked' }
])
const trendChart = ref(null)
const pieChart = ref(null)

// 弹窗相关
const dialogVisible = ref(false)
const dialogTitle = ref('')
const dialogType = ref('')
const peopleList = ref([])

// 部门工时报告
const workMonth = ref('')
const workHoursList = ref([])

async function showPeople(type) {
  dialogType.value = type
  dialogTitle.value = { active: '在职人员', checked: '今日打卡', late: '今日迟到', unchecked: '未打卡' }[type] || '人员列表'
  try {
    const res = await request.get('/stats/people', { params: { type } })
    peopleList.value = res.data
  } catch (e) {
    peopleList.value = []
  }
  dialogVisible.value = true
}

onMounted(async () => {
  // 真实数据：今日概览
  const res = await request.get('/stats/today')
  cards.value = [
    { label: '在职员工', value: res.data.empTotal, type: 'active' },
    { label: '今日打卡', value: res.data.todayCheckin, type: 'checked' },
    { label: '今日迟到', value: res.data.todayLate, type: 'late' },
    { label: '未打卡', value: res.data.notCheckin, type: 'unchecked' }
  ]

  // 加载部门工时报告（默认当月）
  const now = new Date()
  workMonth.value = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  loadWorkHours()

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

// 加载部门工时数据
async function loadWorkHours() {
  if (!workMonth.value) return
  try {
    const res = await request.get('/stats/workHours', { params: { month: workMonth.value } })
    workHoursList.value = res.data
  } catch (e) {
    workHoursList.value = []
  }
}
</script>

<style scoped>
.stat-card { cursor: pointer; transition: transform 0.2s; }
.stat-card:hover { transform: translateY(-2px); }
.card-num { font-size: 32px; font-weight: bold; color: #2b6cb0; text-align: center; }
.card-label { text-align: center; color: #718096; margin-top: 8px; }
.people-count { margin-top: 12px; text-align: right; color: #718096; font-size: 13px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
