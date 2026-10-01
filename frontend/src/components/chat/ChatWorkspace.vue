<template>
  <div class="chat-workspace">
    <ChatSidebar
      v-model:agent-mode="agentMode"
      :sessions="sessions"
      :active-session-id="activeSessionId"
      @new-session="newSession"
      @select-session="selectSession"
      @delete-session="deleteSession"
    />
    <section class="chat-main">
      <header class="chat-header">
        <div>
          <h3>{{ activeSession.title }}</h3>
          <p>{{ modeLabel }} · RAG / ReAct / MCP 工具调用</p>
        </div>
        <span :class="['status-pill', streaming ? 'live' : '']">{{ streaming ? '生成中' : '就绪' }}</span>
      </header>
      <MessageList :messages="activeSession.messages" @pick-prompt="sendMessage" />
      <MessageComposer
        :streaming="streaming"
        :uploading="uploading"
        :can-regenerate="Boolean(lastUserMessage)"
        @send="sendMessage"
        @upload-file="handleUploadFile"
        @stop="stopStreaming"
        @regenerate="regenerate"
      />
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import ChatSidebar from './ChatSidebar.vue'
import MessageList from './MessageList.vue'
import MessageComposer from './MessageComposer.vue'
import { streamChat } from '../../chat/chatStream'
import { createUploadMessage } from '../../chat/uploadActions'
import { ElMessageBox } from 'element-plus'
import { api } from '../../api'

const sessions = ref([createSession('默认对话')])
const activeSessionId = ref(sessions.value[0].id)
const agentMode = ref(null)
const streaming = ref(false)
const uploading = ref(false)
const currentController = ref(null)
const lastUserMessage = ref('')

const modeLabels = {
  LEARNING_TUTOR: '学习导师',
  RESUME_COACH: '简历顾问',
  JOB_ANALYST: '岗位分析',
  INTERVIEWER: '技术面试官',
  REVIEW_COACH: '复盘教练'
}

const activeSession = computed(() => sessions.value.find(session => session.id === activeSessionId.value) || sessions.value[0])
const modeLabel = computed(() => modeLabels[agentMode.value] || '自动路由')

onMounted(loadSessions)

function createSession(title = '新对话') {
  return {
    id: crypto.randomUUID(),
    title,
    messages: [],
    count: 0
  }
}

async function loadSessions() {
  try {
    const remote = await api.listChatSessions()
    if (Array.isArray(remote) && remote.length) {
      sessions.value = remote.map(toSession)
      activeSessionId.value = sessions.value[0].id
      await loadMessages(activeSessionId.value)
    }
  } catch {
    // Keep the local default session while the backend is still starting.
  }
}

async function loadMessages(sessionId) {
  try {
    const messages = await api.listChatMessages(sessionId)
    const session = sessions.value.find(item => item.id === sessionId)
    if (session) {
      session.messages = messages.map(toMessage)
      session.count = session.messages.length
    }
  } catch {
    // Local sessions still work without persisted history.
  }
}

async function newSession() {
  let session = createSession(`新对话 ${sessions.value.length + 1}`)
  try {
    session = toSession(await api.createChatSession({ title: session.title, agentMode: agentMode.value }))
  } catch {
    // Use local fallback session.
  }
  sessions.value.unshift(session)
  activeSessionId.value = session.id
}

async function selectSession(id) {
  if (streaming.value) stopStreaming()
  activeSessionId.value = id
  await loadMessages(id)
}

async function deleteSession(id) {
  try {
    await ElMessageBox.confirm('确定要删除该对话吗？删除后不可恢复。', '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await api.deleteChatSession(id)
    ElMessage.success('对话已删除')
  } catch (error) {
    ElMessage.error(error.message || '删除失败')
    return
  }
  sessions.value = sessions.value.filter(item => item.id !== id)
  if (activeSessionId.value === id) {
    if (sessions.value.length > 0) {
      activeSessionId.value = sessions.value[0].id
      await loadMessages(sessions.value[0].id)
    } else {
      const session = createSession('新对话')
      sessions.value.push(session)
      activeSessionId.value = session.id
    }
  }
}

async function sendMessage(content) {
  if (!content.trim() || streaming.value) return
  const session = activeSession.value
  lastUserMessage.value = content
  if (session.messages.length === 0) {
    session.title = content.slice(0, 18)
  }

  session.messages.push({
    id: crypto.randomUUID(),
    role: 'user',
    content
  })

  const assistant = reactive({
    id: crypto.randomUUID(),
    role: 'assistant',
    skill: null,
    content: '',
    plan: null,
    tools: [],
    references: null,
    route: null,
    usage: null,
    metrics: null,
    memory: null,
    collaboration: null,
    streaming: true,
    stopped: false,
    error: ''
  })
  session.messages.push(assistant)
  session.count = session.messages.length
  streaming.value = true

  try {
    let receivedEvents = 0
    const stream = await streamChat(
      {
        sessionId: session.id,
        message: content,
        agentMode: agentMode.value
      },
      {
        onSkill: skill => {
          receivedEvents += 1
          assistant.skill = skill
        },
        onPlan: plan => {
          receivedEvents += 1
          assistant.plan = plan
        },
        onTools: tools => {
          receivedEvents += 1
          assistant.tools = Array.isArray(tools) ? tools : []
        },
        onReferences: references => {
          receivedEvents += 1
          assistant.references = references
        },
        onRoute: route => {
          receivedEvents += 1
          assistant.route = route
        },
        onUsage: usage => {
          receivedEvents += 1
          assistant.usage = usage
        },
        onMetrics: metrics => {
          receivedEvents += 1
          assistant.metrics = metrics
        },
        onMemory: memory => {
          receivedEvents += 1
          assistant.memory = memory
        },
        onCollaboration: collaboration => {
          receivedEvents += 1
          assistant.collaboration = collaboration
        },
        onMessage: chunk => {
          receivedEvents += 1
          assistant.content += chunk
        },
        onDone: async () => {
          if (receivedEvents === 0) {
            await completeFallback(content, assistant)
          }
          finishAssistant(assistant)
        },
        onAbort: () => {
          assistant.stopped = true
          finishAssistant(assistant)
        },
        onError: async error => {
          if (!assistant.content) {
            try {
              await completeFallback(content, assistant)
              finishAssistant(assistant)
              return
            } catch {
              assistant.error = error.message || '生成失败'
            }
          } else {
            assistant.error = error.message || '生成失败'
          }
          finishAssistant(assistant)
        }
      }
    )
    currentController.value = stream.controller
  } catch (error) {
    assistant.error = error.message
    finishAssistant(assistant)
  }
}

async function handleUploadFile({ file, knowledgeType }) {
  if (!file || uploading.value) return
  uploading.value = true
  try {
    const record = await api.uploadDocument(file, knowledgeType)
    const session = activeSession.value
    const uploadMessage = createUploadMessage(record, knowledgeType)
    session.messages.push(uploadMessage)
    session.count = session.messages.length
    ElMessage.success(`已上传 ${record.filename || file.name}`)
  } catch (error) {
    ElMessage.error(error.message || '文件上传失败')
  } finally {
    uploading.value = false
  }
}

async function completeFallback(content, assistant) {
  const result = await api.chat({
    sessionId: activeSession.value.id,
    message: content,
    agentMode: agentMode.value
  })
  assistant.plan = result.plan
  assistant.skill = result.skill
  assistant.tools = result.toolResults || []
  assistant.references = result.references || null
  assistant.route = result.routeDecision || null
  assistant.usage = result.tokenUsage || null
  assistant.metrics = result.runMetrics || null
  assistant.memory = result.memorySnapshot || null
  assistant.collaboration = result.collaborationTrace || null
  assistant.content = ''
  for (const chunk of chunkText(result.answer || '', 12)) {
    assistant.content += chunk
    await delay(18)
  }
}

function stopStreaming() {
  if (!currentController.value) return
  currentController.value.abort()
  currentController.value = null
}

function regenerate() {
  if (!lastUserMessage.value || streaming.value) return
  sendMessage(lastUserMessage.value)
}

function finishAssistant(assistant) {
  assistant.streaming = false
  streaming.value = false
  currentController.value = null
  activeSession.value.count = activeSession.value.messages.length
  if (!assistant.content && !assistant.error && !assistant.stopped) {
    assistant.error = '没有收到模型输出'
    ElMessage.warning('没有收到模型输出')
  }
}

function chunkText(text, size) {
  const chunks = []
  for (let start = 0; start < text.length; start += size) {
    chunks.push(text.slice(start, start + size))
  }
  return chunks
}

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

function toSession(value) {
  return {
    id: value.id,
    title: value.title || '新对话',
    messages: [],
    count: value.count || 0
  }
}

function toMessage(value) {
  let references = {}
  try {
    references = value.referencesJson ? JSON.parse(value.referencesJson) : {}
  } catch {
    references = {}
  }
  return {
    id: value.id,
    role: value.role,
    content: value.content,
    skill: references.skill ? { id: references.skill, name: references.skill } : null,
    plan: references.plan || null,
    tools: references.tools || [],
    references: references.references || null,
    route: references.route || null,
    usage: references.usage || null,
    metrics: references.metrics || null,
    memory: references.memory || null,
    collaboration: references.collaboration || null,
    streaming: false,
    stopped: false,
    error: ''
  }
}
</script>
