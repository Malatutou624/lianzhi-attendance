<template>
  <view class="page">
    <view class="tabs">
      <view v-for="t in tabs" :key="t.value" :class="['tab', query.status === t.value ? 'active' : '']" @tap="switchTab(t.value)">
        {{ t.label }}
      </view>
    </view>

    <view v-if="!loading && list.length === 0" class="empty">暂无申请记录</view>

    <view v-for="item in list" :key="item.id" class="card" @tap="showDetail(item)">
      <view class="card-top">
        <text class="type">{{ item.typeText }}</text>
        <text :class="['status-tag', item.status]">{{ item.statusText }}</text>
      </view>
      <view class="card-body">
        <view v-if="item.type !== 'resign'">
          <text class="line">{{ formatTime(item.startTime) }} → {{ formatTime(item.endTime) }}</text>
          <text class="line small">时长 {{ item.durationHours }}h</text>
        </view>
        <text v-else class="line small">离职申请</text>
        <text class="line small">事由：{{ item.reason || item.resignReason }}</text>
      </view>
      <view class="card-foot">
        <text>提交于 {{ formatDate(item.createTime) }}</text>
        <text v-if="item.status === 'pending'" class="leader">审批人：{{ item.leaderName || '-' }}</text>
      </view>
      <view class="card-actions" v-if="item.status === 'pending' || item.status === 'rejected'">
        <text v-if="item.status === 'rejected'" class="action-btn reject" @tap.stop="edit(item)">修改重提</text>
        <text v-if="item.status === 'pending'" class="action-btn cancel" @tap.stop="cancel(item)">撤回</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { http } from '@/utils/request'

const list = ref<any[]>([])
const loading = ref(false)
const query = reactive({ status: '' })

const tabs = [
  { label: '全部', value: '' },
  { label: '待审批', value: 'pending' },
  { label: '已通过', value: 'approved' },
  { label: '已驳回', value: 'rejected' },
]

onShow(async () => {
  await loadData()
})

async function loadData() {
  loading.value = true
  try {
    const res = await http.get<any>('/h5/approval/my', { current: 1, size: 50, type: '', status: query.status }, { loading: false })
    list.value = res?.records || []
  } finally {
    loading.value = false
  }
}

function switchTab(v: string) { query.status = v; loadData() }

function formatTime(t: string) { return t ? t.substring(0, 16).replace('T', ' ') : '-' }
function formatDate(t: string) { return t ? t.substring(0, 10) : '' }

function showDetail(item: any) {
  let content = `类型：${item.typeText}\n状态：${item.statusText}\n时间：${formatTime(item.startTime)} → ${formatTime(item.endTime)}\n事由：${item.reason || item.resignReason || '-'}\n提交时间：${formatDate(item.createTime)}`
  if (item.leaderName) content += `\n审批人：${item.leaderName}`
  if (item.rejectReason) content += `\n驳回意见：${item.rejectReason}`
  uni.showModal({ title: '申请详情', content, showCancel: false })
}

async function cancel(item: any) {
  uni.showModal({ title: '撤回', content: '确定撤回这条申请吗？', success: async (r) => {
    if (r.confirm) {
      try {
        await http.post('/h5/approval/cancel/' + item.id, undefined, { loading: false })
        uni.showToast({ title: '已撤回', icon: 'success' })
        loadData()
      } catch {}
    }
  }})
}

function edit(item: any) {
  uni.navigateTo({ url: '/pages/apply/apply' })
}
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; background: #f0f4f8; padding: 24rpx; padding-bottom: 80rpx; }
.tabs { display: flex; background: #fff; border-radius: 16rpx; overflow: hidden; margin-bottom: 24rpx; }
.tab { flex: 1; padding: 20rpx 0; text-align: center; font-size: 26rpx; color: #718096; }
.tab.active { background: #2b6cb0; color: #fff; font-weight: bold; }

.card { background: #fff; border-radius: 24rpx; padding: 32rpx; margin-bottom: 20rpx; box-shadow: 0 4rpx 12rpx rgba(0,0,0,0.05); }
.card-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16rpx; }
.type { font-weight: bold; font-size: 30rpx; color: #1a365d; }
.status-tag { font-size: 22rpx; padding: 6rpx 20rpx; border-radius: 20rpx; }
.status-tag.pending { background: #fef3c7; color: #92400e; }
.status-tag.approved { background: #c6f6d5; color: #22543d; }
.status-tag.rejected { background: #fed7d7; color: #742a2a; }
.card-body { margin-bottom: 12rpx; }
.line { font-size: 28rpx; color: #2d3748; display: block; }
.line.small { font-size: 24rpx; color: #718096; margin-top: 4rpx; }
.card-foot { display: flex; justify-content: space-between; font-size: 24rpx; color: #a0aec0; margin-top: 20rpx; }
.leader { color: #718096; }
.card-actions { display: flex; gap: 24rpx; margin-top: 16rpx; padding-top: 16rpx; border-top: 2rpx solid #f0f0f0; }
.action-btn { font-size: 26rpx; padding: 8rpx 16rpx; }
.action-btn.reject, .action-btn.cancel { color: #e53e3e; }
.empty { text-align: center; color: #a0aec0; padding: 80rpx 0; font-size: 28rpx; }
</style>
