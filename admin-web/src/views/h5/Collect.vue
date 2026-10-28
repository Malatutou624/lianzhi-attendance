<template>
  <!-- 员工信息采集页：新员工扫码后打开，填资料+拍人脸照，路径 /h5/collect -->
  <div class="h5-page">
    <div class="header">
      <div class="title">员工信息采集</div>
      <div class="subtitle">请填写真实信息并拍摄人脸照片，用于考勤打卡</div>
    </div>

    <div class="card">
      <!-- 姓名 / 工号 / 手机号 / 部门 -->
      <div class="form-item">
        <label>姓名 *</label>
        <input v-model="form.name" class="input" placeholder="请输入真实姓名" />
      </div>
      <div class="form-item">
        <label>工号 *</label>
        <input v-model="form.empNo" class="input" placeholder="请输入工号，打卡时使用" />
      </div>
      <div class="form-item">
        <label>手机号</label>
        <input v-model="form.phone" class="input" type="tel" maxlength="11" placeholder="选填" />
      </div>
      <div class="form-item">
        <label>部门</label>
        <select v-model="form.dept" class="input">
          <option value="" disabled>请选择</option>
          <option v-for="d in deptOptions" :key="d" :value="d">{{ d }}</option>
        </select>
      </div>
      <div class="form-item">
        <label>职位</label>
        <input v-model="form.position" class="input" placeholder="选填" />
      </div>
      <div class="form-item">
        <label>所属场所 *</label>
        <select v-model="form.venueId" class="input">
          <option :value="null" disabled>请选择</option>
          <option v-for="v in venues" :key="v.id" :value="v.id">{{ v.name }}</option>
        </select>
      </div>

      <!-- 人脸照片：拍照或从相册选（用label包裹input，点击更可靠） -->
      <div class="form-item">
        <label>人脸照片 *</label>
        <label class="photo-area" for="collectPhotoInput">
          <img v-if="photoPreview" :src="photoPreview" class="photo-preview" />
          <div v-else class="photo-tip">
            <div class="camera-icon">📷</div>
            <div>点击拍摄正脸照片</div>
          </div>
          <input id="collectPhotoInput" ref="fileInput" type="file" accept="image/*" capture="user" style="display:none" @change="onPhotoTaken" />
        </label>
      </div>

      <button class="btn-submit" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中...' : '提交采集' }}
      </button>
    </div>

    <!-- 成功弹层 -->
    <div v-if="done" class="result-mask">
      <div class="result-card">
        <div class="result-icon">🎉</div>
        <div class="result-title">采集成功！</div>
        <div class="result-line">{{ doneName }}，你现在可以去打卡页面打卡了</div>
        <div class="result-line">工号：{{ form.empNo }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../../api/request'

// ---------- 页面数据 ----------
const route = useRoute()
const router = useRouter()
const venues = ref([])                 // 场所下拉列表
const deptOptions = ['人力资源部', '研发中心', '产品设计部', '市场运营部', '财务部', '客户成功部']
const form = ref({ name: '', empNo: '', phone: '', dept: '', position: '', venueId: null, gender: 1 })
const photoPreview = ref('')
const facePhoto = ref('')
const submitting = ref(false)
const done = ref(false)
const doneName = ref('')
const fileInput = ref(null)

// 打开页面时：检查是否已绑定 + 加载场所列表
onMounted(async () => {
  // 检查账号是否已绑定员工（已绑定=已采集过）
  try {
    const infoRes = await request.get('/h5/auth/info')
    if (infoRes.data?.empId) {
      alert('本账号已完成员工信息采集，如需修改请联系管理员')
      router.replace('/h5/home')
      return
    }
  } catch (e) {
    // 没登录（路由守卫应该拦了），或 token 失效
  }

  // 加载场所
  try {
    const res = await request.get('/h5/venue/list')
    venues.value = res.data
    const qid = Number(route.query.venueId)
    if (qid) form.value.venueId = qid
  } catch (e) {
    alert('场所列表加载失败')
  }
})

// ---------- 拍照 + 上传（和打卡页逻辑一样） ----------
function takePhoto() {
  fileInput.value.click()
}
async function onPhotoTaken(e) {
  const file = e.target.files[0]
  if (!file) return
  // 压缩照片（手机照片太大，超过10MB限制）
  const compressedFile = await compressImage(file)
  const reader = new FileReader()
  reader.onload = (ev) => { photoPreview.value = ev.target.result }
  reader.readAsDataURL(compressedFile)
  const formData = new FormData()
  formData.append('file', compressedFile)
  try {
    const res = await request.post('/upload', formData)
    facePhoto.value = res.data
  } catch (err) {
    alert('照片上传失败：' + (err?.msg || err?.message))
  }
}

// 压缩图片：限制最大宽度/高度为 1280px，JPEG 质量 0.7
function compressImage(file) {
  return new Promise((resolve) => {
    const reader = new FileReader()
    reader.readAsDataURL(file)
    reader.onload = (e) => {
      const img = new Image()
      img.src = e.target.result
      img.onload = () => {
        const canvas = document.createElement('canvas')
        const ctx = canvas.getContext('2d')
        let width = img.width
        let height = img.height
        const maxSize = 1280
        if (width > maxSize || height > maxSize) {
          if (width > height) {
            height = Math.round((height * maxSize) / width)
            width = maxSize
          } else {
            width = Math.round((width * maxSize) / height)
            height = maxSize
          }
        }
        canvas.width = width
        canvas.height = height
        ctx.drawImage(img, 0, 0, width, height)
        canvas.toBlob((blob) => {
          resolve(new File([blob], file.name, { type: 'image/jpeg' }))
        }, 'image/jpeg', 0.7)
      }
    }
  })
}

// ---------- 提交采集 ----------
async function submit() {
  if (!form.value.name.trim()) { alert('请填写姓名'); return }
  if (!form.value.empNo.trim()) { alert('请填写工号'); return }
  if (!form.value.venueId) { alert('请选择所属场所'); return }
  if (!facePhoto.value) { alert('请拍摄人脸照片'); return }
  submitting.value = true
  try {
    const res = await request.post('/h5/collect', {
      ...form.value,
      facePhoto: facePhoto.value
    })
    doneName.value = res.data.name
    done.value = true
  } catch (err) {
    alert(err?.msg || '提交失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.h5-page { min-height: 100vh; background: linear-gradient(180deg, #1a365d, #2b6cb0); padding: 24px 16px; box-sizing: border-box; }
.header { text-align: center; color: #fff; margin-bottom: 20px; }
.title { font-size: 22px; font-weight: bold; }
.subtitle { font-size: 13px; opacity: 0.8; margin-top: 6px; }
.card { background: #fff; border-radius: 14px; padding: 20px 16px; }
.form-item { margin-bottom: 16px; }
.form-item label { display: block; font-size: 14px; color: #4a5568; margin-bottom: 6px; }
.input { width: 100%; padding: 12px; font-size: 16px; border: 1px solid #e2e8f0; border-radius: 8px; box-sizing: border-box; outline: none; }
.input:focus { border-color: #2b6cb0; }
.photo-area { height: 180px; background: #f7fafc; border: 1px dashed #cbd5e0; border-radius: 10px; display: flex; align-items: center; justify-content: center; overflow: hidden; }
.photo-tip { text-align: center; color: #a0aec0; font-size: 13px; }
.camera-icon { font-size: 34px; margin-bottom: 6px; }
.photo-preview { width: 100%; height: 100%; object-fit: cover; }
.btn-submit { width: 100%; padding: 14px; margin-top: 8px; border: none; border-radius: 10px; background: #2b6cb0; color: #fff; font-size: 17px; font-weight: bold; }
.btn-submit:disabled { background: #a0aec0; }
.result-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; }
.result-card { background: #fff; border-radius: 14px; padding: 30px 36px; text-align: center; }
.result-icon { font-size: 44px; }
.result-title { font-size: 20px; font-weight: bold; margin: 12px 0; }
.result-line { font-size: 14px; color: #4a5568; margin-top: 6px; }
</style>
