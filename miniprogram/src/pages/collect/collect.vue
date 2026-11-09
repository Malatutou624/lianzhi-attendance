<template>
  <view class="page">
    <view class="header">
      <text class="title">员工信息采集</text>
      <text class="subtitle">请填写真实信息并拍摄人脸照片，用于考勤打卡</text>
    </view>

    <view class="card">
      <view class="form-item">
        <text class="label">姓名 <text class="req">*</text></text>
        <input v-model="form.name" class="input" placeholder="请输入真实姓名" />
      </view>

      <view class="form-item">
        <text class="label">工号 <text class="req">*</text></text>
        <input v-model="form.empNo" class="input" placeholder="请输入工号" />
      </view>

      <view class="form-item">
        <text class="label">性别</text>
        <view class="gender-row">
          <view class="gender-opt" :class="{ active: form.gender === 1 }" @click="form.gender = 1">
            <text class="g-icon">♂</text>
            <text>男</text>
          </view>
          <view class="gender-opt" :class="{ active: form.gender === 2 }" @click="form.gender = 2">
            <text class="g-icon">♀</text>
            <text>女</text>
          </view>
        </view>
      </view>

      <view class="form-item">
        <text class="label">手机号</text>
        <input v-model="form.phone" class="input" type="number" maxlength="11" placeholder="选填" />
      </view>

      <view class="form-item">
        <text class="label">部门</text>
        <picker :range="deptOptions" @change="onDeptChange">
          <view class="picker">{{ form.dept || '请选择部门' }}</view>
        </picker>
      </view>

      <view class="form-item">
        <text class="label">职位</text>
        <input v-model="form.position" class="input" placeholder="选填" />
      </view>

      <view class="form-item">
        <text class="label">所属场所 <text class="req">*</text></text>
        <picker :range="venueNames" @change="onVenueChange">
          <view class="picker">{{ form.venueId ? getVenueName(form.venueId) : '请选择场所' }}</view>
        </picker>
      </view>

      <view class="form-item">
        <text class="label">人脸照片 <text class="req">*</text></text>
        <view class="photo-area" @click="choosePhoto">
          <image v-if="photoPreview" :src="photoPreview" class="photo-preview" mode="aspectFill" />
          <view v-else class="photo-tip">
            <text class="camera-icon">📷</text>
            <text>点击拍摄正脸照片</text>
          </view>
        </view>
        <text class="photo-hint">请在光线充足处拍摄，正面对准人脸</text>
      </view>

      <button class="btn-submit" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中...' : '提交采集' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { http } from '@/utils/request'

const venues = ref<any[]>([])
const deptOptions = ['人力资源部', '研发中心', '产品设计部', '市场运营部', '财务部', '客户成功部']
const form = ref({ name: '', empNo: '', gender: 1, phone: '', dept: '', position: '', venueId: null as number | null })
const photoPreview = ref('')
const facePhoto = ref('')
const submitting = ref(false)
const venueNames = ref<string[]>([])

onShow(async () => {
  try {
    const info = await http.get('/h5/auth/info', undefined, { loading: false })
    if (info?.empId) {
      uni.showToast({ title: '已完成采集', icon: 'none' })
      setTimeout(() => uni.switchTab({ url: '/pages/portal/portal' }), 800)
      return
    }
  } catch {}

  try {
    const list = await http.get<any[]>('/h5/venue/list', undefined, { loading: false })
    venues.value = list || []
    venueNames.value = venues.value.map((v: any) => v.name)
  } catch (e) {
    console.error('场所加载失败', e)
  }
})

function getVenueName(id: number) {
  const v = venues.value.find((x: any) => x.id === id)
  return v ? v.name : ''
}

function onDeptChange(e: any) {
  form.value.dept = deptOptions[e.detail.value]
}

function onVenueChange(e: any) {
  const v = venues.value[e.detail.value]
  form.value.venueId = v ? v.id : null
}

function choosePhoto() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['camera'],
    success: (res) => {
      const tempPath = res.tempFilePaths[0]
      photoPreview.value = tempPath
      uploadPhoto(tempPath)
    }
  })
}

function uploadPhoto(filePath: string) {
  uni.showLoading({ title: '上传照片...', mask: true })
  const token = uni.getStorageSync('h5Token') || ''
  uni.uploadFile({
    url: (import.meta.env.BACKEND_BASE || 'http://127.0.0.1:8080') + '/upload',
    filePath,
    name: 'file',
    header: { 'Authorization': 'Bearer ' + token },
    success: (res) => {
      uni.hideLoading()
      try {
        const data = JSON.parse(res.data)
        if (data.code === 200) {
          facePhoto.value = data.data
        } else {
          uni.showToast({ title: data.msg || '上传失败', icon: 'none' })
        }
      } catch {
        uni.showToast({ title: '上传响应异常', icon: 'none' })
      }
    },
    fail: () => {
      uni.hideLoading()
      uni.showToast({ title: '照片上传失败', icon: 'none' })
    }
  })
}

async function submit() {
  if (!form.value.name.trim()) return uni.showToast({ title: '请填写姓名', icon: 'none' })
  if (!form.value.empNo.trim()) return uni.showToast({ title: '请填写工号', icon: 'none' })
  if (!form.value.venueId) return uni.showToast({ title: '请选择场所', icon: 'none' })
  if (!facePhoto.value) return uni.showToast({ title: '请拍人脸照', icon: 'none' })

  submitting.value = true
  try {
    await http.post('/h5/collect', {
      ...form.value,
      facePhoto: facePhoto.value
    })
    uni.showModal({ title: '采集成功', content: '现在可以去打卡了', showCancel: false,
      success: () => uni.switchTab({ url: '/pages/checkin/checkin' })
    })
  } catch (err: any) {
    uni.showToast({ title: err?.message || '提交失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #1a365d, #2b6cb0);
  padding: 40rpx 24rpx 60rpx;
  box-sizing: border-box;
}

.header {
  text-align: center;
  color: #fff;
  margin-bottom: 40rpx;
  padding: 0 20rpx;
}
.title { font-size: 48rpx; font-weight: bold; display: block; letter-spacing: 4rpx; }
.subtitle { font-size: 26rpx; opacity: 0.8; margin-top: 12rpx; display: block; line-height: 1.5; }

.card {
  background: #fff;
  border-radius: 28rpx;
  padding: 48rpx 36rpx;
  box-shadow: 0 8rpx 32rpx rgba(0,0,0,0.15);
}

.form-item {
  margin-bottom: 36rpx;
}

.label {
  display: block;
  font-size: 30rpx;
  color: #2d3748;
  margin-bottom: 16rpx;
  font-weight: 500;
}

.req { color: #f56565; margin-left: 4rpx; }

.input {
  width: 100%;
  padding: 24rpx 28rpx;
  font-size: 30rpx;
  border: 2rpx solid #e2e8f0;
  border-radius: 16rpx;
  box-sizing: border-box;
  color: #2d3748;
  background: #fafbfc;
  line-height: 1.4;
}
.input:focus {
  border-color: #4299e1;
  background: #fff;
}

.picker {
  padding: 24rpx 28rpx;
  font-size: 30rpx;
  border: 2rpx solid #e2e8f0;
  border-radius: 16rpx;
  color: #2d3748;
  background: #fafbfc;
  line-height: 1.4;
}

/* 性别选项 */
.gender-row {
  display: flex;
  gap: 24rpx;
}
.gender-opt {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  padding: 24rpx 0;
  border: 2rpx solid #e2e8f0;
  border-radius: 16rpx;
  background: #fafbfc;
  font-size: 30rpx;
  color: #4a5568;
  transition: all 0.2s;
}
.gender-opt.active {
  border-color: #4299e1;
  background: #ebf8ff;
  color: #2b6cb0;
  font-weight: 600;
}
.g-icon { font-size: 36rpx; }

/* 拍照区 */
.photo-area {
  height: 420rpx;
  background: #f7fafc;
  border: 2rpx dashed #cbd5e0;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.photo-tip {
  text-align: center;
  color: #a0aec0;
  font-size: 28rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  line-height: 1.6;
}
.camera-icon { font-size: 80rpx; margin-bottom: 16rpx; }
.photo-preview { width: 100%; height: 100%; }
.photo-hint {
  display: block;
  font-size: 24rpx;
  color: #a0aec0;
  margin-top: 16rpx;
  text-align: center;
}

/* 提交按钮 */
.btn-submit {
  width: 100%;
  padding: 32rpx 0;
  margin-top: 24rpx;
  border: none;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #4299e1, #2b6cb0);
  color: #fff;
  font-size: 34rpx;
  font-weight: bold;
  letter-spacing: 4rpx;
  box-shadow: 0 8rpx 24rpx rgba(66,153,225,0.4);
}
.btn-submit[disabled] { background: #a0aec0; box-shadow: none; }
</style>
