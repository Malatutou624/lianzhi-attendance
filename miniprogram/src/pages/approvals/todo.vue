<template>
  <view class="page">
    <view v-if="!loading && list.length === 0" class="empty">暂无待审批申请 🎉</view>

    <view v-for="item in list" :key="item.id" class="card">
      <view class="card-top">
        <text class="emp">{{ item.empName }}（{{ item.empNo }}）</text>
        <text class="type-tag">{{ item.typeText }}</text>
      </view>
      <text class="dept">{{ item.empDept || '-' }}</text>
      <view class="info">
        <text v-if="item.type !== 'resign'">{{ formatTime(item.startTime) }} → {{ formatTime(item.endTime) }}（{{ item.durationHours }}h）</text>
        <text v-else class="resign-tag">离职申请</text>
      </view>
      <text class="reason">事由：{{ item.reason || item.resignReason }}</text>
      <text class="foot">提交于 {{ formatDate(item.createTime) }}</text>
      <view class="actions">
        <button class="btn-reject" @tap="reject(item)">驳回</button>
        <button class="btn-pass" @tap="pass(item)">同意</button>
      </view>
    </view>

    <view v-if="rejectVisible" class="mask" @tap="rejectVisible = false">
      <view class="dialog" @tap.stop>
        <text class="dialog-title">驳回原因（必填）</text>
        <textarea v-model="rejectReason" class="textarea" placeholder="请填写驳回意见" />
        <view class="dialog-actions">
          <button class="btn-cancel" @tap="rejectVisible = false">取消</button>
          <button class="btn-danger" :disabled="!rejectReason.trim()" @tap="confirmReject">确认驳回</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { http } from '@/utils/request'

const list = ref<any[]>([])
const loading = ref(false)
const rejectVisible = ref(false)
const rejectReason = ref('')
const currentItem = ref<any>(null)

onShow(async () => {
  await loadData()
})

async function loadData() {
  loading.value = true
  try {
    const res = await http.get<any>('/h5/approval/todo', { current: 1, size: 50, type: '' }, { loading: false })
    list.value = res?.records || []
  } finally {
    loading.value = false
  }
}

function formatTime(t: string) { return t ? t.substring(0, 16).replace('T', ' ') : '-' }
function formatDate(t: string) { return t ? t.substring(0, 10) : '' }

async function pass(item: any) {
  uni.showModal({ title: '确认', content: `同意 ${item.empName} 的${item.typeText}申请？`,
    success: async (r) => {
      if (r.confirm) {
        try {
          await http.post('/h5/approval/decide', undefined, { params: { id: item.id, pass: true }, loading: false })
          uni.showToast({ title: '已同意', icon: 'success' })
          loadData()
        } catch {}
      }
    }
  })
}

function reject(item: any) {
  currentItem.value = item
  rejectReason.value = ''
  rejectVisible.value = true
}

async function confirmReject() {
  if (!rejectReason.value.trim()) return
  try {
    await http.post('/h5/approval/decide', undefined, {
      params: { id: currentItem.value.id, pass: false, rejectReason: rejectReason.value },
      loading: false
    })
    uni.showToast({ title: '已驳回', icon: 'success' })
    rejectVisible.value = false
    loadData()
  } catch {}
}
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; background: #f0f4f8; padding: 24rpx; padding-bottom: 80rpx; }

.card { background: #fff; border-radius: 24rpx; padding: 32rpx; margin-bottom: 20rpx; box-shadow: 0 4rpx 12rpx rgba(0,0,0,0.05); }
.card-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8rpx; }
.emp { font-weight: bold; font-size: 30rpx; color: #1a365d; }
.type-tag { background: #ebf8ff; color: #2b6cb0; padding: 4rpx 20rpx; border-radius: 20rpx; font-size: 24rpx; }
.dept { font-size: 24rpx; color: #718096; margin-bottom: 16rpx; display: block; }
.info { font-size: 28rpx; color: #2d3748; margin-bottom: 8rpx; display: block; }
.resign-tag { color: #c53030; font-weight: 500; }
.reason { font-size: 26rpx; color: #4a5568; margin: 12rpx 0; background: #f7fafc; padding: 16rpx; border-radius: 12rpx; display: block; }
.foot { font-size: 24rpx; color: #a0aec0; margin-bottom: 20rpx; display: block; }

.actions { display: flex; gap: 20rpx; }
.actions button { flex: 1; padding: 20rpx; border: none; border-radius: 16rpx; font-size: 28rpx; font-weight: bold; }
.btn-reject { background: #fff; color: #e53e3e; border: 2rpx solid #e53e3e !important; }
.btn-pass { background: #38a169; color: #fff; }

.empty { text-align: center; color: #a0aec0; padding: 120rpx 0; font-size: 32rpx; }

.mask { position: fixed; inset: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 999; }
.dialog { background: #fff; border-radius: 24rpx; padding: 40rpx; width: 560rpx; }
.dialog-title { font-size: 32rpx; font-weight: bold; color: #1a365d; display: block; margin-bottom: 24rpx; }
.textarea { width: 100%; height: 200rpx; border: 2rpx solid #e2e8f0; border-radius: 16rpx; padding: 20rpx; font-size: 28rpx; box-sizing: border-box; }
.dialog-actions { display: flex; gap: 20rpx; margin-top: 24rpx; }
.btn-cancel { flex: 1; padding: 16rpx; border: 2rpx solid #e2e8f0; background: #fff; border-radius: 16rpx; font-size: 28rpx; color: #4a5568; }
.btn-danger { flex: 1; padding: 16rpx; border: none; background: #e53e3e; border-radius: 16rpx; font-size: 28rpx; color: #fff; }
.btn-danger[disabled] { background: #a0aec0; }
</style>
