<template>
  <view class="notice-page">
    <!-- 顶部操作栏 -->
    <view v-if="list.length > 0" class="top-bar">
      <text class="count">共 {{ list.length }} 条，{{ unreadCount }} 条未读</text>
      <text v-if="unreadCount > 0" class="mark-all" @click="markAllRead">全部已读</text>
    </view>

    <!-- 列表 -->
    <view v-if="list.length > 0" class="list">
      <view
        v-for="item in list"
        :key="item.id"
        class="item"
        :class="{ unread: !item.isRead }"
        @click="onItemClick(item)"
      >
        <view class="dot" v-if="!item.isRead"></view>
        <view class="icon-wrap" :class="item.type">
          <text class="type-icon">{{ typeIcon(item.type) }}</text>
        </view>
        <view class="content">
          <view class="title-row">
            <text class="title">{{ item.title }}</text>
            <text class="time">{{ formatTime(item.createTime) }}</text>
          </view>
          <text class="body">{{ item.content }}</text>
        </view>
      </view>
    </view>

    <!-- 空状态 -->
    <view v-else class="empty">
      <text class="empty-icon">📭</text>
      <text class="empty-text">暂无消息</text>
      <text class="empty-hint">补卡提醒、月度报告都会在这里出现</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { http } from '@/utils/request'

const list = ref<any[]>([])
const unreadCount = ref(0)

onShow(() => load())

async function load() {
  try {
    list.value = await http.get('/h5/notice/list')
    unreadCount.value = await http.get('/h5/notice/unread', undefined, { loading: false })
  } catch {}
}

async function onItemClick(item: any) {
  if (!item.isRead) {
    try { await http.post(`/h5/notice/${item.id}/read`) } catch {}
    item.isRead = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  }
  // 月报通知 → 跳月报页
  if (item.type === 'MONTHLY_REPORT') {
    uni.navigateTo({ url: '/pages/report/report' })
  }
}

async function markAllRead() {
  try {
    await http.post('/h5/notice/read-all')
    list.value.forEach(n => n.isRead = true)
    unreadCount.value = 0
    uni.showToast({ title: '已全部标记', icon: 'success' })
  } catch {}
}

function typeIcon(type: string) {
  switch (type) {
    case 'MAKEUP_REMIND': return '⏰'
    case 'MONTHLY_REPORT': return '📊'
    default: return '📢'
  }
}

function formatTime(iso: string) {
  if (!iso) return ''
  const d = new Date(iso)
  const now = new Date()
  const diff = (now.getTime() - d.getTime()) / 1000
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style lang="scss" scoped>
.notice-page {
  padding: 20rpx;
  min-height: 100vh;
  background: #f5f7fa;

  .top-bar {
    display: flex; justify-content: space-between; align-items: center;
    padding: 16rpx 24rpx;
    margin-bottom: 16rpx;

    .count { font-size: 26rpx; color: #999; }
    .mark-all { font-size: 26rpx; color: #2b6cb0; }
  }

  .list {
    display: flex; flex-direction: column; gap: 16rpx;

    .item {
      background: #fff;
      border-radius: 16rpx;
      padding: 28rpx 24rpx;
      display: flex;
      position: relative;
      box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.04);

      &.unread { background: #f7fafc; }

      .dot {
        position: absolute; top: 32rpx; left: 8rpx;
        width: 14rpx; height: 14rpx; border-radius: 50%;
        background: #f56565;
      }

      .icon-wrap {
        width: 72rpx; height: 72rpx;
        border-radius: 50%;
        display: flex; align-items: center; justify-content: center;
        margin-right: 20rpx; flex-shrink: 0;
        background: #ebf8ff;

        &.MAKEUP_REMIND { background: #fffaf0; }
        &.MONTHLY_REPORT { background: #f0fff4; }

        .type-icon { font-size: 36rpx; }
      }

      .content { flex: 1; overflow: hidden; }

      .title-row {
        display: flex; justify-content: space-between; align-items: center;
        margin-bottom: 8rpx;
      }

      .title {
        font-size: 28rpx; color: #1a202c;
        font-weight: 500;
      }

      .time { font-size: 22rpx; color: #a0aec0; }

      .body {
        font-size: 24rpx; color: #4a5568;
        line-height: 1.6;
        display: -webkit-box;
        -webkit-box-orient: vertical;
        -webkit-line-clamp: 2;
        overflow: hidden;
      }
    }
  }

  .empty {
    display: flex; flex-direction: column; align-items: center;
    padding-top: 200rpx;

    .empty-icon { font-size: 120rpx; margin-bottom: 24rpx; }
    .empty-text { font-size: 30rpx; color: #a0aec0; margin-bottom: 12rpx; }
    .empty-hint { font-size: 24rpx; color: #cbd5e0; }
  }
}
</style>
