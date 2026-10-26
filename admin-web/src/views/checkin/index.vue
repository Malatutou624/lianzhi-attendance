<template>
  <div>
    <div class="toolbar">
      <el-date-picker v-model="query.date" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" @change="loadData" />
      <el-select v-model="query.venueId" placeholder="按场所筛选" clearable style="width: 140px" @change="loadData">
        <el-option v-for="v in venueList" :key="v.id" :label="v.name" :value="v.id" />
      </el-select>
      <el-select v-model="query.status" placeholder="员工状态" clearable style="width: 140px" @change="loadData">
        <el-option label="正常签到" value="sign_in_normal" />
        <el-option label="迟到" value="sign_in_late" />
        <el-option label="正常签退" value="sign_out_normal" />
        <el-option label="早退" value="sign_out_early" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
    </div>

    <el-table :data="tableData" border v-loading="loading">
      <el-table-column prop="empName" label="姓名" width="100" />
      <el-table-column prop="dept" label="部门" width="120" />
      <el-table-column prop="venueName" label="打卡场所" />
      <el-table-column prop="signInTime" label="签到时间" width="160" />
      <el-table-column prop="signOutTime" label="签退时间" width="160">
        <template #default="{ row }">
          <span v-if="row.signOutTime">{{ row.signOutTime }}</span>
          <span v-else class="no-signout">未签退</span>
        </template>
      </el-table-column>
      <el-table-column label="方式" width="110">
        <template #default="{ row }">
          <el-tag>{{ row.checkinType === 'face' ? '人脸识别' : '拍照' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="similarity" label="相似度" width="90" />
      <el-table-column label="照片" width="90">
        <template #default="{ row }">
          <el-image v-if="row.photoUrl" :src="row.photoUrl" style="width: 50px; height: 50px" fit="cover" :preview-src-list="[row.photoUrl]" preview-teleported />
          <span v-else class="no-photo">无照片</span>
        </template>
      </el-table-column>
      <el-table-column label="工时" width="90">
        <template #default="{ row }">
          <span v-if="row.signInTime && row.signOutTime">{{ calcWorkHours(row) }}</span>
          <span v-else class="no-signout">-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="getStatusTagType(row)">
            {{ getStatusText(row) }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top: 12px; justify-content: flex-end"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="query.size"
      :current-page="query.current"
      @current-change="(p) => { query.current = p; loadData() }"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import request from '../../api/request'

const tableData = ref([])
const total = ref(0)
const loading = ref(false)
const venueList = ref([])
const query = reactive({ current: 1, size: 10, date: null, venueId: null, status: null })

async function loadData() {
  loading.value = true
  try {
    const res = await request.get('/checkin/page', { params: query })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

// 状态显示文本：根据签到/签退状态组合显示
function getStatusText(row) {
  if (row.status === 'late') return '迟到'
  if (row.status === 'early') return '早退'
  if (row.signOutTime) return '正常签退'
  return '正常签到'
}

// 标签颜色
function getStatusTagType(row) {
  if (row.status === 'late') return 'danger'
  if (row.status === 'early') return 'warning'
  return 'success'
}

// 计算工时（小时）
function calcWorkHours(row) {
  const inTime = new Date(row.signInTime)
  const outTime = new Date(row.signOutTime)
  const diffMs = outTime - inTime
  const hours = diffMs / (1000 * 60 * 60)
  return hours.toFixed(2) + 'h'
}

onMounted(async () => {
  loadData()
  const res = await request.get('/venue/list')
  venueList.value = res.data
})
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 12px; }
.no-signout { color: #a0aec0; font-style: italic; }
.no-photo { color: #cbd5e0; font-size: 12px; }
</style>
