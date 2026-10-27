<template>
  <div class="h5-page">
    <div class="header">
      <button class="back" @click="router.back()">←</button>
      <div class="title">待我审批</div>
    </div>

    <div v-if="!loading && list.length === 0" class="empty">暂无待审批申请 🎉</div>

    <div v-for="item in list" :key="item.id" class="card">
      <div class="card-top">
        <div class="emp">{{ item.empName }}（{{ item.empNo }}）</div>
        <div class="type-tag">{{ item.typeText }}</div>
      </div>
      <div class="dept-line">{{ item.empDept || '-' }}</div>
      <div class="info">
        <div v-if="item.type !== 'resign'">{{ formatTime(item.startTime) }} → {{ formatTime(item.endTime) }}（{{ item.durationHours }}h）</div>
        <div v-else class="resign-tag">离职申请</div>
      </div>
      <div class="reason">事由：{{ item.reason || item.resignReason }}</div>
      <div class="foot">
        <span>提交于 {{ formatDate(item.createTime) }}</span>
      </div>
      <div class="actions">
        <button class="btn-reject" @click="reject(item)">驳回</button>
        <button class="btn-pass" @click="pass(item)">同意</button>
      </div>
    </div>

    <el-dialog v-model="rejectVisible" title="驳回原因（必填）" width="90%">
      <textarea v-model="rejectReason" class="input" rows="4" placeholder="请填写驳回意见"></textarea>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :disabled="!rejectReason.trim()" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../../api/request'
import { ElMessage, ElDialog } from 'element-plus'

const route = useRoute()
const router = useRouter()
const emp = ref(null)
const empId = ref(null)
const list = ref([])
const loading = ref(false)
const rejectVisible = ref(false)
const rejectReason = ref('')
const currentItem = ref(null)

onMounted(async () => {
  await identify()
})

async function identify() {
  try {
    const infoRes = await request.get('/h5/auth/info')
    empId.value = infoRes.data?.empId
    emp.value = infoRes.data?.employee
    if (!emp.value) {
      ElMessage.warning('请先完成员工信息采集')
      router.replace('/h5/collect')
      return
    }
  } catch (e) {
    router.replace('/h5/login?redirect=/h5/todoapprovals')
    return
  }
  await loadData()
}

async function loadData() {
  loading.value = true
  try {
    const res = await request.get('/h5/approval/todo', {
      params: { current: 1, size: 50, type: '' }
    })
    list.value = res.data?.records || []
  } finally {
    loading.value = false
  }
}

function formatTime(t) { return t ? t.slice(0, 16).replace('T', ' ') : '-' }
function formatDate(t) { return t ? t.slice(0, 10) : '' }

async function pass(item) {
  if (!confirm(`确认同意 ${item.empName} 的${item.typeText}申请？`)) return
  await request.post('/h5/approval/decide', null, {
    params: { id: item.id, pass: true }
  })
  ElMessage.success('已同意')
  loadData()
}

function reject(item) {
  currentItem.value = item
  rejectReason.value = ''
  rejectVisible.value = true
}

async function confirmReject() {
  try {
    await request.post('/h5/approval/decide', null, {
      params: { id: currentItem.value.id, pass: false, rejectReason: rejectReason.value }
    })
    ElMessage.success('已驳回')
    rejectVisible.value = false
    loadData()
  } catch (e) {}
}
</script>

<style scoped>
.h5-page { min-height: 100vh; background: #f0f4f8; padding: 16px; padding-bottom: 40px; }
.header { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.back { border: none; background: none; font-size: 22px; color: #2b6cb0; cursor: pointer; }
.title { font-size: 18px; font-weight: bold; color: #1a365d; }

.card { background: #fff; border-radius: 12px; padding: 16px; margin-bottom: 10px; box-shadow: 0 2px 6px rgba(0,0,0,0.05); }
.card-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.emp { font-weight: bold; font-size: 15px; color: #1a365d; }
.type-tag { background: #ebf8ff; color: #2b6cb0; padding: 2px 10px; border-radius: 10px; font-size: 12px; }
.dept-line { font-size: 12px; color: #718096; margin-bottom: 8px; }
.info { font-size: 14px; color: #2d3748; margin-bottom: 4px; }
.resign-tag { color: #c53030; font-weight: 500; }
.reason { font-size: 13px; color: #4a5568; margin: 6px 0; background: #f7fafc; padding: 8px; border-radius: 6px; }
.foot { font-size: 12px; color: #a0aec0; margin-bottom: 10px; }
.actions { display: flex; gap: 10px; }
.actions button { flex: 1; padding: 10px; border: none; border-radius: 8px; font-size: 14px; font-weight: bold; }
.btn-reject { background: #fff; color: #e53e3e; border: 1px solid #e53e3e !important; }
.btn-pass { background: #38a169; color: #fff; }
.empty { text-align: center; color: #a0aec0; padding: 60px 0; }
.input { width: 100%; padding: 10px; border: 1px solid #e2e8f0; border-radius: 8px; box-sizing: border-box; font-size: 14px; }
</style>
