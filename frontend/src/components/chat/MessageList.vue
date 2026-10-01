<template>
  <div ref="scroller" class="message-list" @scroll="handleScroll">
    <div v-if="!messages.length" class="chat-empty">
      <div class="empty-mark">ZY</div>
      <h3>今天准备哪一场面试？</h3>
      <p>可以从简历优化、岗位 JD 分析、项目追问、学习计划或复盘补强开始。</p>
      <div class="prompt-grid">
        <button v-for="prompt in prompts" :key="prompt" type="button" @click="$emit('pickPrompt', prompt)">
          {{ prompt }}
        </button>
      </div>
    </div>

    <article v-for="message in messages" :key="message.id" :class="['message-row', message.role]">
      <div class="avatar">{{ message.role === 'user' ? '你' : 'ZY' }}</div>
      <div class="message-bubble">
        <ToolTrace v-if="message.role === 'assistant'" :skill="message.skill" :plan="message.plan" :tools="message.tools" />
        <CollaborationTrace v-if="message.role === 'assistant'" :collaboration="message.collaboration" />
        <ReferenceTrace v-if="message.role === 'assistant'" :references="message.references" />
        <ObservabilityTrace
          v-if="message.role === 'assistant'"
          :route="message.route"
          :usage="message.usage"
          :metrics="message.metrics"
          :memory="message.memory"
        />
        <div v-if="message.kind === 'upload'" class="upload-card">
          <div class="upload-card-head">
            <span class="file-icon">DOC</span>
            <div>
              <strong>{{ message.upload?.filename }}</strong>
              <p>{{ message.upload?.typeLabel }} · {{ message.upload?.parseStatus }} · {{ message.upload?.chunkCount }} 个切片</p>
            </div>
          </div>
          <div class="quick-actions">
            <button
              v-for="action in message.quickActions || []"
              :key="action.id"
              type="button"
              @click="$emit('pickPrompt', action.prompt)"
            >
              {{ action.label }}
            </button>
          </div>
        </div>
        <div v-else class="message-content" v-html="renderMarkdown(message.content || '')"></div>
        <span v-if="message.streaming" class="cursor"></span>
        <div v-if="message.stopped" class="message-note">已停止生成</div>
        <div v-if="message.error" class="message-error">{{ message.error }}</div>
      </div>
    </article>
  </div>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'
import ToolTrace from './ToolTrace.vue'
import CollaborationTrace from './CollaborationTrace.vue'
import ReferenceTrace from './ReferenceTrace.vue'
import ObservabilityTrace from './ObservabilityTrace.vue'
import { renderMarkdown } from '../../chat/markdownRenderer'

defineEmits(['pickPrompt'])

const props = defineProps({
  messages: {
    type: Array,
    required: true
  }
})

const scroller = ref(null)
const shouldStick = ref(true)

const prompts = [
  '根据我的 Java 后端简历，生成一份面试准备计划',
  '分析这个 JD 和我项目经历的匹配度',
  '扮演面试官，围绕 Redis 项目连续追问',
  '把这次面试复盘整理成薄弱点清单'
]

watch(
  () => props.messages.map(message => `${message.id}:${message.content?.length || 0}:${message.streaming}`).join('|'),
  async () => {
    if (!shouldStick.value) return
    await nextTick()
    if (scroller.value) {
      scroller.value.scrollTop = scroller.value.scrollHeight
    }
  }
)

function handleScroll() {
  const el = scroller.value
  if (!el) return
  shouldStick.value = el.scrollHeight - el.scrollTop - el.clientHeight < 96
}

</script>
