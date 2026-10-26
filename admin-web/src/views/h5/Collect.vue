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
        <input v-model="form.dept" class="input" placeholder="选填" />
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
import { useRoute } from 'vue-router'
import axios from 'axios'

// ---------- 页面数据 ----------
const route = useRoute()
const venues = ref([])                 // 场所下拉列表
const form = ref({ name: '', empNo: '', phone: '', dept: '', venueId: null, gender: 1 })
const photoPreview = ref('')
const facePhoto = ref('')              // 上传后的人脸照片URL
const submitting = ref(false)
const done = ref(false)
const doneName = ref('')
const fileInput = ref(null)

// 打开页面时加载场所列表
onMounted(async () => {
  try {
    const res = await axios.get('/api/h5/venue/list')
    if (res.data.code === 200) {
      venues.value = res.data.data
      // 从入口页跳进来时带着 venueId，自动选中该场所，员工不用再选
      const qid = Number(route.query.venueId)
      if (qid) {
        form.value.venueId = qid
      }
    }
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
  const reader = new FileReader()
  reader.onload = (ev) => { photoPreview.value = ev.target.result }
  reader.readAsDataURL(file)
  const formData = new FormData()
  formData.append('file', file)
  try {
    const res = await axios.post('/api/upload', formData)
    if (res.data.code === 200) {
      facePhoto.value = res.data.data
    } else {
      // 后端返回失败（例如目录写不进去）时必须提示，否则照片没传上去用户也不知道
      alert('照片上传失败：' + res.data.msg)
    }
  } catch (err) {
    alert('照片上传失败：' + err.message)
  }
}

// ---------- 提交采集 ----------
async function submit() {
  if (!form.value.name.trim()) { alert('请填写姓名'); return }
  if (!form.value.empNo.trim()) { alert('请填写工号'); return }
  if (!form.value.venueId) { alert('请选择所属场所'); return }
  if (!facePhoto.value) { alert('请拍摄人脸照片'); return }
  submitting.value = true
  try {
    const res = await axios.post('/api/h5/collect', {
      ...form.value,
      facePhoto: facePhoto.value
    })
    if (res.data.code === 200) {
      doneName.value = res.data.data.name
      done.value = true
    } else {
      alert(res.data.msg)   // 例如"工号已采集过"
    }
  } catch (err) {
    alert('提交失败：' + err.message)
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
