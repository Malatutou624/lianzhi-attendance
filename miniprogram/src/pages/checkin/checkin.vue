<template>
  <view class="page">
    <!-- 顶部信息 -->
    <view class="header">
      <text class="title">{{ venue?.name || '加载中...' }}</text>
      <text class="subtitle" v-if="venue">{{ venue.address || '联智考勤打卡点' }}</text>
      <text class="emp-badge" v-if="emp">👤 {{ emp.name }}（{{ emp.empNo }}）· {{ emp.dept || '' }}</text>
      <text class="emp-badge warn" v-else>⚠️ 未绑定员工，请先采集</text>
    </view>

    <!-- 时钟 -->
    <view class="clock">
      <text class="time">{{ nowTime }}</text>
      <text class="date">{{ nowDate }}</text>
    </view>

    <!-- ========== AI 人脸识别区域（核心效果） ========== -->
    <view class="face-scanner-wrap" @click="choosePhoto">
      <!-- 外层光晕圈（呼吸动画） -->
      <view class="face-halo" :class="haloClass"></view>

      <!-- 圆形裁剪容器 -->
      <view class="face-circle">
        <!-- 摄像头/照片内容（圆形裁剪） -->
        <image
          v-if="photoPreview"
          :src="photoPreview"
          class="face-photo"
          mode="aspectFill"
        />
        <view v-else class="face-camera-placeholder">
          <text class="camera-icon">📷</text>
          <text class="camera-text">点击开启摄像头</text>
        </view>

        <!-- 扫描线（上传中 + 比对中时显示） -->
        <view v-if="scanning" class="scan-line"></view>

        <!-- 四角 L 型定位框 -->
        <view class="corner corner-tl"></view>
        <view class="corner corner-tr"></view>
        <view class="corner corner-bl"></view>
        <view class="corner corner-br"></view>
      </view>

      <!-- 比对成功遮罩 -->
      <view v-if="matchResult === 'success'" class="result-overlay success">
        <view class="check-circle">
          <text class="check-icon">✓</text>
        </view>
      </view>

      <!-- 比对失败遮罩 -->
      <view v-if="matchResult === 'fail'" class="result-overlay fail">
        <view class="cross-circle">
          <text class="cross-icon">✗</text>
        </view>
      </view>
    </view>

    <!-- 状态文字区 -->
    <view class="face-status-bar">
      <template v-if="matchResult === 'success'">
        <text class="status-title success">识别成功</text>
        <text class="status-sub">{{ emp?.name || '本人' }} · 正在打卡...</text>
      </template>
      <template v-else-if="matchResult === 'fail'">
        <text class="status-title fail">识别未通过</text>
        <text class="status-sub">{{ matchReason }}，请本人正对镜头重新拍照</text>
        <text class="retry-btn" @click.stop="resetPhoto">重新拍照</text>
      </template>
      <template v-else-if="faceStatus === 'uploading'">
        <text class="status-title scanning">正在上传照片...</text>
        <text class="status-sub">请保持不动</text>
      </template>
      <template v-else-if="submitting">
        <text class="status-title scanning">AI 比对中...</text>
        <text class="status-sub">正在进行人脸识别比对</text>
      </template>
      <template v-else-if="photoPreview && photoUrl">
        <text class="status-title ready">照片已就绪</text>
        <text class="status-sub">请点击下方打卡按钮</text>
      </template>
      <template v-else>
        <text class="status-title idle">点击上方区域开启识别</text>
        <text class="status-sub">请正对镜头、光线充足</text>
      </template>
    </view>

    <!-- 定位状态 -->
    <text class="loc-status" :class="locOk ? 'ok' : 'warn'">{{ locText }}</text>

    <!-- 打卡按钮 -->
    <button
      class="btn-checkin"
      :class="{ 'btn-success': matchResult === 'success' }"
      :disabled="submitting || matchResult === 'fail' || !photoUrl"
      @click="doCheckin"
    >
      {{ submitting ? '识别中...' : (matchResult === 'success' ? '打卡成功 ✓' : (photoUrl ? '开始打卡' : '请先拍照')) }}
    </button>

    <text class="debug-link" @click="useDebugLocation">定位不准？点这里使用演示定位</text>

    <!-- 打卡结果弹窗 -->
    <view v-if="result" class="result-mask" @click="result = null">
      <view class="result-card">
        <text class="result-icon">{{ result.status === 'normal' ? '🎉' : '⚠️' }}</text>
        <text class="result-title">{{ result.punchType }}打卡成功</text>
        <text class="result-line">{{ result.empName }} · {{ result.time }}</text>
        <text class="result-line">状态：{{ result.statusText }}（距场所 {{ result.distance }} 米）</text>
        <text class="result-close">点击任意处关闭</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow, onHide } from '@dcloudio/uni-app'
import { http } from '@/utils/request'

const venue = ref<any>(null)
const emp = ref<any>(null)
const photoPreview = ref('')
const photoUrl = ref('')
const faceStatus = ref<'pending' | 'uploading' | 'ready'>('pending')
const matchResult = ref<'success' | 'fail' | null>(null)
const matchReason = ref('')
const locText = ref('正在获取定位...')
const locOk = ref(false)
const longitude = ref<number | null>(null)
const latitude = ref<number | null>(null)
const submitting = ref(false)
const result = ref<any>(null)

const nowTime = ref('')
const nowDate = ref('')
let timer: any = null
let onShowCount = 0

// 扫描动画 + 呼吸光晕
const scanning = computed(() =>
  faceStatus.value === 'uploading' || submitting.value
)
const haloClass = computed(() => {
  if (matchResult.value === 'success') return 'halo-success'
  if (matchResult.value === 'fail') return 'halo-fail'
  if (scanning.value) return 'halo-scanning'
  return 'halo-idle'
})

function tick() {
  const d = new Date()
  nowTime.value = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
  nowDate.value = `${d.getMonth() + 1}月${d.getDate()}日 星期${'日一二三四五六'[d.getDay()]}`
}

onShow(async () => {
  tick()
  if (!timer) timer = setInterval(tick, 1000)

  onShowCount++
  if (onShowCount === 1) {
    photoPreview.value = ''
    photoUrl.value = ''
    faceStatus.value = 'pending'
    matchResult.value = null
    matchReason.value = ''
  }

  try {
    const list = await http.get<any[]>('/h5/venue/list', undefined, { loading: false })
    if (list && list.length > 0) {
      let vid = Number(uni.getStorageSync('checkinVenueId') || 0)
      if (!vid) vid = list[0].id
      const v = list.find((x: any) => x.id === vid) || list[0]
      venue.value = v
      uni.setStorageSync('checkinVenueId', v.id)
    }
  } catch {}

  try {
    const info = await http.get<any>('/h5/auth/info', undefined, { loading: false })
    emp.value = info?.employee || null
  } catch {}

  getLocation()
})

onHide(() => {
  if (timer) { clearInterval(timer); timer = null }
  onShowCount = 0
})

function getLocation() {
  uni.getLocation({
    type: 'gcj02', isHighAccuracy: true, timeout: 8000,
    success: (res) => {
      longitude.value = res.longitude
      latitude.value = res.latitude
      locOk.value = true
      locText.value = '定位成功 ✓'
    },
    fail: () => {
      locOk.value = false
      locText.value = '定位失败，已使用演示定位'
      useDebugLocation()
    }
  })
}

function useDebugLocation() {
  if (!venue.value?.longitude) return
  longitude.value = venue.value.longitude + (Math.random() - 0.5) * 0.0005
  latitude.value = venue.value.latitude + (Math.random() - 0.5) * 0.0005
  locOk.value = true
}

function choosePhoto() {
  if (submitting.value) return
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['camera'],
    success: (res) => {
      matchResult.value = null
      matchReason.value = ''
      const tempPath = res.tempFilePaths[0]
      photoPreview.value = tempPath
      uploadPhoto(tempPath)
    }
  })
}

function uploadPhoto(filePath: string) {
  faceStatus.value = 'uploading'
  const token = uni.getStorageSync('h5Token') || ''
  const url = (import.meta.env.BACKEND_BASE || 'http://127.0.0.1:8080') + '/upload'

  uni.uploadFile({
    url, filePath, name: 'file',
    header: { 'Authorization': 'Bearer ' + token },
    success: (res) => {
      try {
        const data = JSON.parse(res.data)
        if (res.statusCode === 200 && data.code === 200) {
          photoUrl.value = data.data
          faceStatus.value = 'ready'
          // 上传后自动触发打卡（含后端人脸比对）
          autoCheckin()
        } else {
          faceStatus.value = 'pending'
          uni.showToast({ title: data.msg || '上传失败', icon: 'none' })
        }
      } catch {
        faceStatus.value = 'pending'
        uni.showToast({ title: '上传响应异常', icon: 'none' })
      }
    },
    fail: (err) => {
      faceStatus.value = 'pending'
      console.error('[uploadPhoto] fail:', err)
      uni.showToast({ title: '照片上传失败', icon: 'none' })
    }
  })
}

// 拍照+上传成功后自动打卡（触发后端人脸比对）
async function autoCheckin() {
  if (!emp.value) { matchResult.value = 'fail'; matchReason.value = '请先完成信息采集'; return }
  if (!locOk.value || !longitude.value) { matchResult.value = 'fail'; matchReason.value = '定位失败'; return }
  if (!venue.value) { matchResult.value = 'fail'; matchReason.value = '场所未加载'; return }

  submitting.value = true
  try {
    await http.post('/h5/checkin', {
      venueId: venue.value.id,
      longitude: longitude.value,
      latitude: latitude.value,
      photoUrl: photoUrl.value,
      deviceInfo: 'WeChatMiniProgram'
    })
    // 后端返回 200 = 人脸比对通过 + 打卡成功
    matchResult.value = 'success'
    matchReason.value = ''
  } catch (err: any) {
    // 后端返回 401/业务错误 = 人脸比对失败或其他原因
    matchResult.value = 'fail'
    matchReason.value = err?.msg || '网络错误'
  } finally {
    submitting.value = false
  }
}

// 手动点打卡按钮（matchResult==='success' 时再点一次看详情）
async function doCheckin() {
  if (matchResult.value === 'success') {
    uni.showToast({ title: '已打卡 ✓', icon: 'success' })
    return
  }
  await autoCheckin()
}

// 重新拍照
function resetPhoto() {
  matchResult.value = null
  matchReason.value = ''
  photoPreview.value = ''
  photoUrl.value = ''
  faceStatus.value = 'pending'
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #0f2027, #203a43, #2c5364);
  padding: 40rpx 32rpx;
  color: #fff;
}
.header { text-align: center; }
.title { font-size: 40rpx; font-weight: bold; display: block; }
.subtitle { font-size: 24rpx; opacity: 0.75; margin-top: 6rpx; display: block; }
.clock { text-align: center; margin: 40rpx 0 32rpx; }
.time { font-size: 80rpx; font-weight: bold; letter-spacing: 4rpx; display: block; font-family: monospace; }
.date { font-size: 26rpx; opacity: 0.8; margin-top: 4rpx; display: block; }

/* ========== AI 人脸识别区域 ========== */
.face-scanner-wrap {
  position: relative;
  width: 420rpx;
  height: 420rpx;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 呼吸光晕圈 */
.face-halo {
  position: absolute;
  width: 100%; height: 100%;
  border-radius: 50%;
  transition: all 0.4s ease;
  &.halo-idle {
    border: 4rpx solid rgba(99, 179, 237, 0.4);
    box-shadow: 0 0 20rpx rgba(99, 179, 237, 0.15);
  }
  &.halo-scanning {
    border: 4rpx solid rgba(72, 187, 120, 0.7);
    box-shadow: 0 0 40rpx rgba(72, 187, 120, 0.3);
    animation: haloPulse 1.5s ease-in-out infinite;
  }
  &.halo-success {
    border: 6rpx solid #48bb78;
    box-shadow: 0 0 60rpx rgba(72, 187, 120, 0.6);
  }
  &.halo-fail {
    border: 6rpx solid #f56565;
    box-shadow: 0 0 60rpx rgba(245, 101, 101, 0.5);
    animation: shake 0.4s ease-in-out;
  }
}

@keyframes haloPulse {
  0%, 100% { transform: scale(1); opacity: 0.9; }
  50%      { transform: scale(1.04); opacity: 1; }
}
@keyframes shake {
  0%, 100% { transform: translateX(0); }
  20% { transform: translateX(-12rpx); }
  40% { transform: translateX(12rpx); }
  60% { transform: translateX(-8rpx); }
  80% { transform: translateX(8rpx); }
}

/* 圆形裁剪 */
.face-circle {
  position: relative;
  width: 320rpx; height: 320rpx;
  border-radius: 50%;
  overflow: hidden;
  background: #1a202c;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 3rpx solid rgba(255,255,255,0.15);
}
.face-photo { width: 100%; height: 100%; }
.face-camera-placeholder {
  text-align: center;
  color: rgba(255,255,255,0.5);
}
.camera-icon { font-size: 80rpx; display: block; margin-bottom: 8rpx; }
.camera-text { font-size: 24rpx; }

/* 扫描线 */
.scan-line {
  position: absolute;
  left: 0; right: 0;
  height: 4rpx;
  top: 0;
  background: linear-gradient(90deg, transparent, #48bb78, transparent);
  box-shadow: 0 0 20rpx #48bb78;
  animation: scanMove 2.2s ease-in-out infinite;
}
@keyframes scanMove {
  0%   { top: 0; opacity: 1; }
  50%  { top: 100%; opacity: 1; }
  51%  { top: 100%; opacity: 0; }
  100% { top: 0; opacity: 0; }
}

/* 四角 L 型定位框 */
.corner {
  position: absolute;
  width: 32rpx; height: 32rpx;
  border-color: #48bb78;
  border-style: solid;
  border-width: 0;
}
.corner-tl { top: 14rpx; left: 14rpx; border-top-width: 4rpx; border-left-width: 4rpx; border-top-left-radius: 8rpx; }
.corner-tr { top: 14rpx; right: 14rpx; border-top-width: 4rpx; border-right-width: 4rpx; border-top-right-radius: 8rpx; }
.corner-bl { bottom: 14rpx; left: 14rpx; border-bottom-width: 4rpx; border-left-width: 4rpx; border-bottom-left-radius: 8rpx; }
.corner-br { bottom: 14rpx; right: 14rpx; border-bottom-width: 4rpx; border-right-width: 4rpx; border-bottom-right-radius: 8rpx; }

/* 成功/失败遮罩 */
.result-overlay {
  position: absolute; inset: 0;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  pointer-events: none;
}
.check-circle {
  width: 160rpx; height: 160rpx;
  border-radius: 50%;
  background: rgba(72, 187, 120, 0.95);
  display: flex; align-items: center; justify-content: center;
  animation: popIn 0.5s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.check-icon { font-size: 100rpx; color: #fff; font-weight: bold; }
.cross-circle {
  width: 160rpx; height: 160rpx;
  border-radius: 50%;
  background: rgba(245, 101, 101, 0.95);
  display: flex; align-items: center; justify-content: center;
  animation: popIn 0.5s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.cross-icon { font-size: 100rpx; color: #fff; font-weight: bold; }
@keyframes popIn {
  0%   { transform: scale(0); opacity: 0; }
  100% { transform: scale(1); opacity: 1; }
}

/* ========== 状态文字区 ========== */
.face-status-bar {
  text-align: center;
  margin: 36rpx 0 20rpx;
  min-height: 100rpx;
}
.status-title { font-size: 32rpx; font-weight: bold; display: block; }
.status-title.success { color: #48bb78; }
.status-title.fail    { color: #f56565; }
.status-title.scanning { color: #63b3ed; }
.status-title.ready   { color: #68d391; }
.status-title.idle    { color: #a0aec0; }
.status-sub { font-size: 24rpx; opacity: 0.7; margin-top: 8rpx; display: block; }
.retry-btn {
  display: inline-block;
  margin-top: 12rpx;
  padding: 10rpx 32rpx;
  background: rgba(245, 101, 101, 0.25);
  border: 2rpx solid #f56565;
  border-radius: 40rpx;
  color: #f56565;
  font-size: 26rpx;
}

.loc-status { text-align: center; font-size: 24rpx; margin-bottom: 32rpx; display: block; }
.loc-status.ok   { color: #68d391; }
.loc-status.warn { color: #fbd38d; }

/* 打卡按钮 */
.btn-checkin {
  display: block;
  width: 300rpx; height: 300rpx;
  margin: 0 auto;
  border-radius: 50%;
  border: 10rpx solid rgba(255,255,255,0.3);
  background: linear-gradient(145deg, #3182ce, #2b6cb0);
  color: #fff;
  font-size: 48rpx;
  font-weight: bold;
  line-height: 280rpx;
  padding: 0;
  box-shadow: 0 10rpx 30rpx rgba(49, 130, 206, 0.4);
}
.btn-checkin[disabled] {
  background: #4a5568;
  border-color: rgba(255,255,255,0.1);
  box-shadow: none;
}
.btn-checkin.btn-success {
  background: linear-gradient(145deg, #48bb78, #38a169);
  border-color: rgba(72, 187, 120, 0.5);
  box-shadow: 0 10rpx 30rpx rgba(72, 187, 120, 0.5);
}

.debug-link {
  display: block;
  text-align: center;
  font-size: 22rpx;
  color: rgba(255,255,255,0.4);
  margin-top: 32rpx;
}

.emp-badge { margin-top: 10rpx; font-size: 22rpx; opacity: 0.85; display: block; }
.emp-badge.warn { color: #fbd38d; }

/* 打卡结果弹窗 */
.result-mask {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.6);
  display: flex; align-items: center; justify-content: center;
}
.result-card {
  background: #fff;
  color: #2d3748;
  border-radius: 28rpx;
  padding: 56rpx 72rpx;
  text-align: center;
}
.result-icon { font-size: 88rpx; display: block; }
.result-title { font-size: 40rpx; font-weight: bold; margin: 20rpx 0; display: block; }
.result-line { font-size: 28rpx; color: #4a5568; margin-top: 12rpx; display: block; }
.result-close { font-size: 24rpx; color: #a0aec0; margin-top: 32rpx; display: block; }
</style>
