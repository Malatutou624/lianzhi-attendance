<template>
  <el-container class="layout">
    <!-- 左侧菜单 -->
    <el-aside width="210px">
      <div class="logo">联智考勤</div>
      <el-menu :default-active="$route.path" router background-color="#1a365d" text-color="#cbd5e0" active-text-color="#ffffff">
        <el-menu-item index="/dashboard">数据中心</el-menu-item>
        <el-menu-item index="/venue">办公场所</el-menu-item>
        <el-menu-item index="/employee">员工管理</el-menu-item>
        <el-menu-item index="/checkin">打卡记录</el-menu-item>
        <el-menu-item index="/rule">考勤规则</el-menu-item>
        <el-menu-item index="/approval">审批中心</el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶栏 -->
      <el-header class="header">
        <span class="page-title">{{ $route.meta.title }}</span>
        <el-dropdown @command="handleCommand">
          <span class="user">{{ userStore.userInfo.realName || userStore.userInfo.username || '管理员' }} ▾</span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">👤 个人主页</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <!-- 主内容区：子路由页面显示在这里 -->
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRouter } from 'vue-router'
import request from '../api/request'
import { useUserStore } from '../store/user'

const router = useRouter()
const userStore = useUserStore()

async function handleCommand(cmd) {
  if (cmd === 'profile') {
    router.push('/profile')
    return
  }
  if (cmd === 'logout') {
    try { await request.post('/auth/logout') } catch (e) { /* 忽略 */ }
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout { height: 100vh; }
.el-aside { background: #1a365d; }
.logo { color: #fff; font-size: 18px; font-weight: bold; text-align: center; padding: 20px 0; }
.el-menu { border-right: none; }
.header {
  display: flex; justify-content: space-between; align-items: center;
  background: #fff; box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
}
.page-title { font-weight: bold; color: #2d3748; }
.user { cursor: pointer; color: #2b6cb0; }
</style>
