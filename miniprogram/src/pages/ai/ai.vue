<template>
  <view class="page">
    <view class="top-bar">
      <text class="title">🤖 AI 智能问答</text>
      <view class="new" @click="createFresh">新建</view>
    </view>

    <scroll-view v-if="messages.length === 0" class="welcome-area" scroll-y>
      <view class="welcome">
        <text class="hi">👋</text>
        <text class="h3">我是联智考勤助手</text>
        <text class="p">可以查考勤统计、考勤规则、请假审批状态等</text>
        <view class="chips">
          <text class="chip" @click="ask('本月我的考勤统计')">本月我的考勤</text>
          <text class="chip" @click="ask('公司上班时间是几点')">上班时间</text>
          <text class="chip" @click="ask('最近有哪些审批')">我的审批</text>
        </view>
      </view>
    </scroll-view>

    <scroll-view v-else class="msgs" scroll-y :scroll-top="scrollTop" :scroll-with-animation="true">
      <view v-for="m in messages" :key="m.id" class="msg" :class="m.role">
        <view class="bubble">
          <text class="content">{{ m.content }}</text>
          <view v-if="m.sources" class="src-btn" @click="toggle(m.id)">
            📎 来源 {{ open.has(m.id) ? '▲' : '▼' }}
          </view>
          <view v-if="open.has(m.id)" class="src-body">{{ m.sources }}</view>
        </view>
      </view>
      <view v-if="typing" class="msg assistant"><view class="bubble typing"><text></text><text></text><text></text></view></view>
    </scroll-view>

    <view class="input-wrap">
      <input
        v-model="input"
        class="input"
        :disabled="typing"
        placeholder="问点什么…"
        confirm-type="send"
        @confirm="send"
      />
      <button class="btn-send" :disabled="typing || !input.trim()" @click="send">发送</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, nextTick } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { http } from '@/utils/request'

const input = ref('')
const messages = ref<any[]>([])
const typing = ref(false)
const open = reactive(new Set<number>())
const scrollTop = ref(0)
let sessionId: string | null = null
let msgIdCounter = 0

onShow(async () => {
  if (!sessionId) {
    try {
      const res = await http.post<any>('/h5/ai/session/new', undefined, { loading: false })
      sessionId = res?.id || null
    } catch {
      uni.showToast({ title: '请先登录', icon: 'none' })
    }
  }
})

async function createFresh() {
  uni.showModal({ title: '新对话', content: '确定开始新对话？', success: async (r) => {
    if (r.confirm) {
      messages.value = []
      open.clear()
      try {
        const res = await http.post<any>('/h5/ai/session/new', undefined, { loading: false })
        sessionId = res?.id || null
      } catch {}
    }
  }})
}

async function send() {
  const q = input.value.trim()
  if (!q || typing.value || !sessionId) return
  input.value = ''
  messages.value.push({ id: ++msgIdCounter, role: 'user', content: q })
  typing.value = true
  scrollToBottom()

  try {
    const res = await http.post<any>('/h5/ai/chat', { sessionId, question: q })
    messages.value.push({ id: ++msgIdCounter, role: 'assistant', content: res?.reply || '无回复', sources: res?.sources })
  } catch {
    messages.value.push({ id: ++msgIdCounter, role: 'assistant', content: '❌ 请求失败，请稍后重试' })
  } finally {
    typing.value = false
    scrollToBottom()
  }
}

async function ask(q: string) { input.value = q; await send() }

function toggle(id: number) { open.has(id) ? open.delete(id) : open.add(id) }

function scrollToBottom() {
  nextTick(() => {
    scrollTop.value = messages.value.length * 99999
  })
}
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; background: #f7fafc; display: flex; flex-direction: column; }
.top-bar { display: flex; align-items: center; padding: 24rpx 32rpx; background: linear-gradient(135deg, #805ad5, #6b46c1); color: #fff; }
.top-bar .title { flex: 1; text-align: center; font-weight: 600; font-size: 32rpx; }
.new { background: rgba(255,255,255,0.2); padding: 8rpx 24rpx; border-radius: 24rpx; font-size: 24rpx; }
.welcome-area { flex: 1; }
.welcome { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 80rpx 40rpx; text-align: center; }
.welcome .hi { font-size: 96rpx; margin-bottom: 24rpx; }
.welcome .h3 { color: #2d3748; margin: 0 0 16rpx; font-size: 36rpx; font-weight: bold; }
.welcome .p { color: #718096; font-size: 26rpx; margin-bottom: 48rpx; }
.chips { display: flex; flex-wrap: wrap; gap: 16rpx; justify-content: center; }
.chip { padding: 12rpx 28rpx; background: #fff; border-radius: 32rpx; font-size: 24rpx; color: #6b46c1; box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.06); }
.msgs { flex: 1; padding: 32rpx; }
.msg { margin-bottom: 32rpx; display: flex; }
.msg.user { justify-content: flex-end; }
.bubble { max-width: 80%; padding: 24rpx 28rpx; border-radius: 32rpx; font-size: 28rpx; line-height: 1.6; }
.msg.user .bubble { background: #6b46c1; color: #fff; border-bottom-right-radius: 8rpx; }
.msg.assistant .bubble { background: #fff; color: #2d3748; box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.06); border-bottom-left-radius: 8rpx; }
.content { white-space: pre-wrap; }
.src-btn { margin-top: 16rpx; font-size: 22rpx; color: #a0aec0; border-top: 2rpx dashed #e2e8f0; padding-top: 12rpx; }
.src-body { margin: 12rpx 0 0; padding: 12rpx; background: #f7fafc; border-radius: 12rpx; font-size: 20rpx; overflow-x: auto; }
.typing { display: flex; gap: 8rpx; padding: 24rpx 28rpx; }
.typing text { width: 16rpx; height: 16rpx; background: #a0aec0; border-radius: 50%; }
.input-wrap { display: flex; align-items: center; gap: 16rpx; padding: 24rpx 32rpx; background: #fff; border-top: 2rpx solid #e2e8f0; }
.input { flex: 1; border: 2rpx solid #e2e8f0; border-radius: 40rpx; padding: 20rpx 28rpx; font-size: 28rpx; }
.btn-send { padding: 16rpx 36rpx; border: none; border-radius: 40rpx; background: #805ad5; color: #fff; font-size: 28rpx; }
.btn-send[disabled] { background: #cbd5e0; }
</style>
