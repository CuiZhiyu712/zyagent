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
      <MessageList :messages="activeSession.messages" @pick-prompt="sendMessage" @retry-task="retryTask" />
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
import ChatSidebar from './ChatSidebar.vue'
import MessageList from './MessageList.vue'
import MessageComposer from './MessageComposer.vue'
import { useChatWorkspace } from '../../chat/useChatWorkspace'

const {
  sessions, activeSessionId, activeSession, lastUserMessage, agentMode, modeLabel, streaming, uploading,
  newSession, selectSession, deleteSession, sendMessage, handleUploadFile, stopStreaming, regenerate, retryTask
} = useChatWorkspace()
</script>
