<template>
  <view class="page">
    <view class="card">
      <template v-if="form.type !== 'resign'">
        <view class="form-item">
          <text class="label">申请类型</text>
          <picker :range="typeLabels" @change="onTypeChange">
            <view class="picker">{{ getTypeLabel(form.type) || '请选择' }}</view>
          </picker>
        </view>

        <view v-if="form.type" class="form-item">
          <text class="label">开始日期</text>
          <picker mode="date" :value="form.startDate" @change="(e:any) => form.startDate = e.detail.value">
            <view class="picker">{{ form.startDate || '请选择日期' }}</view>
          </picker>
        </view>
        <view v-if="form.type" class="form-item">
          <text class="label">开始时间</text>
          <picker mode="time" :value="form.startTime" @change="(e:any) => form.startTime = e.detail.value">
            <view class="picker">{{ form.startTime || '请选择时间' }}</view>
          </picker>
        </view>
        <view v-if="form.type" class="form-item">
          <text class="label">结束日期</text>
          <picker mode="date" :value="form.endDate" @change="(e:any) => form.endDate = e.detail.value">
            <view class="picker">{{ form.endDate || '请选择日期' }}</view>
          </picker>
        </view>
        <view v-if="form.type" class="form-item">
          <text class="label">结束时间</text>
          <picker mode="time" :value="form.endTime" @change="(e:any) => form.endTime = e.detail.value">
            <view class="picker">{{ form.endTime || '请选择时间' }}</view>
          </picker>
        </view>

        <view v-if="form.type && form.startTime && form.endTime" class="form-item">
          <text class="label">时长（自动计算）</text>
          <view class="auto-num">{{ autoDuration }} 小时</view>
        </view>

        <view v-if="form.type" class="form-item">
          <text class="label">事由</text>
          <textarea v-model="form.reason" class="textarea" placeholder="请填写事由" />
        </view>
      </template>

      <template v-else>
        <view class="notice">⚠️ 离职申请提交后不可撤回，请谨慎操作</view>
        <view class="form-item">
          <text class="label">离职原因</text>
          <textarea v-model="form.resignReason" class="textarea" placeholder="请详细说明离职原因" />
        </view>
      </template>

      <view v-if="canSubmit" class="form-item">
        <text class="label">审批人</text>
        <view class="leader-info">
          <text v-if="leaderName">{{ leaderName }}</text>
          <text v-else class="no-leader">暂无审批人</text>
        </view>
      </view>

      <button v-if="canSubmit" class="btn-submit" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中...' : '提交申请' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { http } from '@/utils/request'

const emp = ref<any>(null)
const leaderName = ref('')
const leaderId = ref<any>(null)
const submitting = ref(false)

const typeOptions = [
  { value: 'leave', label: '事假' },
  { value: 'sick', label: '病假' },
  { value: 'annual', label: '年假' },
  { value: 'overtime', label: '加班' },
  { value: 'compensate', label: '调休' },
  { value: 'makeup', label: '补卡' },
  { value: 'resign', label: '离职' },
]
const typeLabels = typeOptions.map(o => o.label)

const form = reactive({
  type: '', startDate: '', startTime: '', endDate: '', endTime: '',
  reason: '', resignReason: ''
})

onShow(async () => {
  try {
    const info = await http.get<any>('/h5/auth/info', undefined, { loading: false })
    emp.value = info?.employee || null
    if (!emp.value) {
      uni.showModal({ title: '提示', content: '请先完成员工信息采集', showCancel: false,
        success: () => uni.navigateBack() })
      return
    }
  } catch {
    uni.showToast({ title: '请先登录', icon: 'none' })
    return
  }

  try {
    const approver = await http.get<any>('/h5/approval/approver', undefined, { loading: false })
    leaderName.value = approver?.leaderName || ''
    leaderId.value = approver?.leaderId || null
  } catch {}
})

function onTypeChange(e: any) {
  form.type = typeOptions[e.detail.value].value
}
function getTypeLabel(v: string) {
  const o = typeOptions.find(x => x.value === v)
  return o ? o.label : ''
}

const autoDuration = computed(() => {
  if (!form.startDate || !form.startTime || !form.endDate || !form.endTime) return '0'
  const start = new Date(form.startDate + 'T' + form.startTime)
  const end = new Date(form.endDate + 'T' + form.endTime)
  const ms = end.getTime() - start.getTime()
  if (ms <= 0) return '0'
  return (ms / 3600000).toFixed(2)
})

const canSubmit = computed(() => {
  if (!emp.value) return false
  if (form.type === 'resign') return (form.resignReason || '').trim().length > 0
  if (!form.type) return false
  if (!form.startDate || !form.startTime || !form.endDate || !form.endTime) return false
  return autoDuration.value !== '0' && (form.reason || '').trim().length > 0
})

async function submit() {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    const payload: any = { type: form.type, reason: form.reason, resignReason: form.resignReason }
    if (form.type !== 'resign') {
      payload.startTime = form.startDate + 'T' + form.startTime + ':00'
      payload.endTime = form.endDate + 'T' + form.endTime + ':00'
    }
    await http.post('/h5/approval/submit', payload)
    uni.showModal({ title: '成功', content: '提交成功！等待审批', showCancel: false,
      success: () => uni.navigateBack() })
  } catch (err: any) {
    uni.showToast({ title: err?.message || '提交失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; background: #f0f4f8; padding: 32rpx; padding-bottom: 80rpx; }
.card { background: #fff; border-radius: 24rpx; padding: 40rpx; box-shadow: 0 4rpx 12rpx rgba(0,0,0,0.05); }
.form-item { margin-bottom: 32rpx; }
.label { display: block; font-size: 28rpx; color: #4a5568; margin-bottom: 12rpx; font-weight: 500; }
.picker, .textarea {
  width: 100%; padding: 24rpx; font-size: 30rpx; border: 2rpx solid #e2e8f0; border-radius: 16rpx;
  box-sizing: border-box; background: #fff; color: #2d3748;
}
.textarea { height: 160rpx; }
.auto-num { padding: 24rpx; background: #ebf8ff; border-radius: 16rpx; color: #2b6cb0; font-weight: 500; font-size: 28rpx; }
.leader-info { padding: 24rpx; background: #f7fafc; border-radius: 16rpx; color: #2d3748; font-size: 28rpx; }
.no-leader { color: #e53e3e; }
.notice { background: #fff5f5; color: #c53030; padding: 24rpx; border-radius: 16rpx; font-size: 26rpx; margin-bottom: 32rpx; }
.btn-submit { width: 100%; padding: 28rpx; margin-top: 16rpx; border: none; border-radius: 20rpx; background: #2b6cb0; color: #fff; font-size: 30rpx; font-weight: bold; }
.btn-submit[disabled] { background: #a0aec0; }
</style>
