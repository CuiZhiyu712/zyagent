<template>
  <aside class="chat-side">
    <button class="new-chat" type="button" @click="$emit('newSession')">+ 新建对话</button>
    <div class="mode-block">
      <label>Agent 模式</label>
      <select :value="agentMode || ''" @change="$emit('update:agentMode', $event.target.value || null)">
        <option value="">自动路由</option>
        <option value="LEARNING_TUTOR">学习导师</option>
        <option value="RESUME_COACH">简历顾问</option>
        <option value="JOB_ANALYST">岗位分析</option>
        <option value="INTERVIEWER">技术面试官</option>
        <option value="REVIEW_COACH">复盘教练</option>
      </select>
    </div>
    <div class="session-list">
      <div class="side-title">最近会话</div>
      <div
        v-for="session in sessions"
        :key="session.id"
        :class="['session-item', session.id === activeSessionId ? 'active' : '']"
      >
        <button
          class="session-btn"
          type="button"
          @click="$emit('selectSession', session.id)"
        >
          <span>{{ session.title }}</span>
          <small>{{ session.count }} 条消息</small>
        </button>
        <button class="session-del" type="button" title="删除会话" @click.stop="$emit('deleteSession', session.id)">×</button>
      </div>
    </div>
  </aside>
</template>

<script setup>
defineProps({
  sessions: {
    type: Array,
    required: true
  },
  activeSessionId: {
    type: String,
    required: true
  },
  agentMode: {
    type: String,
    default: null
  }
})

defineEmits(['newSession', 'selectSession', 'update:agentMode', 'deleteSession'])
</script>
