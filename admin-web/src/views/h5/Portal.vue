<template>
  <!-- H5 统一入口页：员工扫场所码后进入，选「信息采集」或「考勤打卡」，不用手输网址 -->
  <div class="h5-page">
    <div class="header">
      <div class="title">{{ venue ? venue.name : '联智智能考勤' }}</div>
      <div class="subtitle">{{ venue ? (venue.address || '请选择要办理的业务') : '请选择要办理的业务' }}</div>
    </div>

    <div v-if="error" class="error">{{ error }}</div>

    <div class="actions">
      <!-- 新员工：先采集 -->
      <button class="big-btn collect" @click="go('collect')">
        <div class="icon">👤</div>
        <div class="label">信息采集</div>
        <div class="desc">首次使用先录姓名、工号、人脸照片</div>
      </button>

      <!-- 已采集员工：直接打卡 -->
      <button class="big-btn checkin" @click="go('checkin')">
        <div class="icon">📍</div>
        <div class="label">考勤打卡</div>
        <div class="desc">定位 + 拍照，一键完成上下班打卡</div>
      </button>
    </div>

    <div class="footer">联智智能考勤 · 位置打卡</div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'

const route = useRoute()
const router = useRouter()
// 场所ID 从二维码链接 ?venueId=xx 里取
const venueId = route.query.venueId
const venue = ref(null)
const error = ref('')

onMounted(async () => {
  if (!venueId) {
    error.value = '缺少场所参数，请重新扫描场所二维码'
    return
  }
  try {
    const res = await axios.get('/api/h5/venue/' + venueId)
    if (res.data.code === 200) {
      venue.value = res.data.data
    } else {
      error.value = res.data.msg
    }
  } catch (e) {
    error.value = '场所信息加载失败，请确认手机和电脑在同一个 WiFi'
  }
})

// 带着 venueId 跳到具体页面
function go(page) {
  if (!venueId) {
    alert('缺少场所参数，请重新扫描场所码')
    return
  }
  router.push({ path: '/h5/' + page, query: { venueId } })
}
</script>

<style scoped>
.h5-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #1a365d, #2b6cb0);
  padding: 40px 20px;
  box-sizing: border-box;
  color: #fff;
}
.header { text-align: center; margin-bottom: 32px; }
.title { font-size: 24px; font-weight: bold; }
.subtitle { font-size: 14px; opacity: 0.85; margin-top: 8px; }

.actions { display: flex; flex-direction: column; gap: 20px; }

.big-btn {
  width: 100%;
  padding: 24px 20px;
  border: none;
  border-radius: 16px;
  text-align: left;
  color: #fff;
  display: flex;
  flex-direction: column;
  gap: 6px;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.18);
}
.big-btn.collect { background: #4c51bf; }
.big-btn.checkin { background: #38a169; }
.big-btn:active { transform: scale(0.98); }
.icon { font-size: 28px; }
.label { font-size: 21px; font-weight: bold; }
.desc { font-size: 13px; opacity: 0.85; }

.error {
  background: rgba(255, 255, 255, 0.15);
  border-radius: 10px;
  padding: 12px;
  font-size: 13px;
  text-align: center;
  margin-bottom: 20px;
}
.footer { text-align: center; font-size: 12px; opacity: 0.6; margin-top: 40px; }
</style>
