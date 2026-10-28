<template>
  <div class="h5-page">
    <div class="header">
      <button class="back" @click="router.back()">←</button>
      <div class="title">我的申请</div>
    </div>

    <!-- Tab 筛选 -->
    <div class="tabs">
      <button v-for="t in tabs" :key="t.value"
              :class="{ active: query.status === t.value }"
              @click="switchTab(t.value)">
        {{ t.label }}
      </button>
    </div>

    <div v-if="!loading && list.length === 0" class="empty">暂无申请记录</div>

    <div v-for="item in list" :key="item.id" class="card" @click="showDetail(item)">
      <div class="card-top">
        <div class="type">{{ item.typeText }}</div>
        <el-tag size="small" :type="statusTag(item.status)">{{ item.statusText }}</el-tag>
      </div>
      <div class="card-body">
        <div v-if="item.type !== 'resign'">
          <div class="line">{{ formatTime(item.startTime) }} → {{ formatTime(item.endTime) }}</div>
          <div class="line small">时长 {{ item.durationHours }}h</div>
        </div>
        <div v-else class="line small">离职申请</div>
        <div class="line small">事由：{{ item.reason || item.resignReason }}</div>
      </div>
      <div class="card-foot">
        <span>提交于 {{ formatDate(item.createTime) }}</span>
        <span v-if="item.status === 'pending'" class="leader">审批人：{{ item.leaderName || '-' }}</span>
        <span v-if="item.status === 'rejected'" class="reject-btn" @click.stop="edit(item)">修改重提</span>
        <span v-if="item.status === 'pending'" class="cancel-btn" @click.stop="cancel(item)">撤回</span>
      </div>
    </div>

    <el-dialog v-model="detailVisible" title="申请详情" width="90%">
      <template v-if="detail">
        <div class="detail-row"><span>申请类型</span><span>{{ detail.typeText }}</span></div>
        <div class="detail-row"><span>状态</span><el-tag size="small" :type="statusTag(detail.status)">{{ detail.statusText }}</el-tag></div>
        <div class="detail-row"><span>申请人</span><span>{{ detail.empName }}（{{ detail.empNo }}）</span></div>
        <div class="detail-row"><span>部门</span><span>{{ detail.empDept || '-' }}</span></div>
        <div v-if="detail.type !== 'resign'" class="detail-row"><span>时间</span><span>{{ formatTime(detail.startTime) }} → {{ formatTime(detail.endTime) }}</span></div>
        <div v-if="detail.type !== 'resign'" class="detail-row"><span>时长</span><span>{{ detail.durationHours }}h</span></div>
        <div class="detail-row"><span>{{ detail.type === 'resign' ? '离职原因' : '事由' }}</span><span>{{ detail.reason || detail.resignReason || '-' }}</span></div>
        <div v-if="detail.attachments" class="detail-row"><span>附件</span>
          <img :src="detail.attachments" style="width:60px;height:60px;object-fit:cover;border-radius:6px" />
        </div>
        <div class="detail-row"><span>提交时间</span><span>{{ formatDateTime(detail.createTime) }}</span></div>
        <div v-if="detail.approverName" class="detail-row"><span>审批人</span><span>{{ detail.approverName }}</span></div>
        <div v-if="detail.rejectReason" class="detail-row"><span>驳回意见</span><span style="color:#e53e3e">{{ detail.rejectReason }}</span></div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../../api/request'
import { ElTag, ElDialog, ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const emp = ref(null)
const empId = ref(null)
const list = ref([])
const loading = ref(false)
const detailVisible = ref(false)
const detail = ref(null)

const tabs = [
  { label: '全部', value: '' },
  { label: '待审批', value: 'pending' },
  { label: '已通过', value: 'approved' },
  { label: '已驳回', value: 'rejected' },
]
const query = reactive({ status: '' })

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
    router.replace('/h5/login?redirect=/h5/myapprovals')
    return
  }
  await loadData()
}

async function loadData() {
  loading.value = true
  try {
    const res = await request.get('/h5/approval/my', {
      params: { current: 1, size: 50, type: '', status: query.status }
    })
    list.value = res.data?.records || []
  } finally {
    loading.value = false
  }
}

function switchTab(v) { query.status = v; loadData() }
function statusTag(s) {
  if (s === 'approved') return 'success'
  if (s === 'rejected') return 'danger'
  return 'warning'
}

function formatTime(t) { return t ? t.slice(0, 16).replace('T', ' ') : '-' }
function formatDate(t) { return t ? t.slice(0, 10) : '' }
function formatDateTime(t) { return t ? t.slice(0, 19).replace('T', ' ') : '' }

function showDetail(item) { detail.value = item; detailVisible.value = true }

async function cancel(item) {
  if (!confirm('确定撤回这条申请吗？')) return
  await request.post('/h5/approval/cancel/' + item.id)
  ElMessage.success('已撤回')
  loadData()
}

function edit(item) {
  // 驳回的申请修改：跳转到发起申请页，带上原有参数
  router.push({ path: '/h5/apply', query: { type: item.type } })
}
</script>

<style scoped>
.h5-page { min-height: 100vh; background: #f0f4f8; padding: 16px; padding-bottom: 40px; }
.header { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.back { border: none; background: none; font-size: 22px; color: #2b6cb0; cursor: pointer; }
.title { font-size: 18px; font-weight: bold; color: #1a365d; }

.tabs { display: flex; gap: 0; background: #fff; border-radius: 10px; overflow: hidden; margin-bottom: 12px; }
.tabs button { flex: 1; padding: 10px 0; border: none; background: #fff; font-size: 13px; color: #718096; }
.tabs button.active { background: #2b6cb0; color: #fff; font-weight: bold; }

.card { background: #fff; border-radius: 12px; padding: 16px; margin-bottom: 10px; box-shadow: 0 2px 6px rgba(0,0,0,0.05); }
.card-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.type { font-weight: bold; font-size: 15px; color: #1a365d; }
.line { font-size: 14px; color: #2d3748; }
.line.small { font-size: 12px; color: #718096; margin-top: 2px; }
.card-foot { display: flex; justify-content: space-between; font-size: 12px; color: #a0aec0; margin-top: 10px; }
.leader { color: #718096; }
.reject-btn, .cancel-btn { color: #e53e3e; }
.empty { text-align: center; color: #a0aec0; padding: 40px 0; }

.detail-row { display: flex; justify-content: space-between; padding: 8px 0; font-size: 14px; border-bottom: 1px solid #f0f0f0; }
.detail-row span:first-child { color: #718096; }
</style>
