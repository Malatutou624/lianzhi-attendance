<template>
  <div class="h5-login">
    <div class="brand">
      <div class="logo">🕐</div>
      <div class="name">联智智能考勤</div>
      <div class="subtitle">员工端</div>
    </div>

    <div class="tabs">
      <button :class="{active: tab === 'login'}" @click="tab = 'login'">登 录</button>
      <button :class="{active: tab === 'register'}" @click="tab = 'register'">注 册</button>
    </div>

    <div class="form">
      <input v-model="form.username" placeholder="账号（3位以上）" />
      <input v-model="form.password" type="password" placeholder="密码（6位以上）" />
      <div v-if="tab === 'register' && !bound" class="hint">注册后请先完成员工信息采集</div>
      <button class="submit" :disabled="loading" @click="handleSubmit">
        {{ loading ? '处理中...' : (tab === 'login' ? '登 录' : '注 册') }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import request from '../../api/request'

const router = useRouter()
const route = useRoute()
const tab = ref('login')
const loading = ref(false)
const form = reactive({ username: '', password: '' })

// 已登录 → 直接跳
onMounted(async () => {
  const token = localStorage.getItem('h5Token')
  if (token) {
    try {
      // request 会自动带 h5Token 和 /api 前缀
      const res = await request.get('/h5/auth/info')
      const hasBound = !!res.data?.empId
      const redirect = route.query.redirect || (hasBound ? '/h5/home' : '/h5/collect')
      router.replace(redirect)
    } catch (e) {
      // token 失效，继续显示登录页
      localStorage.removeItem('h5Token')
    }
  }
})

async function handleSubmit() {
  if (!form.username || form.username.length < 3) { alert('账号至少3位'); return }
  if (!form.password || form.password.length < 6) { alert('密码至少6位'); return }

  loading.value = true
  try {
    const url = tab.value === 'login' ? '/h5/auth/login' : '/h5/auth/register'
    // request.post 会自动拼 /api 前缀、自动带 h5Token
    const res = await request.post(url, form)
    const token = res.data?.token
    if (!token) { alert('返回数据异常'); return }

    localStorage.setItem('h5Token', token)
    const bound = !!res.data?.bound

    alert(tab.value === 'login' ? '登录成功' : '注册成功')
    const redirect = route.query.redirect || (bound ? '/h5/home' : '/h5/collect')
    router.replace(redirect)
  } catch (e) {
    // request 拦截器已经弹了 ElMessage，这里不再重复 alert
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.h5-login {
  min-height: 100vh;
  background: linear-gradient(180deg, #2b6cb0 0%, #1a365d 100%);
  padding: 60px 24px 24px;
  color: #fff;
  box-sizing: border-box;
}
.brand { text-align: center; margin-bottom: 40px; }
.logo { font-size: 60px; }
.name { font-size: 24px; font-weight: 600; margin-top: 8px; }
.subtitle { font-size: 13px; opacity: 0.75; margin-top: 4px; }

.tabs {
  display: flex;
  background: rgba(255,255,255,0.15);
  border-radius: 24px;
  padding: 4px;
  margin-bottom: 24px;
}
.tabs button {
  flex: 1;
  padding: 10px 0;
  border: none;
  background: transparent;
  color: rgba(255,255,255,0.8);
  border-radius: 20px;
  font-size: 15px;
  cursor: pointer;
}
.tabs button.active {
  background: #fff;
  color: #2b6cb0;
  font-weight: 600;
}

.form input {
  width: 100%;
  box-sizing: border-box;
  padding: 14px 16px;
  border: 1px solid rgba(255,255,255,0.3);
  background: rgba(255,255,255,0.1);
  border-radius: 12px;
  color: #fff;
  font-size: 15px;
  margin-bottom: 14px;
}
.form input::placeholder { color: rgba(255,255,255,0.6); }
.form input:focus { outline: none; border-color: rgba(255,255,255,0.7); }

.hint { font-size: 12px; opacity: 0.7; margin-bottom: 10px; }

.submit {
  width: 100%;
  padding: 14px;
  background: #fff;
  color: #2b6cb0;
  border: none;
  border-radius: 24px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  margin-top: 8px;
}
.submit:disabled { opacity: 0.6; }
</style>
