<template>
  <!-- 手机打卡页：员工扫场所二维码后打开，路径 /h5/checkin?venueId=1 -->
  <div class="h5-page">
    <!-- 顶部：场所名称 -->
    <div class="header">
      <div class="title">{{ venue ? venue.name : '加载中...' }}</div>
      <div class="subtitle" v-if="venue">{{ venue.address || '联智考勤打卡点' }}</div>
    </div>

    <!-- 当前时间（每秒刷新） -->
    <div class="clock">
      <div class="time">{{ nowTime }}</div>
      <div class="date">{{ nowDate }}</div>
    </div>

    <!-- 工号输入：打过一次卡后会记住，不用再输 -->
    <div class="form">
      <input v-model="empNo" class="input" placeholder="请输入工号" maxlength="20" />
    </div>

    <!-- 拍照区：用label包裹input，点击更可靠 -->
    <label class="photo-area" for="checkinPhotoInput">
      <img v-if="photoPreview" :src="photoPreview" class="photo-preview" />
      <div v-else class="photo-tip">
        <div class="camera-icon">📷</div>
        <div>点击拍张现场照（可选）</div>
      </div>
      <input id="checkinPhotoInput" ref="fileInput" type="file" accept="image/*" capture="user" style="display:none" @change="onPhotoTaken" />
    </label>

    <!-- 定位状态提示 -->
    <div class="loc-status" :class="locOk ? 'ok' : 'warn'">{{ locText }}</div>

    <!-- 打卡大按钮 -->
    <button class="btn-checkin" :disabled="submitting" @click="doCheckin">
      {{ submitting ? '打卡中...' : '打卡' }}
    </button>

    <!-- 定位不准或失败时的手动兜底（实训演示用，随时可点） -->
    <div class="debug-link" @click="() => useDebugLocation(false)">定位不准/失败？点这里使用演示定位</div>

    <!-- 打卡结果弹层 -->
    <div v-if="result" class="result-mask" @click="result = null">
      <div class="result-card">
        <div class="result-icon">{{ result.status === 'normal' ? '✅' : '⚠️' }}</div>
        <div class="result-title">{{ result.punchType }}打卡成功</div>
        <div class="result-line">{{ result.empName }} · {{ result.time }}</div>
        <div class="result-line">状态：{{ result.statusText }}（距场所 {{ result.distance }} 米）</div>
        <div class="result-close">点击任意处关闭</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import { wgs84ToGcj02 } from '../../utils/coordTransform'

// ---------- 页面数据 ----------
const route = useRoute()
const venueId = route.query.venueId        // 从二维码链接里取场所ID
const venue = ref(null)                    // 场所信息
const empNo = ref(localStorage.getItem('h5_empNo') || '')  // 记住上次输入的工号
const photoPreview = ref('')               // 照片预览图（base64）
const photoUrl = ref('')                   // 照片上传到服务器后的 URL
const locText = ref('正在获取定位...')
const locOk = ref(false)
const longitude = ref(null)
const latitude = ref(null)
const submitting = ref(false)
const result = ref(null)                   // 打卡成功后的结果
const fileInput = ref(null)

// ---------- 时钟：每秒刷新 ----------
const nowTime = ref('')
const nowDate = ref('')
let timer = null
function tick() {
  const d = new Date()
  nowTime.value = d.toTimeString().substring(0, 8)                    // 14:30:05
  nowDate.value = `${d.getMonth() + 1}月${d.getDate()}日 星期${'日一二三四五六'[d.getDay()]}`
}

// ---------- 打开页面时：加载场所信息 + 启动时钟 + 请求定位 ----------
onMounted(async () => {
  tick()
  timer = setInterval(tick, 1000)
  // 加载场所信息
  try {
    const res = await axios.get('/api/h5/venue/' + venueId)
    if (res.data.code === 200) {
      venue.value = res.data.data
    } else {
      alert(res.data.msg)
    }
  } catch (e) {
    alert('场所加载失败，请确认二维码链接是否正确')
  }
  // 请求浏览器定位
  getLocation()
})
onUnmounted(() => clearInterval(timer))

// ---------- 浏览器定位 ----------
function getLocation() {
  if (!navigator.geolocation) {
    locText.value = '当前浏览器不支持定位'
    return
  }
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      // 浏览器定位返回 WGS-84（GPS标准坐标），场所坐标是高德 GCJ-02
      // 必须转换，否则距离会有几十米偏差
      const [lng, lat] = wgs84ToGcj02(pos.coords.longitude, pos.coords.latitude)
      longitude.value = lng
      latitude.value = lat
      locOk.value = true
      locText.value = '定位成功 ✓'
    },
    (err) => {
      locOk.value = false
      locText.value = '定位失败：' + err.message
      // 定位失败时自动用场所坐标兜底，避免员工卡在这一步没法继续打卡
      useDebugLocation(true)
    },
    { enableHighAccuracy: true, timeout: 8000 }
  )
}

// ---------- 演示兜底：用场所坐标加一点随机偏移 ----------
// 实训演示时定位常常失败（非 https 访问、电脑没定位服务），用这个保证流程能走完
// auto = true 表示是定位失败后自动触发的，提示文案不一样
function useDebugLocation(auto) {
  if (!venue.value || !venue.value.longitude) {
    if (auto) {
      locText.value = '定位失败，且场所还没有配置坐标，请联系管理员'
    } else {
      alert('场所还没有配置坐标，请先让管理员完善')
    }
    return
  }
  longitude.value = venue.value.longitude + (Math.random() - 0.5) * 0.0005  // 偏移约 ±25米
  latitude.value = venue.value.latitude + (Math.random() - 0.5) * 0.0005
  locOk.value = true
  locText.value = auto ? '定位失败，已自动使用演示定位（场所坐标 ±25 米）' : '已使用演示定位（场所坐标 ±25 米）'
}

// ---------- 拍照 ----------
function takePhoto() {
  fileInput.value.click()   // 触发隐藏的文件选择框，手机上会打开相机
}
async function onPhotoTaken(e) {
  const file = e.target.files[0]
  if (!file) return
  // 本地预览：用 FileReader 读成 base64 显示在页面上
  const reader = new FileReader()
  reader.onload = (ev) => { photoPreview.value = ev.target.result }
  reader.readAsDataURL(file)
  // 上传到服务器，拿到可访问的 URL
  const form = new FormData()
  form.append('file', file)
  try {
    const res = await axios.post('/api/upload', form)
    if (res.data.code === 200) {
      photoUrl.value = res.data.data
    } else {
      // 后端返回失败时必须提示，避免照片没传上去却没有任何反馈
      alert('照片上传失败：' + res.data.msg)
    }
  } catch (err) {
    alert('照片上传失败：' + err.message)
  }
}

// ---------- 提交打卡 ----------
async function doCheckin() {
  if (!empNo.value.trim()) { alert('请输入工号'); return }
  if (!locOk.value) { alert('还没有定位成功'); return }
  submitting.value = true
  try {
    const res = await axios.post('/api/h5/checkin', {
      venueId: Number(venueId),
      empNo: empNo.value.trim(),
      longitude: longitude.value,
      latitude: latitude.value,
      photoUrl: photoUrl.value,
      deviceInfo: navigator.userAgent
    })
    if (res.data.code === 200) {
      result.value = res.data.data                       // 弹出成功卡片
      localStorage.setItem('h5_empNo', empNo.value.trim()) // 记住工号，下次免输
    } else {
      alert(res.data.msg)                                // 失败原因直接弹出（超范围/重复打卡等）
    }
  } catch (err) {
    alert('打卡失败：' + err.message)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
/* 手机端页面：宽度100%，蓝色主题 */
.h5-page { min-height: 100vh; background: linear-gradient(180deg, #1a365d, #2b6cb0); padding: 24px 16px; box-sizing: border-box; color: #fff; }
.header { text-align: center; margin-top: 12px; }
.title { font-size: 22px; font-weight: bold; }
.subtitle { font-size: 13px; opacity: 0.8; margin-top: 4px; }
.clock { text-align: center; margin: 28px 0; }
.time { font-size: 46px; font-weight: bold; letter-spacing: 2px; }
.date { font-size: 14px; opacity: 0.8; margin-top: 4px; }
.form { margin: 0 8px 16px; }
.input { width: 100%; padding: 14px; font-size: 17px; border: none; border-radius: 10px; box-sizing: border-box; outline: none; }
.photo-area { margin: 0 8px 16px; height: 130px; background: rgba(255,255,255,0.15); border-radius: 10px; display: flex; align-items: center; justify-content: center; overflow: hidden; }
.photo-tip { text-align: center; opacity: 0.8; font-size: 13px; }
.camera-icon { font-size: 30px; margin-bottom: 4px; }
.photo-preview { width: 100%; height: 100%; object-fit: cover; }
.loc-status { text-align: center; font-size: 13px; margin-bottom: 20px; }
.loc-status.ok { color: #9ae6b4; }
.loc-status.warn { color: #fbd38d; }
.btn-checkin { display: block; width: 180px; height: 180px; margin: 0 auto; border-radius: 50%; border: 6px solid rgba(255,255,255,0.4); background: #48bb78; color: #fff; font-size: 30px; font-weight: bold; }
.btn-checkin:disabled { background: #a0aec0; }
.debug-link { text-align: center; font-size: 12px; color: #fbd38d; margin-top: 18px; text-decoration: underline; }
.result-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; }
.result-card { background: #fff; color: #2d3748; border-radius: 14px; padding: 28px 36px; text-align: center; }
.result-icon { font-size: 44px; }
.result-title { font-size: 20px; font-weight: bold; margin: 10px 0; }
.result-line { font-size: 14px; color: #4a5568; margin-top: 6px; }
.result-close { font-size: 12px; color: #a0aec0; margin-top: 16px; }
</style>
