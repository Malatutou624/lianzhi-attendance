<template>
  <div>
    <el-tabs v-model="tab" @tab-change="onTabChange">
      <!-- 待我审批 -->
      <el-tab-pane label="待我审批" name="todo">
        <div class="toolbar">
          <el-select v-model="todoQuery.type" placeholder="申请类型" clearable style="width:140px" @change="loadTodo">
            <el-option v-for="o in typeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-button type="primary" @click="loadTodo">查询</el-button>
        </div>
        <el-table :data="todoList" border v-loading="todoLoading">
          <el-table-column label="申请人" width="140">
            <template #default="{ row }">{{ row.empName }}（{{ row.empNo }}）</template>
          </el-table-column>
          <el-table-column prop="empDept" label="部门" width="110" />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ row.typeText }}</template>
          </el-table-column>
          <el-table-column label="申请时段" width="280">
            <template #default="{ row }">
              <template v-if="row.type === 'resign'">离职申请</template>
              <template v-else>{{ row.startTime?.slice(0,16).replace('T',' ') }} → {{ row.endTime?.slice(0,16).replace('T',' ') }}（{{ row.durationHours }}h）</template>
            </template>
          </el-table-column>
          <el-table-column label="事由">
            <template #default="{ row }">{{ row.reason || row.resignReason }}</template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="success" @click="pass(row)">同意</el-button>
              <el-button size="small" type="danger" @click="reject(row)">驳回</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 全部单据 -->
      <el-tab-pane label="全部单据" name="all">
        <div class="toolbar">
          <el-input v-model="allQuery.empName" placeholder="申请人姓名" clearable style="width:140px" @change="loadAll" />
          <el-select v-model="allQuery.type" placeholder="申请类型" clearable style="width:140px" @change="loadAll">
            <el-option v-for="o in typeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-select v-model="allQuery.status" placeholder="审批状态" clearable style="width:120px" @change="loadAll">
            <el-option label="待审批" value="pending" />
            <el-option label="已通过" value="approved" />
            <el-option label="已驳回" value="rejected" />
            <el-option label="已撤回" value="cancelled" />
          </el-select>
          <el-button type="primary" @click="loadAll">查询</el-button>
        </div>
        <el-table :data="allList" border v-loading="allLoading">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column label="申请人" width="130">
            <template #default="{ row }">{{ row.empName }}（{{ row.empNo }}）</template>
          </el-table-column>
          <el-table-column prop="empDept" label="部门" width="110" />
          <el-table-column label="类型" width="90"><template #default="{ row }">{{ row.typeText }}</template></el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status)">{{ row.statusText }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="leaderName" label="审批人" width="100" />
          <el-table-column label="提交时间" width="160">
            <template #default="{ row }">{{ row.createTime?.slice(0,19).replace('T',' ') }}</template>
          </el-table-column>
          <el-table-column label="详情" width="80" fixed="right">
            <template #default="{ row }">
              <el-button size="small" link @click="showDetail(row)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination style="margin-top:12px;justify-content:flex-end" layout="total,prev,pager,next"
          :total="allTotal" :page-size="allQuery.size" :current-page="allQuery.current"
          @current-change="(p)=>{ allQuery.current=p; loadAll() }" />
      </el-tab-pane>

      <!-- 我已处理 -->
      <el-tab-pane label="我已处理" name="handled">
        <el-table :data="handledList" border v-loading="handledLoading">
          <el-table-column label="申请人" width="130">
            <template #default="{ row }">{{ row.empName }}（{{ row.empNo }}）</template>
          </el-table-column>
          <el-table-column label="类型" width="90"><template #default="{ row }">{{ row.typeText }}</template></el-table-column>
          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 'approved' ? 'success' : 'danger'">
                {{ row.status === 'approved' ? '已同意' : '已驳回' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="审批时间" width="160">
            <template #default="{ row }">{{ row.approveTime?.slice(0,19).replace('T',' ') }}</template>
          </el-table-column>
          <el-table-column label="驳回原因">
            <template #default="{ row }">{{ row.rejectReason || '-' }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="申请详情" width="560px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="申请类型">{{ detail.typeText }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTag(detail.status)">{{ detail.statusText }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="申请人">{{ detail.empName }}（{{ detail.empNo }}）</el-descriptions-item>
          <el-descriptions-item label="部门">{{ detail.empDept || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.type !== 'resign'" label="开始时间">{{ detail.startTime?.slice(0,16).replace('T',' ') }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.type !== 'resign'" label="结束时间">{{ detail.endTime?.slice(0,16).replace('T',' ') }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.type !== 'resign'" label="时长">{{ detail.durationHours }}h</el-descriptions-item>
          <el-descriptions-item label="审批人">{{ detail.approverName || detail.leaderName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ detail.createTime?.slice(0,19).replace('T',' ') }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.approveTime" label="审批时间">{{ detail.approveTime?.slice(0,19).replace('T',' ') }}</el-descriptions-item>
          <el-descriptions-item label="事由/离职原因" :span="2">{{ detail.reason || detail.resignReason || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.rejectReason" label="驳回意见" :span="2">
            <span style="color:#e53e3e">{{ detail.rejectReason }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import request from '../../api/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const tab = ref('todo')
const typeOptions = [
  { value: 'leave', label: '事假' }, { value: 'sick', label: '病假' },
  { value: 'annual', label: '年假' }, { value: 'overtime', label: '加班' },
  { value: 'compensate', label: '调休' }, { value: 'makeup', label: '补卡' },
  { value: 'resign', label: '离职' },
]

// 当前登录管理员（onMounted 时从 /auth/info 动态获取）
const currentUser = reactive({ id: 1, name: '管理员' })

// Todo Tab
const todoQuery = reactive({ type: null })
const todoList = ref([])
const todoLoading = ref(false)

// All Tab
const allQuery = reactive({ current: 1, size: 10, empName: '', type: null, status: null })
const allList = ref([])
const allTotal = ref(0)
const allLoading = ref(false)

// Handled Tab
const handledList = ref([])
const handledLoading = ref(false)

// Detail
const detailVisible = ref(false)
const detail = ref(null)

onMounted(async () => {
  try {
    const info = await request.get('/auth/info')
    if (info.data) {
      currentUser.id = info.data.id || 1
      currentUser.name = info.data.realName || info.data.username || '管理员'
    }
  } catch (e) {}
  loadTodo()
})

function onTabChange(t) {
  if (t === 'todo') loadTodo()
  else if (t === 'all') loadAll()
  else if (t === 'handled') loadHandled()
}

async function loadTodo() {
  todoLoading.value = true
  try {
    const res = await request.get('/approval/todo', { params: { leaderId: currentUser.id, type: todoQuery.type, current: 1, size: 50 } })
    todoList.value = res.data.records
  } finally { todoLoading.value = false }
}

async function loadAll() {
  allLoading.value = true
  try {
    const res = await request.get('/approval/all', {
      params: {
        current: allQuery.current,
        size: allQuery.size,
        type: allQuery.type || null,
        status: allQuery.status || null,
        empName: allQuery.empName || null
      }
    })
    allList.value = res.data.records
    allTotal.value = res.data.total
  } finally { allLoading.value = false }
}

async function loadHandled() {
  handledLoading.value = true
  try {
    const res = await request.get('/approval/handled', { params: { approverId: currentUser.id, current: 1, size: 50 } })
    handledList.value = res.data.records
  } finally { handledLoading.value = false }
}

function statusTag(s) {
  if (s === 'approved') return 'success'
  if (s === 'rejected') return 'danger'
  if (s === 'cancelled') return 'info'
  return 'warning'
}

function showDetail(row) { detail.value = row; detailVisible.value = true }

async function pass(row) {
  await ElMessageBox.confirm(`确认同意 ${row.empName} 的${row.typeText}申请？`, '提示', { type: 'warning' })
  await request.post('/approval/decide', null, { params: { id: row.id, approverId: currentUser.id, pass: true } })
  ElMessage.success('已同意')
  loadTodo()
}

async function reject(row) {
  const { value: reason } = await ElMessageBox.prompt('请输入驳回意见', '驳回', {
    confirmButtonText: '确认驳回',
    cancelButtonText: '取消',
    inputValidator: v => v.trim() ? true : '请填写驳回意见'
  })
  await request.post('/approval/decide', null, {
    params: { id: row.id, approverId: currentUser.id, pass: false, rejectReason: reason }
  })
  ElMessage.success('已驳回')
  loadTodo()
}
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 12px; }
</style>
