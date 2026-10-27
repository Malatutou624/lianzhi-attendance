<template>
  <div class="profile-wrap">
    <el-card class="profile-card">
      <template #header>
        <div class="card-header">
          <span>个人主页</span>
          <span class="username">{{ user?.username }} · {{ user?.role === 'admin' ? '系统管理员' : user?.role }}</span>
        </div>
      </template>

      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px" class="profile-form">
        <div class="avatar-row">
          <div class="avatar">
            <img v-if="form.avatar" :src="form.avatar" />
            <span v-else>{{ (form.realName || form.username || '?').charAt(0) }}</span>
          </div>
          <div class="avatar-actions">
            <el-upload
              :show-file-list="false"
              :before-upload="onAvatarUpload"
              accept="image/*">
              <el-button size="small">上传头像</el-button>
            </el-upload>
            <div class="tip">建议 200×200 像素，JPG/PNG</div>
          </div>
        </div>

        <el-form-item label="登录账号">
          <el-input :model-value="user?.username" disabled />
        </el-form-item>

        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="请输入真实姓名" maxlength="32" />
        </el-form-item>

        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" maxlength="20" />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" maxlength="100" />
        </el-form-item>

        <el-form-item label="头像地址">
          <el-input v-model="form.avatar" placeholder="头像 URL，也可上方直接上传" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存修改</el-button>
          <el-button @click="load">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import request from '../api/request'
import { ElMessage } from 'element-plus'

const user = ref(null)
const form = reactive({ realName: '', phone: '', email: '', avatar: '' })
const saving = ref(false)
const formRef = ref(null)

const rules = {
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ]
}

onMounted(load)

async function load() {
  try {
    const res = await request.get('/auth/profile')
    user.value = res.data
    Object.assign(form, {
      realName: res.data.realName || '',
      phone: res.data.phone || '',
      email: res.data.email || '',
      avatar: res.data.avatar || ''
    })
  } catch (e) {
    ElMessage.error('加载个人信息失败')
  }
}

async function save() {
  try {
    await formRef.value?.validate()
  } catch (e) { return }

  saving.value = true
  try {
    await request.put('/auth/profile', {
      realName: form.realName || null,
      phone: form.phone || null,
      email: form.email || null,
      avatar: form.avatar || null
    })
    ElMessage.success('保存成功')
    // 更新 store 里的 userInfo.realName（顶栏显示）
    const { useUserStore } = await import('../store/user')
    const store = useUserStore()
    if (store.userInfo) {
      store.userInfo.realName = form.realName
      store.userInfo.phone = form.phone
      store.userInfo.email = form.email
      store.userInfo.avatar = form.avatar
    }
  } catch (e) {
    // request 已经弹错
  } finally {
    saving.value = false
  }
}

async function onAvatarUpload(file) {
  const fd = new FormData()
  fd.append('file', file)
  try {
    const res = await request.post('/upload', fd)
    form.avatar = res.data
  } catch (err) {
    ElMessage.error('头像上传失败')
  }
  return false // 阻止 element-plus 自动上传
}
</script>

<style scoped>
.profile-wrap { padding: 20px; }
.profile-card { max-width: 560px; }
.card-header { display: flex; justify-content: space-between; align-items: center; font-weight: bold; }
.card-header .username { font-size: 12px; color: #a0aec0; font-weight: normal; }
.avatar-row {
  display: flex; align-items: center; gap: 20px; margin-bottom: 24px;
  padding-bottom: 20px; border-bottom: 1px solid #edf2f7;
}
.avatar {
  width: 72px; height: 72px; border-radius: 50%;
  background: #2b6cb0; color: #fff; font-size: 32px;
  display: flex; align-items: center; justify-content: center; overflow: hidden;
}
.avatar img { width: 100%; height: 100%; object-fit: cover; }
.avatar-actions { display: flex; flex-direction: column; gap: 6px; }
.avatar-actions .tip { font-size: 12px; color: #a0aec0; }
</style>
