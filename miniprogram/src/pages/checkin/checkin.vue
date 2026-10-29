<template>
  <view class="page">
    <view class="header">
      <text class="title">{{ venue?.name || '加载中...' }}</text>
      <text class="subtitle" v-if="venue">{{ venue.address || '联智考勤打卡点' }}</text>
      <text class="emp-badge" v-if="emp">👤 {{ emp.name }}（{{ emp.empNo }}）· {{ emp.dept || '' }}</text>
      <text class="emp-badge warn" v-else>⚠️ 未绑定员工，请先采集</text>
    </view>

    <view class="clock">
      <text class="time">{{ nowTime }}</text>
      <text class="date">{{ nowDate }}</text>
    </view>

    <view class="photo-area" @click="choosePhoto">
      <image v-if="photoPreview" :src="photoPreview" class="photo-preview" mode="aspectFill" />
      <view v-else class="photo-tip">
        <text class="camera-icon">📷</text>
        <text>点击拍张现场照（可选）</text>
      </view>
    </view>

    <text class="loc-status" :class="locOk ? 'ok' : 'warn'">{{ locText }}</text>

    <button class="btn-checkin" :disabled="submitting" @click="doCheckin">
      {{ submitting ? '打卡中...' : '打卡' }}
    </button>

    <text class="debug-link" @click="useDebugLocation">定位不准？点这里使用演示定位</text>

    <view v-if="result" class="result-mask" @click="result = null">
      <view class="result-card">
        <text class="result-icon">{{ result.status === 'normal' ? '✅' : '⚠️' }}</text>
        <text class="result-title">{{ result.punchType }}打卡成功</text>
        <text class="result-line">{{ result.empName }} · {{ result.time }}</text>
        <text class="result-line">状态：{{ result.statusText }}（距场所 {{ result.distance }} 米）</text>
        <text class="result-close">点击任意处关闭</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow, onHide } from '@dcloudio/uni-app'
import { http } from '@/utils/request'

const venue = ref<any>(null)
const emp = ref<any>(null)
const photoPreview = ref('')
const photoUrl = ref('')
const locText = ref('正在获取定位...')
const locOk = ref(false)
const longitude = ref<number | null>(null)
const latitude = ref<number | null>(null)
const submitting = ref(false)
const result = ref<any>(null)

const nowTime = ref('')
const nowDate = ref('')
let timer: any = null

function tick() {
  const d = new Date()
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  const ss = String(d.getSeconds()).padStart(2, '0')
  nowTime.value = `${hh}:${mm}:${ss}`
  nowDate.value = `${d.getMonth() + 1}月${d.getDate()}日 星期${'日一二三四五六'[d.getDay()]}`
}

onShow(async () => {
  tick()
  if (!timer) timer = setInterval(tick, 1000)

  // 加载场所（用 venueId 参数或默认第一个）
  try {
    const list = await http.get<any[]>('/h5/venue/list', undefined, { loading: false })
    if (list && list.length > 0) {
      // 从 storage 读 venueId，没有就用第一个
      let vid = Number(uni.getStorageSync('checkinVenueId') || 0)
      if (!vid) vid = list[0].id
      const v = list.find((x: any) => x.id === vid) || list[0]
      venue.value = v
      uni.setStorageSync('checkinVenueId', v.id)
    }
  } catch (e) {
    console.error('场所加载失败', e)
  }

  // 加载员工信息
  try {
    const info = await http.get<any>('/h5/auth/info', undefined, { loading: false })
    emp.value = info?.employee || null
  } catch {}

  // 获取定位
  getLocation()
})

onHide(() => {
  if (timer) { clearInterval(timer); timer = null }
})

function getLocation() {
  uni.getLocation({
    type: 'gcj02',
    isHighAccuracy: true,
    timeout: 8000,
    success: (res) => {
      longitude.value = res.longitude
      latitude.value = res.latitude
      locOk.value = true
      locText.value = '定位成功 ✓'
    },
    fail: (err) => {
      locOk.value = false
      locText.value = '定位失败：' + (err.errMsg || '未知')
      useDebugLocation()
    }
  })
}

function useDebugLocation() {
  if (!venue.value?.longitude) {
    locText.value = '场所未配置坐标，无法演示定位'
    return
  }
  longitude.value = venue.value.longitude + (Math.random() - 0.5) * 0.0005
  latitude.value = venue.value.latitude + (Math.random() - 0.5) * 0.0005
  locOk.value = true
  locText.value = '已使用演示定位（场所坐标 ±25 米）'
}

function choosePhoto() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['camera', 'album'],
    success: (res) => {
      const tempPath = res.tempFilePaths[0]
      photoPreview.value = tempPath
      uploadPhoto(tempPath)
    }
  })
}

function uploadPhoto(filePath: string) {
  const token = uni.getStorageSync('h5Token') || ''
  uni.uploadFile({
    url: (import.meta.env.BACKEND_BASE || 'http://127.0.0.1:8080') + '/upload',
    filePath,
    name: 'file',
    header: { token },
    success: (res) => {
      try {
        const data = JSON.parse(res.data)
        if (data.code === 200) photoUrl.value = data.data
      } catch {}
    },
    fail: () => {}
  })
}

async function doCheckin() {
  if (!emp.value) return uni.showToast({ title: '请先完成信息采集', icon: 'none' })
  if (!locOk.value || !longitude.value) return uni.showToast({ title: '还没有定位成功', icon: 'none' })
  if (!venue.value) return uni.showToast({ title: '场所未加载', icon: 'none' })

  submitting.value = true
  try {
    const res = await http.post('/h5/checkin', {
      venueId: venue.value.id,
      longitude: longitude.value,
      latitude: latitude.value,
      photoUrl: photoUrl.value,
      deviceInfo: 'WeChatMiniProgram'
    })
    result.value = res
  } catch (err: any) {
    uni.showToast({ title: err?.msg || '打卡失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; background: linear-gradient(180deg, #1a365d, #2b6cb0); padding: 48rpx 32rpx; color: #fff; }
.header { text-align: center; }
.title { font-size: 44rpx; font-weight: bold; display: block; }
.subtitle { font-size: 26rpx; opacity: 0.8; margin-top: 8rpx; display: block; }
.clock { text-align: center; margin: 56rpx 0; }
.time { font-size: 92rpx; font-weight: bold; letter-spacing: 4rpx; display: block; }
.date { font-size: 28rpx; opacity: 0.8; margin-top: 8rpx; display: block; }
.photo-area { height: 260rpx; background: rgba(255,255,255,0.15); border-radius: 20rpx; display: flex; align-items: center; justify-content: center; overflow: hidden; margin-bottom: 24rpx; }
.photo-tip { text-align: center; opacity: 0.8; font-size: 26rpx; display: flex; flex-direction: column; align-items: center; }
.camera-icon { font-size: 60rpx; margin-bottom: 8rpx; }
.photo-preview { width: 100%; height: 100%; }
.loc-status { text-align: center; font-size: 26rpx; margin-bottom: 40rpx; display: block; }
.loc-status.ok { color: #9ae6b4; }
.loc-status.warn { color: #fbd38d; }
.btn-checkin { display: block; width: 360rpx; height: 360rpx; margin: 0 auto; border-radius: 50%; border: 12rpx solid rgba(255,255,255,0.4); background: #48bb78; color: #fff; font-size: 60rpx; font-weight: bold; line-height: 336rpx; padding: 0; }
.btn-checkin[disabled] { background: #a0aec0; }
.debug-link { display: block; text-align: center; font-size: 24rpx; color: #fbd38d; margin-top: 36rpx; text-decoration: underline; }
.emp-badge { margin-top: 12rpx; font-size: 24rpx; opacity: 0.9; display: block; }
.emp-badge.warn { color: #fbd38d; }
.result-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; }
.result-card { background: #fff; color: #2d3748; border-radius: 28rpx; padding: 56rpx 72rpx; text-align: center; }
.result-icon { font-size: 88rpx; display: block; }
.result-title { font-size: 40rpx; font-weight: bold; margin: 20rpx 0; display: block; }
.result-line { font-size: 28rpx; color: #4a5568; margin-top: 12rpx; display: block; }
.result-close { font-size: 24rpx; color: #a0aec0; margin-top: 32rpx; display: block; }
</style>
