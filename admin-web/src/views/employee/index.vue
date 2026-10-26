<template>
  <div>
    <div class="toolbar">
      <el-input v-model="query.name" placeholder="按姓名搜索" style="width: 200px" clearable @change="loadData" />
      <el-select v-model="query.venueId" placeholder="按场所筛选" clearable style="width: 180px" @change="loadData">
        <el-option v-for="v in venueList" :key="v.id" :label="v.name" :value="v.id" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
    </div>

    <el-table :data="tableData" border v-loading="loading">
      <el-table-column prop="empNo" label="工号" width="100" />
      <el-table-column prop="name" label="姓名" width="100" />
      <el-table-column prop="dept" label="部门" />
      <el-table-column prop="position" label="职位" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column label="人脸照片" width="100">
        <template #default="{ row }">
          <el-image v-if="row.facePhoto" :src="row.facePhoto" style="width: 50px; height: 50px" fit="cover" :preview-src-list="[row.facePhoto]" preview-teleported />
          <span v-else style="color: #a0aec0">未采集</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '在职' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
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
// 员工列表页：员工数据主要由 H5 扫码采集进来，这里做查看和管理
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../api/request'

const tableData = ref([])
const total = ref(0)
const loading = ref(false)
const venueList = ref([])
const query = reactive({ current: 1, size: 10, name: '', venueId: null })

async function loadData() {
  loading.value = true
  try {
    const res = await request.get('/employee/page', { params: query })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除员工「${row.name}」吗？`, '提示', { type: 'warning' })
  await request.delete(`/employee/${row.id}`)
  ElMessage.success('已删除')
  loadData()
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
