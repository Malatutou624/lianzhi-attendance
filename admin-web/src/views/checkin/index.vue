<template>
  <div>
    <div class="toolbar">
      <el-date-picker v-model="query.date" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" @change="loadData" />
      <el-select v-model="query.venueId" placeholder="按场所筛选" clearable style="width: 180px" @change="loadData">
        <el-option v-for="v in venueList" :key="v.id" :label="v.name" :value="v.id" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
    </div>

    <el-table :data="tableData" border v-loading="loading">
      <el-table-column prop="empName" label="姓名" width="100" />
      <el-table-column prop="venueName" label="打卡场所" />
      <el-table-column prop="checkinTime" label="打卡时间" width="180" />
      <el-table-column label="方式" width="110">
        <template #default="{ row }">
          <el-tag>{{ row.checkinType === 'face' ? '人脸识别' : '拍照' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="similarity" label="相似度" width="90" />
      <el-table-column label="照片" width="90">
        <template #default="{ row }">
          <el-image v-if="row.photoUrl" :src="row.photoUrl" style="width: 50px; height: 50px" fit="cover" :preview-src-list="[row.photoUrl]" preview-teleported />
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'normal' ? 'success' : 'warning'">
            {{ { normal: '正常', late: '迟到', early: '早退' }[row.status] || row.status }}
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
const query = reactive({ current: 1, size: 10, date: null, venueId: null })

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

onMounted(async () => {
  loadData()
  const res = await request.get('/venue/list')
  venueList.value = res.data
})
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 12px; }
</style>
