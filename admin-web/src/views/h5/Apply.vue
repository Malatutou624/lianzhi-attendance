<template>
  <div class="h5-page">
    <div class="header">
      <button class="back" @click="router.back()">←</button>
      <div class="title">发起申请</div>
    </div>

    <div class="card">
      <!-- 通用申请类型表单 -->
      <template v-if="form.type !== 'resign'">
        <div class="form-item">
          <label>申请类型</label>
          <select v-model="form.type" class="input">
            <option value="" disabled>请选择</option>
            <option v-for="o in typeOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
          </select>
        </div>

        <div v-if="form.type" class="form-item">
          <label>开始时间</label>
          <input v-model="form.startTime" type="datetime-local" class="input" />
        </div>
        <div v-if="form.type" class="form-item">
          <label>结束时间</label>
          <input v-model="form.endTime" type="datetime-local" class="input" />
        </div>
        <div v-if="form.type && form.startTime && form.endTime" class="form-item">
          <label>时长（自动计算）</label>
          <div class="auto-num">{{ autoDuration }} 小时</div>
        </div>

        <div v-if="form.type" class="form-item">
          <label>事由</label>
          <textarea v-model="form.reason" class="input" rows="3" placeholder="请填写事由"></textarea>
        </div>

        <div v-if="form.type" class="form-item">
          <label>附件（可选）</label>
          <input type="file" accept="image/*" @change="onAttach" style="margin-top:6px" />
          <div v-if="attachPreview" class="attach-preview">
            <img :src="attachPreview" />
            <button class="attach-remove" @click="attachPreview = ''">×</button>
          </div>
        </div>

        <div v-if="form.type" class="form-item">
          <label>审批人</label>
          <div class="leader-info">
            <span v-if="leaderName">{{ leaderName }}</span>
            <span v-else class="no-leader">暂无审批人，请联系管理员</span>
          </div>
        </div>
      </template>

      <!-- 离职申请：特殊表单 -->
      <template v-else>
        <div class="notice">⚠️ 离职申请提交后不可撤回，请谨慎操作</div>
        <div class="form-item">
          <label>离职原因</label>
          <textarea v-model="form.resignReason" class="input" rows="4" placeholder="请详细说明离职原因"></textarea>
        </div>
        <div class="form-item">
          <label>审批人</label>
          <div class="leader-info">
            <span v-if="leaderName">{{ leaderName }}</span>
            <span v-else class="no-leader">暂无审批人</span>
          </div>
        </div>
      </template>

      <button v-if="canSubmit" class="btn-submit" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中...' : '提交申请' }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../../api/request'

const router = useRouter()
const emp = ref(null)           // 当前绑定的员工
const leaderName = ref('')      // 审批人姓名
const leaderId = ref(null)
const attachPreview = ref('')
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

const form = reactive({
  type: '', startTime: '', endTime: '', reason: '',
  resignReason: '', attachments: ''
})

onMounted(async () => {
  await identify()
})

async function identify() {
  // 从 /h5/auth/info 拿当前 H5 账号绑定的员工信息
  try {
    const infoRes = await request.get('/h5/auth/info')
    emp.value = infoRes.data?.employee
    if (!emp.value) {
      alert('请先完成员工信息采集')
      router.replace('/h5/collect')
      return
    }
  } catch (e) {
    alert('请先登录')
    router.replace('/h5/login?redirect=/h5/apply')
    return
  }

  // 拿审批人信息（后端自动 fallback 到系统管理员）
  try {
    const approverRes = await request.get('/h5/approval/approver')
    leaderName.value = approverRes.data?.leaderName || ''
    leaderId.value = approverRes.data?.leaderId || null
  } catch (e) {
    // 审批人接口失败不阻塞，前端照样可以提交
  }
}

const autoDuration = computed(() => {
  if (!form.startTime || !form.endTime) return '0'
  const ms = new Date(form.endTime) - new Date(form.startTime)
  if (ms <= 0) return '0'
  return (ms / 3600000).toFixed(2)
})

const canSubmit = computed(() => {
  if (!emp.value) return false
  if (form.type === 'resign') return form.resignReason?.trim()?.length > 0
  if (!form.type) return false
  if (!form.startTime || !form.endTime) return false
  return form.startTime < form.endTime && form.reason?.trim()?.length > 0
})

async function onAttach(e) {
  const file = e.target.files[0]
  if (!file) return
  const fd = new FormData()
  fd.append('file', file)
  try {
    const res = await request.post('/upload', fd)
    form.attachments = res.data
    attachPreview.value = res.data
  } catch (err) {
    alert('附件上传失败：' + (err?.msg || err?.message))
  }
}

async function submit() {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    const payload = {
      type: form.type,
      reason: form.reason,
      resignReason: form.resignReason,
      attachments: form.attachments
    }
    if (form.type !== 'resign') {
      // datetime-local 给的是 "2026-10-28T15:07"（有时带秒有时不带），补 :00 变成合法 LocalDateTime
      payload.startTime = ensureISOSeconds(form.startTime)
      payload.endTime = ensureISOSeconds(form.endTime)
    }
    // 用 H5 审批接口（request 会自动带 h5Token 和 /api 前缀）
    await request.post('/h5/approval/submit', payload)
    alert('提交成功！等待审批')
    router.replace('/h5/myapprovals')
  } catch (err) {
    // request 拦截器已经弹过 ElMessage 了，这里兜底
    alert('提交失败')
  } finally {
    submitting.value = false
  }
}

/** 保证 datetime-local 值带秒："2026-10-28T15:07" → "2026-10-28T15:07:00" */
function ensureISOSeconds(val) {
  if (!val) return val
  // 如果没秒，补 :00
  if (val.length === 16) return val + ':00'
  return val
}
</script>

<style scoped>
.h5-page { min-height: 100vh; background: #f0f4f8; padding: 16px; padding-bottom: 40px; }
.header { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.back { border: none; background: none; font-size: 22px; color: #2b6cb0; cursor: pointer; }
.title { font-size: 18px; font-weight: bold; color: #1a365d; }

.card { background: #fff; border-radius: 12px; padding: 20px; box-shadow: 0 2px 6px rgba(0,0,0,0.05); }
.form-item { margin-bottom: 16px; }
.form-item label { display: block; font-size: 14px; color: #4a5568; margin-bottom: 6px; font-weight: 500; }
.input { width: 100%; padding: 12px; font-size: 16px; border: 1px solid #e2e8f0; border-radius: 8px; box-sizing: border-box; }
textarea.input { resize: none; }
.auto-num { padding: 12px; background: #ebf8ff; border-radius: 8px; color: #2b6cb0; font-weight: 500; }
.leader-info { padding: 12px; background: #f7fafc; border-radius: 8px; color: #2d3748; font-size: 14px; font-weight: 500; }
.leader-info .no-leader { color: #e53e3e; font-weight: normal; }
.notice { background: #fff5f5; color: #c53030; padding: 12px; border-radius: 8px; font-size: 13px; margin-bottom: 16px; }
.attach-preview { position: relative; margin-top: 8px; }
.attach-preview img { width: 80px; height: 80px; object-fit: cover; border-radius: 8px; }
.attach-remove {
  position: absolute; top: -6px; right: 60px; width: 22px; height: 22px; border-radius: 50%;
  background: #e53e3e; color: #fff; border: none; font-size: 14px; line-height: 1;
}
.btn-submit { width: 100%; padding: 14px; margin-top: 8px; border: none; border-radius: 10px; background: #2b6cb0; color: #fff; font-size: 16px; font-weight: bold; }
.btn-submit:disabled { background: #a0aec0; }
</style>
