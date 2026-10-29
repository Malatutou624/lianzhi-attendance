<template>
  <div class="h5-ai">
    <!-- 顶部 -->
    <div class="top-bar">
      <button class="back" @click="$router.back()">←</button>
      <div class="title">🤖 AI 智能问答</div>
      <button class="new" @click="createFresh">新建</button>
    </div>

    <!-- 欢迎卡片 -->
    <div v-if="messages.length === 0" class="welcome">
      <div class="hi">👋</div>
      <h3>我是联智考勤助手</h3>
      <p>可以查考勤统计、考勤规则、请假审批状态等</p>
      <div class="chips">
        <span @click="ask('本月我的考勤统计')">本月我的考勤</span>
        <span @click="ask('公司上班时间是几点')">上班时间</span>
        <span @click="ask('最近有哪些审批')">我的审批</span>
      </div>
    </div>

    <!-- 消息区 -->
    <div v-else class="msgs" ref="msgList">
      <div v-for="m in messages" :key="m.id" class="msg" :class="m.role">
        <div class="bubble">
          <div class="content" v-html="formatMd(m.content)"></div>
          <div v-if="m.sources" class="src-btn" @click="toggle(m.id)">
            📎 来源 {{ open.has(m.id) ? '▲' : '▼' }}
          </div>
          <pre v-if="open.has(m.id)" class="src-body">{{ m.sources }}</pre>
        </div>
      </div>
      <div v-if="typing" class="msg assistant"><div class="bubble typing"><span></span><span></span><span></span></div></div>
    </div>

    <!-- 底部输入 -->
    <div class="input-wrap">
      <button class="btn-export" @click="exportMd" title="导出 Markdown">⬇</button>
      <textarea
        v-model="input"
        :disabled="typing"
        placeholder="问点什么…"
        @keydown.enter.exact.prevent="send"
        rows="1"
        ref="textarea"
      ></textarea>
      <button class="btn-send" :disabled="typing || !input.trim()" @click="send">发送</button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../../api/request'

const router = useRouter()
const input = ref('')
const messages = ref([])
const typing = ref(false)
const open = reactive(new Set())
const msgList = ref(null)
const textarea = ref(null)
let sessionId = null

onMounted(async () => {
  // 自动创建一个会话（每次进都是新的，H5 端简化）
  try {
    const res = await request.post('/h5/ai/session/new')
    sessionId = res.data.id
  } catch (e) {
    alert('请先登录')
    router.replace('/h5/login')
  }
})

async function createFresh() {
  if (!confirm('确定开始新对话？当前对话不会被保存到会话列表。')) return
  messages.value = []
  try {
    const res = await request.post('/h5/ai/session/new')
    sessionId = res.data.id
  } catch (e) {}
}

async function send() {
  const q = input.value.trim()
  if (!q || typing.value || !sessionId) return
  input.value = ''

  messages.value.push({ role: 'user', content: q })
  typing.value = true
  nextTick(() => { scrollToBottom(); autoResize() })

  try {
    const res = await request.post('/h5/ai/chat', { sessionId, question: q })
    messages.value.push({ role: 'assistant', content: res.data.reply, sources: res.data.sources })
  } catch (e) {
    messages.value.push({ role: 'assistant', content: '❌ 请求失败' })
  } finally {
    typing.value = false
    nextTick(scrollToBottom)
  }
}

async function ask(q) { input.value = q; await send() }

async function exportMd() {
  if (!sessionId) return
  try {
    const res = await request.get(`/h5/ai/session/${sessionId}/export`)
    const data = res.data
    const blob = new Blob([data.markdown], { type: 'text/markdown;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = data.filename; a.click()
    URL.revokeObjectURL(url)
  } catch (e) { alert('导出失败') }
}

function toggle(id) { open.has(id) ? open.delete(id) : open.add(id) }
function scrollToBottom() { if (msgList.value) msgList.value.scrollTop = msgList.value.scrollHeight }
function autoResize() {
  if (textarea.value) {
    textarea.value.style.height = 'auto'
    textarea.value.style.height = Math.min(textarea.value.scrollHeight, 100) + 'px'
  }
}
function formatMd(text) {
  if (!text) return ''
  return text
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n/g, '<br>')
}
</script>

<style scoped>
.h5-ai { min-height: 100vh; background: #f7fafc; display: flex; flex-direction: column; }
.top-bar {
  display: flex; align-items: center; padding: 12px 16px;
  background: linear-gradient(135deg, #805ad5, #6b46c1); color: #fff;
  position: sticky; top: 0; z-index: 10;
}
.top-bar .back { background: transparent; border: none; color: #fff; font-size: 20px; cursor: pointer; padding: 0 8px; }
.top-bar .title { flex: 1; text-align: center; font-weight: 600; }
.top-bar .new {
  background: rgba(255,255,255,0.2); border: none; color: #fff;
  padding: 4px 12px; border-radius: 12px; font-size: 12px; cursor: pointer;
}

.welcome { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 40px 20px; text-align: center; }
.welcome .hi { font-size: 48px; margin-bottom: 12px; }
.welcome h3 { color: #2d3748; margin: 0 0 8px; }
.welcome p { color: #718096; font-size: 13px; margin-bottom: 24px; }
.chips { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }
.chips span { padding: 6px 14px; background: #fff; border-radius: 16px; font-size: 12px; color: #6b46c1; cursor: pointer; box-shadow: 0 1px 4px rgba(0,0,0,0.06); }

.msgs { flex: 1; overflow-y: auto; padding: 16px; }
.msg { margin-bottom: 16px; display: flex; }
.msg.user { justify-content: flex-end; }
.msg .bubble { max-width: 80%; padding: 12px 14px; border-radius: 16px; font-size: 14px; line-height: 1.6; white-space: pre-wrap; }
.msg.user .bubble { background: #6b46c1; color: #fff; border-bottom-right-radius: 4px; }
.msg.assistant .bubble { background: #fff; color: #2d3748; box-shadow: 0 1px 4px rgba(0,0,0,0.06); border-bottom-left-radius: 4px; }
.src-btn { margin-top: 8px; font-size: 11px; color: #a0aec0; cursor: pointer; border-top: 1px dashed #e2e8f0; padding-top: 6px; }
.src-body { margin: 6px 0 0; padding: 6px; background: #f7fafc; border-radius: 6px; font-size: 10px; overflow-x: auto; }

.typing { display: flex; gap: 4px; padding: 12px 14px; }
.typing span { width: 8px; height: 8px; background: #a0aec0; border-radius: 50%; animation: blink 1.4s infinite; }
.typing span:nth-child(2) { animation-delay: 0.2s; }
.typing span:nth-child(3) { animation-delay: 0.4s; }
@keyframes blink { 0%,60%,100% { opacity: 0.3; } 30% { opacity: 1; } }

.input-wrap {
  display: flex; align-items: flex-end; gap: 8px; padding: 12px 16px;
  background: #fff; border-top: 1px solid #e2e8f0; position: sticky; bottom: 0;
}
.input-wrap textarea {
  flex: 1; resize: none; border: 1px solid #e2e8f0; border-radius: 20px; padding: 10px 14px;
  font-size: 14px; outline: none; max-height: 100px; font-family: inherit;
}
.input-wrap textarea:focus { border-color: #805ad5; }
.btn-send {
  padding: 8px 18px; border: none; border-radius: 20px; background: #805ad5; color: #fff; cursor: pointer; font-size: 14px;
}
.btn-send:disabled { background: #cbd5e0; }
.btn-export { background: transparent; border: none; font-size: 20px; cursor: pointer; color: #6b46c1; padding: 4px; }
</style>
