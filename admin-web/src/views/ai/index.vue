<template>
  <div class="ai-layout">
    <!-- 左侧会话列表 -->
    <aside class="sider">
      <div class="sider-title">AI 助手</div>
      <button class="btn-new" @click="createSession">＋ 新建对话</button>

      <div class="session-list">
        <div
          v-for="s in sessions" :key="s.id"
          class="session-item"
          :class="{ active: s.id === currentSessionId }"
          @click="switchSession(s.id)"
        >
          <div class="title">{{ s.title }}</div>
          <div class="time">{{ formatTime(s.updateTime) }}</div>
          <div class="actions">
            <span class="act export" @click.stop="exportSession(s.id)" title="导出">⬇</span>
            <span class="act del" @click.stop="deleteSession(s.id)" title="删除">✕</span>
          </div>
        </div>
        <div v-if="sessions.length === 0" class="empty">暂无会话</div>
      </div>
    </aside>

    <!-- 右侧聊天内容 -->
    <main class="chat">
      <!-- 空会话：欢迎卡片 -->
      <div v-if="!currentSessionId" class="welcome">
        <div class="hi">👋</div>
        <h2>你好，我是联智考勤助手</h2>
        <p>我可以帮你查询考勤数据、分析工时、解答考勤规则问题。所有数据都来自真实业务数据库，不会编造。</p>
        <div class="suggest-grid">
          <div class="suggest" @click="ask('今日考勤概况')">
            <div class="s-icon">📊</div>
            <div class="s-title">今日考勤概况</div>
            <div class="s-desc">在职 / 打卡 / 迟到 / 未打卡</div>
          </div>
          <div class="suggest" @click="ask('今天谁迟到了')">
            <div class="s-icon">⚠️</div>
            <div class="s-title">迟到人员查询</div>
            <div class="s-desc">迟到明细、部门</div>
          </div>
          <div class="suggest" @click="ask('这个月各部门工时怎么样')">
            <div class="s-icon">📈</div>
            <div class="s-title">部门工时分析</div>
            <div class="s-desc">总工时、人均</div>
          </div>
          <div class="suggest" @click="ask('公司上班时间是几点')">
            <div class="s-icon">📋</div>
            <div class="s-title">考勤规则问答</div>
            <div class="s-desc">上下班时间、宽限</div>
          </div>
        </div>
      </div>

      <!-- 会话中：消息气泡 -->
      <div v-else class="messages" ref="msgList">
        <div v-if="messages.length === 0" class="hint">试着问点什么吧 ~</div>

        <div v-for="m in messages" :key="m.id" class="msg" :class="m.role">
          <div class="avatar">{{ m.role === 'user' ? '👤' : '🤖' }}</div>
          <div class="bubble">
            <div class="content" v-html="formatMd(m.content)"></div>
            <div v-if="m.sources" class="sources" @click="toggleSources(m.id)">
              <span class="src-label">📎 来源引用</span>
              <pre v-if="openSources.has(m.id)" class="src-body">{{ m.sources }}</pre>
            </div>
          </div>
        </div>

        <!-- 打字机效果 -->
        <div v-if="typing" class="msg assistant">
          <div class="avatar">🤖</div>
          <div class="bubble typing">
            <span></span><span></span><span></span>
          </div>
        </div>
      </div>

      <!-- 底部输入区 -->
      <div class="input-bar">
        <textarea
          v-model="input"
          :disabled="typing"
          placeholder="输入问题，Enter 发送，Shift+Enter 换行"
          @keydown.enter.exact.prevent="send"
          rows="2"
        ></textarea>
        <button class="btn-send" :disabled="typing || !input.trim()" @click="send">
          发送
        </button>
      </div>
      <div class="quick-bar">
        <span class="chip" @click="ask('今日考勤概况')">今日考勤概况</span>
        <span class="chip" @click="ask('今天谁迟到了')">今天谁迟到了</span>
        <span class="chip" @click="ask('公司上班时间是几点')">上班时间</span>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, nextTick, onMounted } from 'vue'
import request from '../../api/request'

const sessions = ref([])
const currentSessionId = ref(null)
const messages = ref([])
const input = ref('')
const typing = ref(false)
const openSources = reactive(new Set())
const msgList = ref(null)

onMounted(loadSessions)

async function loadSessions() {
  try {
    const res = await request.get('/ai/session/list')
    sessions.value = res.data || []
    if (sessions.value.length > 0) {
      currentSessionId.value = sessions.value[0].id
      await loadMessages(currentSessionId.value)
    }
  } catch (e) {}
}

async function createSession() {
  try {
    const res = await request.post('/ai/session/new')
    sessions.value.unshift(res.data)
    currentSessionId.value = res.data.id
    messages.value = []
  } catch (e) {}
}

async function switchSession(id) {
  currentSessionId.value = id
  await loadMessages(id)
}

async function loadMessages(id) {
  try {
    const res = await request.get(`/ai/session/${id}/messages`)
    messages.value = res.data || []
    nextTick(() => scrollBottom())
  } catch (e) {}
}

async function send() {
  const q = input.value.trim()
  if (!q || typing.value) return
  input.value = ''

  // 无会话则先建一个
  if (!currentSessionId.value) {
    await createSession()
  }

  // 先 optimistic push
  messages.value.push({ role: 'user', content: q })
  typing.value = true
  nextTick(() => scrollBottom())

  try {
    const res = await request.post('/ai/chat', {
      sessionId: currentSessionId.value,
      question: q
    })
    const r = res.data
    messages.value.push({ role: 'assistant', content: r.reply, sources: r.sources })
    // 刷新会话列表（标题可能变了）
    loadSessions()
  } catch (e) {
    messages.value.push({ role: 'assistant', content: '❌ 请求失败：' + (e.message || '未知错误') })
  } finally {
    typing.value = false
    nextTick(() => scrollBottom())
  }
}

async function ask(q) {
  input.value = q
  await send()
}

async function deleteSession(id) {
  if (!confirm('确定删除该会话？')) return
  try {
    await request.delete(`/ai/session/${id}`)
    sessions.value = sessions.value.filter(s => s.id !== id)
    if (currentSessionId.value === id) {
      currentSessionId.value = sessions.value[0]?.id || null
      messages.value = []
      if (currentSessionId.value) loadMessages(currentSessionId.value)
    }
  } catch (e) {}
}

async function exportSession(id) {
  try {
    const res = await request.get(`/ai/session/${id}/export`)
    const data = res.data
    const blob = new Blob([data.markdown], { type: 'text/markdown;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = data.filename
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) { alert('导出失败') }
}

function toggleSources(id) {
  if (openSources.has(id)) openSources.delete(id)
  else openSources.add(id)
}

function scrollBottom() {
  if (msgList.value) msgList.value.scrollTop = msgList.value.scrollHeight
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  return `${d.getMonth()+1}/${d.getDate()} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`
}

// 极简 Markdown → HTML（支持 **粗体**、换行、列表）
function formatMd(text) {
  if (!text) return ''
  let html = text
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/^• /gm, '• ')
  html = html.replace(/\n/g, '<br>')
  return html
}
</script>

<style scoped>
.ai-layout { display: flex; height: calc(100vh - 60px); background: #f7fafc; }
.sider {
  width: 240px; background: #2d3748; color: #e2e8f0;
  display: flex; flex-direction: column; padding: 16px;
}
.sider-title { font-size: 18px; font-weight: bold; margin-bottom: 12px; }
.btn-new {
  width: 100%; padding: 10px; border: none; border-radius: 8px;
  background: #4299e1; color: #fff; font-size: 14px; cursor: pointer; margin-bottom: 16px;
}
.btn-new:hover { background: #3182ce; }
.session-list { flex: 1; overflow-y: auto; }
.session-item {
  padding: 10px 12px; border-radius: 8px; margin-bottom: 6px; cursor: pointer;
  position: relative; font-size: 13px;
}
.session-item:hover { background: rgba(255,255,255,0.08); }
.session-item.active { background: rgba(255,255,255,0.15); }
.session-item .title { font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.session-item .time { font-size: 11px; color: #a0aec0; margin-top: 2px; }
.session-item .actions { position: absolute; right: 8px; top: 8px; display: none; gap: 4px; }
.session-item:hover .actions { display: flex; }
.act { cursor: pointer; padding: 2px 4px; border-radius: 4px; font-size: 12px; }
.act:hover { background: rgba(255,255,255,0.15); }
.empty { text-align: center; color: #718096; padding: 40px 0; font-size: 13px; }

.chat { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.welcome { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 40px; text-align: center; }
.welcome .hi { font-size: 60px; margin-bottom: 16px; }
.welcome h2 { font-size: 24px; margin: 0 0 8px; color: #2d3748; }
.welcome p { color: #718096; max-width: 520px; }
.suggest-grid {
  display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; margin-top: 32px; max-width: 600px;
}
.suggest {
  background: #fff; border-radius: 12px; padding: 16px; text-align: left;
  cursor: pointer; box-shadow: 0 2px 8px rgba(0,0,0,0.06); transition: transform 0.15s;
}
.suggest:hover { transform: translateY(-2px); box-shadow: 0 4px 16px rgba(0,0,0,0.1); }
.suggest .s-icon { font-size: 28px; margin-bottom: 8px; }
.suggest .s-title { font-weight: 600; color: #2d3748; }
.suggest .s-desc { font-size: 12px; color: #718096; margin-top: 4px; }

.messages { flex: 1; overflow-y: auto; padding: 24px; }
.msg { display: flex; gap: 12px; margin-bottom: 20px; align-items: flex-start; }
.msg.user { flex-direction: row-reverse; }
.msg .avatar { font-size: 24px; width: 36px; height: 36px; display: flex; align-items: center; justify-content: center; }
.msg .bubble { max-width: 70%; padding: 12px 16px; border-radius: 12px; line-height: 1.6; white-space: pre-wrap; font-size: 14px; }
.msg.user .bubble { background: #4299e1; color: #fff; }
.msg.assistant .bubble { background: #fff; color: #2d3748; box-shadow: 0 1px 4px rgba(0,0,0,0.06); }
.msg.assistant .content :deep(strong) { font-weight: 600; }
.sources { margin-top: 8px; font-size: 12px; color: #718096; cursor: pointer; border-top: 1px dashed #e2e8f0; padding-top: 8px; }
.src-body { margin: 6px 0 0; padding: 8px; background: #f7fafc; border-radius: 6px; font-size: 11px; overflow-x: auto; }

.typing { display: flex; gap: 4px; padding: 12px 16px; align-items: center; }
.typing span { width: 8px; height: 8px; background: #a0aec0; border-radius: 50%; animation: blink 1.4s infinite; }
.typing span:nth-child(2) { animation-delay: 0.2s; }
.typing span:nth-child(3) { animation-delay: 0.4s; }
@keyframes blink { 0%,60%,100% { opacity: 0.3; } 30% { opacity: 1; } }

.input-bar { display: flex; gap: 8px; padding: 12px 24px; background: #fff; border-top: 1px solid #e2e8f0; }
.input-bar textarea {
  flex: 1; resize: none; border: 1px solid #e2e8f0; border-radius: 8px; padding: 10px; font-size: 14px; outline: none;
  font-family: inherit;
}
.input-bar textarea:focus { border-color: #4299e1; }
.btn-send {
  padding: 0 24px; border: none; border-radius: 8px; background: #4299e1; color: #fff; cursor: pointer; font-size: 14px;
}
.btn-send:disabled { background: #a0aec0; cursor: not-allowed; }
.quick-bar { display: flex; gap: 8px; padding: 0 24px 12px; flex-wrap: wrap; }
.chip { padding: 4px 12px; background: #edf2f7; border-radius: 16px; font-size: 12px; color: #4a5568; cursor: pointer; }
.chip:hover { background: #e2e8f0; }
.hint { text-align: center; color: #a0aec0; padding: 40px 0; font-size: 13px; }
</style>
