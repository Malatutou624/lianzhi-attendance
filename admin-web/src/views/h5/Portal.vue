<template>
  <!-- H5 统一入口页：员工扫场所码后进入 -->
  <div class="h5-page">
    <!-- 顶部：场所 + 账号信息 -->
    <div class="header">
      <div class="top-bar">
        <div class="title">{{ venue ? venue.name : '联智智能考勤' }}</div>
        <button v-if="account" class="logout-btn" @click="logout">退出</button>
      </div>
      <div class="subtitle">{{ venue ? (venue.address || '请选择要办理的业务') : '请选择要办理的业务' }}</div>
      <div v-if="account" class="account-badge">
        👤 {{ account.username }}
        <span v-if="account.employee" class="bound">· {{ account.employee.name }}（{{ account.employee.empNo }}）</span>
        <span v-else class="unbound">· 尚未采集</span>
      </div>
    </div>

    <div v-if="error" class="error">{{ error }}</div>

    <div class="actions">
      <!-- 信息采集：未绑定时 active，已绑定时 disabled -->
      <button class="big-btn collect" :class="{ disabled: account?.empId }" @click="go('collect')">
        <div class="icon">👤</div>
        <div class="label">信息采集</div>
        <div class="desc">{{ account?.empId ? '已完成采集' : '首次使用先录姓名、工号、人脸照片' }}</div>
      </button>

      <!-- 考勤打卡：已绑定时 active，未绑定时 disabled -->
      <button class="big-btn checkin" :class="{ disabled: !account?.empId }" @click="go('checkin')">
        <div class="icon">📍</div>
        <div class="label">考勤打卡</div>
        <div class="desc">{{ account?.empId ? '定位 + 拍照，一键完成上下班打卡' : '请先完成信息采集' }}</div>
      </button>

      <!-- 个人考勤：始终可进（但未绑定只能看空数据） -->
      <button class="big-btn attend" @click="goHome">
        <div class="icon">📋</div>
        <div class="label">个人考勤</div>
        <div class="desc">考勤日历 · 请假加班申请 · 审批进度</div>
      </button>
    </div>

    <div class="footer">联智智能考勤 · 位置打卡</div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../../api/request'

const route = useRoute()
const router = useRouter()
const venueId = route.query.venueId
const venue = ref(null)
const account = ref(null)
const error = ref('')

onMounted(async () => {
  // 加载场所
  if (!venueId) {
    error.value = '缺少场所参数，请重新扫描场所二维码'
  } else {
    try {
      const res = await request.get('/h5/venue/' + venueId)
      venue.value = res.data
    } catch (e) {
      error.value = '场所信息加载失败'
    }
  }
  // 加载当前账号信息（含绑定状态）
  try {
    const res = await request.get('/h5/auth/info')
    account.value = res.data
  } catch (e) {
    // 路由守卫应该已经拦了，这里兜底
  }
})

function go(page) {
  if (!venueId) { alert('请重新扫描场所码'); return }
  if (page === 'checkin' && !account.value?.empId) { alert('请先完成信息采集'); return }
  router.push({ path: '/h5/' + page, query: { venueId } })
}

function goHome() {
  router.push({ path: '/h5/home' })
}

function logout() {
  if (!confirm('确定要退出登录吗？')) return
  localStorage.removeItem('h5Token')
  window.location.href = '/h5/login'
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
.header { text-align: center; margin-bottom: 24px; }
.top-bar { display: flex; justify-content: center; align-items: center; gap: 12px; position: relative; }
.title { font-size: 24px; font-weight: bold; }
.logout-btn { position: absolute; right: 0; background: rgba(255,255,255,0.2); border: none; color: #fff; padding: 4px 12px; border-radius: 14px; font-size: 12px; cursor: pointer; }
.subtitle { font-size: 14px; opacity: 0.85; margin-top: 8px; }
.account-badge { margin-top: 10px; font-size: 12px; opacity: 0.85; }
.account-badge .bound { color: #9ae6b4; }
.account-badge .unbound { color: #fbd38d; }

.actions { display: flex; flex-direction: column; gap: 16px; }

.big-btn {
  width: 100%;
  padding: 22px 18px;
  border: none;
  border-radius: 16px;
  text-align: left;
  color: #fff;
  display: flex;
  flex-direction: column;
  gap: 4px;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.18);
  cursor: pointer;
}
.big-btn.collect { background: #4c51bf; }
.big-btn.checkin { background: #38a169; }
.big-btn.attend { background: #d69e2e; }
.big-btn.disabled { opacity: 0.45; cursor: not-allowed; }
.big-btn:active:not(.disabled) { transform: scale(0.98); }
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
