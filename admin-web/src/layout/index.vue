<template>
  <el-container class="layout">
    <!-- 左侧菜单 -->
    <el-aside width="220px" class="sidebar">
      <!-- Logo -->
      <div class="logo">
        <div class="logo-icon">联</div>
        <div class="logo-text">
          <div class="logo-title">联智考勤</div>
          <div class="logo-sub">Smart Attendance</div>
        </div>
      </div>

      <!-- 菜单 -->
      <el-menu :default-active="$route.path" router
               background-color="transparent" text-color="#a0aec0" active-text-color="#ffffff"
               class="custom-menu">
        <el-menu-item index="/dashboard">
          <span class="mi">🏠</span><span>首页</span>
        </el-menu-item>
        <el-menu-item index="/analysis">
          <span class="mi">📊</span><span>数据分析看板</span>
        </el-menu-item>
        <el-menu-item index="/venue">
          <span class="mi">📍</span><span>办公场所</span>
        </el-menu-item>
        <el-menu-item index="/employee">
          <span class="mi">👥</span><span>员工管理</span>
        </el-menu-item>
        <el-menu-item index="/checkin">
          <span class="mi">⏰</span><span>打卡记录</span>
        </el-menu-item>
        <el-menu-item index="/rule">
          <span class="mi">📋</span><span>考勤规则</span>
        </el-menu-item>
        <el-menu-item index="/approval">
          <span class="mi">✅</span><span>审批中心</span>
        </el-menu-item>
        <el-menu-item index="/ai">
          <span class="mi">🤖</span><span>AI 助手</span>
        </el-menu-item>
        <el-menu-item index="/knowledge">
          <span class="mi">📚</span><span>知识库管理</span>
        </el-menu-item>
      </el-menu>

      <!-- 底部版本信息 -->
      <div class="sidebar-footer">
        <span>v2.0 · 毕业设计</span>
      </div>
    </el-aside>

    <el-container>
      <!-- 顶栏 -->
      <el-header class="header">
        <div class="header-left">
          <el-breadcrumb separator="›">
            <el-breadcrumb-item>管理后台</el-breadcrumb-item>
            <el-breadcrumb-item>{{ $route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <!-- 通知铃铛（预留） -->
          <el-badge :value="0" :hidden="true" class="bell">
            <el-icon :size="20"><Bell /></el-icon>
          </el-badge>

          <!-- 用户信息 -->
          <el-dropdown @command="handleCommand" trigger="click">
            <div class="user-info">
              <div class="avatar">{{ (userStore.userInfo.realName || userStore.userInfo.username || '管').charAt(0) }}</div>
              <div class="user-detail">
                <span class="uname">{{ userStore.userInfo.realName || userStore.userInfo.username || '管理员' }}</span>
                <span class="urole">系统管理员</span>
              </div>
              <el-icon class="caret"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile"><span class="di">👤</span>个人主页</el-dropdown-item>
                <el-dropdown-item divided command="logout"><span class="di">🚪</span>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 主内容区 -->
      <el-main class="main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '../store/user'
import { Bell, ArrowDown } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()

async function handleCommand(cmd) {
  if (cmd === 'profile') { router.push('/profile'); return }
  if (cmd === 'logout') {
    try { await fetch('/auth/logout', { method: 'POST' }) } catch {}
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
/* ===== 整体布局 ===== */
.layout { height: 100vh; overflow: hidden; }

/* ===== 侧边栏 ===== */
.sidebar {
  background: linear-gradient(180deg, #1e3a5f 0%, #0f2544 100%);
  box-shadow: 2px 0 12px rgba(0,0,0,0.08);
  display: flex; flex-direction: column;
  overflow-y: auto;
}

.logo {
  padding: 28px 20px 24px;
  display: flex; align-items: center; gap: 14px;
  border-bottom: 1px solid rgba(255,255,255,0.08);
}

.logo-icon {
  width: 44px; height: 44px;
  background: linear-gradient(135deg, #4299e1, #805ad5);
  border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 20px; font-weight: bold;
  box-shadow: 0 4px 12px rgba(66,153,225,0.4);
}

.logo-title { color: #fff; font-size: 17px; font-weight: 700; letter-spacing: 1px; }
.logo-sub { color: rgba(255,255,255,0.4); font-size: 11px; letter-spacing: 2px; margin-top: 2px; }

.custom-menu { border-right: none; flex: 1; padding: 12px 0; }
.custom-menu .el-menu-item {
  height: 46px; margin: 4px 10px; border-radius: 8px;
  display: flex; align-items: center; gap: 12px;
  font-size: 14px; font-weight: 500;
  transition: all 0.2s;
}
.custom-menu .el-menu-item:hover {
  background: rgba(255,255,255,0.08) !important;
  color: #fff !important;
}
.custom-menu .el-menu-item.is-active {
  background: linear-gradient(135deg, #4299e1, #667eea) !important;
  color: #fff !important;
  box-shadow: 0 4px 14px rgba(66,153,225,0.4);
}
.mi { font-size: 16px; width: 22px; text-align: center; }

.sidebar-footer {
  padding: 16px 20px;
  color: rgba(255,255,255,0.3); font-size: 11px; text-align: center;
  border-top: 1px solid rgba(255,255,255,0.06);
}

/* ===== 顶栏 ===== */
.header {
  background: #fff;
  box-shadow: 0 1px 6px rgba(0,0,0,0.05);
  display: flex; justify-content: space-between; align-items: center;
  padding: 0 24px;
  height: 60px;
}

.header-left { display: flex; align-items: center; }
.header-left :deep(.el-breadcrumb__inner) { color: #718096 !important; font-size: 13px; }
.header-left :deep(.el-breadcrumb__inner.is-link) { color: #2b6cb0 !important; font-weight: 500; }

.header-right { display: flex; align-items: center; gap: 16px; }
.bell { color: #a0aec0; cursor: pointer; }
.bell:hover { color: #4299e1; }

.user-info {
  display: flex; align-items: center; gap: 10px;
  cursor: pointer; padding: 6px 14px 6px 6px;
  border-radius: 24px; transition: background 0.2s;
}
.user-info:hover { background: #f7fafc; }

.avatar {
  width: 36px; height: 36px; border-radius: 50%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff; font-size: 16px; font-weight: bold;
  display: flex; align-items: center; justify-content: center;
}
.user-detail { display: flex; flex-direction: column; line-height: 1.2; }
.uname { font-size: 14px; color: #2d3748; font-weight: 600; }
.urole { font-size: 11px; color: #a0aec0; }
.caret { color: #a0aec0; font-size: 12px; margin-left: 4px; }
.di { margin-right: 8px; }

/* ===== 主内容区 ===== */
.main {
  background: #f7fafc;
  padding: 20px;
  overflow-y: auto;
}

/* ===== 页面切换动画 ===== */
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s, transform 0.2s; }
.fade-enter-from { opacity: 0; transform: translateY(8px); }
.fade-leave-to { opacity: 0; transform: translateY(-4px); }
</style>
