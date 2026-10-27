<template>
  <div class="h5-page">
    <div class="header">
      <div class="title">个人考勤</div>
      <div class="subtitle">查看考勤、发起申请、跟进审批</div>
    </div>

    <!-- 未识别员工：输入工号 -->
    <div v-if="!emp" class="card">
      <div class="form-item">
        <label>请输入你的工号</label>
        <input v-model="empNo" class="input" placeholder="如：E1001" />
      </div>
      <button class="btn-primary" @click="identify">进入</button>
    </div>

    <!-- 已识别员工：本月概览 + 快捷入口 -->
    <template v-else>
      <div class="card overview">
        <div class="greet">你好，{{ emp.name }} 👋</div>
        <div class="greet-sub">{{ emp.dept }} · {{ emp.position }}</div>
        <div class="stat-row">
          <div class="stat-item">
            <div class="stat-num">{{ summary.attendDays }}</div>
            <div class="stat-label">出勤天数</div>
          </div>
          <div class="stat-item">
            <div class="stat-num warn">{{ summary.lateCount }}</div>
            <div class="stat-label">迟到次数</div>
          </div>
          <div class="stat-item">
            <div class="stat-num warn2">{{ summary.earlyCount }}</div>
            <div class="stat-label">早退次数</div>
          </div>
          <div class="stat-item">
            <div class="stat-num">{{ summary.totalHours }}h</div>
            <div class="stat-label">累计工时</div>
          </div>
        </div>
      </div>

      <div class="menu-grid">
        <button class="menu-btn" @click="go('/h5/calendar')">
          <div class="icon">📅</div>
          <div class="label">考勤日历</div>
        </button>
        <button class="menu-btn" @click="go('/h5/apply')">
          <div class="icon">📝</div>
          <div class="label">发起申请</div>
        </button>
        <button class="menu-btn" @click="go('/h5/myapprovals')">
          <div class="icon">📋</div>
          <div class="label">我的申请</div>
          <span v-if="summary.pendingCount > 0" class="badge">{{ summary.pendingCount }}</span>
        </button>
        <button v-if="emp.role === 2" class="menu-btn" @click="go('/h5/todoapprovals')">
          <div class="icon">✅</div>
          <div class="label">待我审批</div>
          <span v-if="summary.todoCount > 0" class="badge">{{ summary.todoCount }}</span>
        </button>
      </div>

      <div class="card shortcut">
        <div class="shortcut-title">快捷操作</div>
        <div class="shortcut-row">
          <button class="shortcut-btn" @click="go('/h5/checkin')">📍 去打卡</button>
          <button class="shortcut-btn" @click="go('/h5/apply?type=makeup')">🔧 补卡申请</button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import request from '../../api/request'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const emp = ref(null)
const empId = ref(null)
const summary = reactive({ attendDays: 0, lateCount: 0, earlyCount: 0, totalHours: 0, pendingCount: 0, todoCount: 0 })

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
    router.replace('/h5/login?redirect=/h5/home')
    return
  }
  await loadSummary()
}

async function loadSummary() {
  // 考勤统计：调用后端 /h5/stats 统一计算，保证前后端对齐
  try {
    const res = await request.get('/h5/stats')
    const s = res.data || {}
    summary.attendDays = s.attendDays ?? 0
    summary.lateCount = s.lateCount ?? 0
    summary.earlyCount = s.earlyCount ?? 0
    summary.totalHours = s.totalHours ?? 0
  } catch (e) {
    // 考勤数据失败不阻塞首页
  }

  // 待审批申请数
  try {
    const my = await request.get('/h5/approval/my', {
      params: { current: 1, size: 1, status: 'pending' }
    })
    summary.pendingCount = my.data?.total || 0
  } catch (e) {}

  // 待我审批数（仅 leader 有）
  try {
    const todo = await request.get('/h5/approval/todo', {
      params: { current: 1, size: 1 }
    })
    summary.todoCount = todo.data?.total || 0
  } catch (e) {}
}

function go(path) {
  router.push({ path })
}
</script>

<style scoped>
.h5-page { min-height: 100vh; background: #f0f4f8; padding: 20px 16px; padding-bottom: 40px; }
.header { margin-bottom: 16px; }
.title { font-size: 22px; font-weight: bold; color: #1a365d; }
.subtitle { font-size: 13px; color: #718096; margin-top: 4px; }

.card { background: #fff; border-radius: 12px; padding: 20px; margin-bottom: 16px; box-shadow: 0 2px 6px rgba(0,0,0,0.05); }
.form-item label { display: block; font-size: 14px; color: #4a5568; margin-bottom: 6px; }
.input { width: 100%; padding: 12px; font-size: 16px; border: 1px solid #e2e8f0; border-radius: 8px; box-sizing: border-box; }
.btn-primary { width: 100%; padding: 14px; margin-top: 12px; border: none; border-radius: 8px; background: #2b6cb0; color: #fff; font-size: 16px; font-weight: bold; }

.overview { background: linear-gradient(135deg, #1a365d, #2b6cb0); color: #fff; }
.greet { font-size: 20px; font-weight: bold; }
.greet-sub { font-size: 13px; opacity: 0.85; margin-top: 4px; margin-bottom: 16px; }
.stat-row { display: flex; justify-content: space-around; }
.stat-item { text-align: center; }
.stat-num { font-size: 24px; font-weight: bold; }
.stat-num.warn { color: #fbd38d; }
.stat-num.warn2 { color: #fed7d7; }
.stat-label { font-size: 12px; opacity: 0.85; margin-top: 4px; }

.menu-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 16px; }
.menu-btn {
  background: #fff; border: none; border-radius: 12px; padding: 20px;
  display: flex; flex-direction: column; align-items: center; gap: 6px;
  box-shadow: 0 2px 6px rgba(0,0,0,0.05); position: relative;
}
.menu-btn:active { transform: scale(0.97); }
.menu-btn .icon { font-size: 28px; }
.menu-btn .label { font-size: 14px; color: #2d3748; font-weight: 500; }
.badge {
  position: absolute; top: 8px; right: 10px;
  background: #e53e3e; color: #fff; font-size: 11px; padding: 2px 7px;
  border-radius: 10px; min-width: 18px; text-align: center;
}

.shortcut-title { font-size: 14px; color: #4a5568; margin-bottom: 10px; font-weight: 500; }
.shortcut-row { display: flex; gap: 10px; }
.shortcut-btn { flex: 1; padding: 10px; border: 1px solid #e2e8f0; border-radius: 8px; background: #f7fafc; font-size: 13px; color: #2d3748; }
</style>
