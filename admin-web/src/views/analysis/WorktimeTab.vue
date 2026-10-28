<template>
  <div>
    <!-- 筛选栏：部门 / 场所 / 日期范围，下面柱状图、饼图、汇总表全部联动 -->
    <div class="filter-bar">
      <el-select v-model="dept" placeholder="全部部门" clearable style="width: 160px">
        <el-option v-for="d in depts" :key="d" :label="d" :value="d" />
      </el-select>
      <el-select v-model="venueId" placeholder="全部场所" clearable style="width: 160px">
        <el-option v-for="v in venues" :key="v.id" :label="v.name" :value="v.id" />
      </el-select>
      <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期"
        end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 260px" />
      <el-button type="primary" @click="load">查询</el-button>
      <el-button @click="onExport">导出Excel</el-button>
    </div>

    <!-- 图表区：部门工时柱状图 + 考勤异常占比饼图 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="14">
        <el-card shadow="hover">
          <template #header><span>部门工时（正常工时 / 加班时长）</span></template>
          <div ref="barChart" style="height: 320px"></div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="hover">
          <template #header><span>考勤状态占比（按次独立计数：同一记录可同时计入多类）</span></template>
          <div ref="pieChart" style="height: 320px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 员工汇总表：点击行穿透到原始打卡+审批单据 -->
    <el-card shadow="hover" style="margin-top: 16px">
      <template #header><span>员工考勤汇总（点击行查看明细）</span></template>
      <el-table :data="emps" border size="small" max-height="420" @row-click="openDetail" highlight-current-row>
        <el-table-column prop="empNo" label="工号" width="90" />
        <el-table-column prop="name" label="姓名" width="90" />
        <el-table-column prop="dept" label="部门" width="110" />
        <el-table-column prop="attendDays" label="出勤天数" width="90" />
        <el-table-column prop="lateCount" label="迟到次数" width="90">
          <template #default="{ row }">
            <span :class="{ warn: row.lateCount > 0 }">{{ row.lateCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="earlyCount" label="早退次数" width="90">
          <template #default="{ row }">
            <span :class="{ warn: row.earlyCount > 0 }">{{ row.earlyCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="missingCount" label="缺卡次数" width="90">
          <template #default="{ row }">
            <span :class="{ warn: row.missingCount > 0 }">{{ row.missingCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="normalHours" label="正常工时(h)" width="110" />
        <el-table-column prop="overtimeHours" label="加班时长(h)" width="110" />
        <el-table-column prop="leaveHours" label="请假时长(h)" width="110" />
        <el-table-column prop="compHours" label="调休时长(h)" />
      </el-table>
    </el-card>

    <!-- 穿透明细弹窗：原始打卡记录 + 审批单据 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="900px" top="6vh">
      <el-tabs>
        <el-tab-pane label="原始打卡记录">
          <el-table :data="detailCheckins" border size="small" max-height="300">
            <el-table-column prop="date" label="日期" width="110" />
            <el-table-column prop="signInTime" label="签到时间" width="140" />
            <el-table-column prop="signOutTime" label="签退时间" width="140" />
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === '正常' ? 'success' : 'danger'" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="venueName" label="场所" width="130" />
            <el-table-column prop="checkinType" label="方式" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="关联审批单据">
          <el-table :data="detailApprovals" border size="small" max-height="300">
            <el-table-column prop="typeText" label="类型" width="90" />
            <el-table-column prop="startTime" label="开始时间" width="150" />
            <el-table-column prop="endTime" label="结束时间" width="150" />
            <el-table-column prop="durationHours" label="时长(h)" width="90" />
            <el-table-column prop="status" label="审批状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === '已通过' ? 'success' : (row.status === '待审批' ? 'warning' : 'info')" size="small">
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="事由" />
          </el-table>
          <div class="count-tip">仅审批状态为「已通过」的单据会计入工时统计</div>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import request from '../../api/request'
import { download } from '../../api/download'

const depts = ref([])
const venues = ref([])
const dept = ref(null)
const venueId = ref(null)
const now = new Date()
const monthStart = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`
const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
const dateRange = ref([monthStart, today])

const emps = ref([])
const barChart = ref(null)
const pieChart = ref(null)
let bar = null, pie = null

const dialogVisible = ref(false)
const dialogTitle = ref('')
const detailCheckins = ref([])
const detailApprovals = ref([])

// 当前筛选参数（加载明细/导出时复用，保证三图与表格同源）
function queryParams() {
  return {
    dept: dept.value || undefined,
    venueId: venueId.value || undefined,
    start: dateRange.value?.[0],
    end: dateRange.value?.[1]
  }
}

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
  const res = await request.get('/analysis/worktime', { params: queryParams() })
  emps.value = res.data.emps
  await nextTick()
  renderBar(res.data.depts)
  renderPie(res.data.anomaly)
}

function renderBar(deptRows) {
  if (!bar) bar = echarts.init(barChart.value)
  bar.setOption({
    tooltip: { trigger: 'axis', valueFormatter: (v) => v == null ? '-' : Number(v).toFixed(2) },
    legend: {},
    grid: { left: 60, right: 20, bottom: 40 },
    xAxis: { type: 'category', data: deptRows.map(d => d.dept) },
    yAxis: { type: 'value', name: '小时' },
    series: [
      { name: '正常工时', type: 'bar', data: deptRows.map(d => d.normalHours), itemStyle: { color: '#2b6cb0' } },
      { name: '加班时长', type: 'bar', data: deptRows.map(d => d.overtimeHours), itemStyle: { color: '#dd6b20' } }
    ]
  }, true)
}

function renderPie(anomaly) {
  if (!pie) pie = echarts.init(pieChart.value)
  pie.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: ['35%', '60%'],
      data: anomaly,
      label: { formatter: '{b}: {c}' }
    }]
  }, true)
}

// 数据穿透：查看员工原始打卡 + 审批单据明细（沿用当前筛选的日期范围）
async function openDetail(row) {
  dialogTitle.value = `${row.name}（${row.empNo}）考勤明细 · ${dateRange.value?.[0]} 至 ${dateRange.value?.[1]}`
  const res = await request.get('/analysis/worktime/detail', {
    params: { ...queryParams(), empId: row.empId }
  })
  detailCheckins.value = res.data.checkins
  detailApprovals.value = res.data.approvals
  dialogVisible.value = true
}

function onExport() {
  download('/analysis/worktime/export', queryParams(), '考勤工时汇总.xlsx')
}

onBeforeUnmount(() => { bar && bar.dispose(); pie && pie.dispose() })
</script>

<style scoped>
.filter-bar { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.warn { color: #e53e3e; font-weight: bold; }
.count-tip { margin-top: 10px; color: #718096; font-size: 12px; }
:deep(.el-table__row) { cursor: pointer; }
</style>
