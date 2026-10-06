<template>
  <div v-if="hasContent" class="observability-trace">
    <details v-if="task || status">
      <summary>
        <span>Agent 任务</span>
        <strong :class="['trace-task-state', taskStateClass]">{{ taskState }}</strong>
      </summary>
      <div class="trace-grid">
        <div v-if="task">
          <span>Task</span>
          <strong>{{ shortTaskId }}</strong>
        </div>
        <div v-if="status?.persistence">
          <span>持久化</span>
          <strong>{{ persistenceLabel }}</strong>
        </div>
      </div>
      <p v-if="status?.errorMessage" class="trace-error">{{ status.errorCode || 'ERROR' }} · {{ status.errorMessage }}</p>
      <button v-if="canRetry" type="button" class="trace-retry" @click="$emit('retry')">重试任务</button>
      <div v-if="steps.length" class="trace-steps">
        <div v-for="step in steps" :key="step.stepNo" class="trace-step">
          <span class="trace-step-no">#{{ step.stepNo }}</span>
          <span class="trace-step-title">{{ step.title }}</span>
          <strong :class="['trace-step-state', stepStateClass(step)]">{{ step.status }}</strong>
        </div>
      </div>
    </details>

    <details v-if="route" open>
      <summary>
        <span>路由决策</span>
        <strong>{{ route.category || 'GENERAL' }}</strong>
        <em v-if="route.action" class="route-action">{{ actionLabel }}</em>
      </summary>
      <div class="trace-grid">
        <div>
          <span>Skill</span>
          <strong>{{ route.skillName || route.skillId }}</strong>
        </div>
        <div v-if="route.previousSkillId">
          <span>上一任务</span>
          <strong>{{ route.previousSkillId }}</strong>
        </div>
        <div>
          <span>命中词</span>
          <strong>{{ keywordText }}</strong>
        </div>
      </div>
      <p>{{ route.reason }}</p>
    </details>

    <details v-if="usage || metrics || memory">
      <summary>
        <span>评估与用量</span>
        <strong v-if="usage">{{ usage.totalTokens || 0 }} tokens</strong>
      </summary>
      <div class="trace-grid">
        <div v-if="usage">
          <span>Token</span>
          <strong>{{ usage.promptTokens || 0 }} / {{ usage.completionTokens || 0 }}</strong>
        </div>
        <div v-if="metrics">
          <span>工具成功率</span>
          <strong>{{ successRate }}</strong>
        </div>
        <div v-if="metrics">
          <span>RAG 命中</span>
          <strong>{{ metrics.ragHitCount || 0 }} · {{ metrics.ragSearchMode || 'none' }}</strong>
        </div>
        <div v-if="memory">
          <span>短期记忆</span>
          <strong>{{ memory.recentMessageCount || 0 }} 条</strong>
        </div>
      </div>
      <p v-if="memory?.summary">{{ memory.summary }}</p>
    </details>
  </div>
</template>

<script setup>
import { computed } from 'vue'

defineEmits(['retry'])

const props = defineProps({
  task: {
    type: Object,
    default: null
  },
  status: {
    type: Object,
    default: null
  },
  steps: {
    type: Array,
    default: () => []
  },
  route: {
    type: Object,
    default: null
  },
  usage: {
    type: Object,
    default: null
  },
  metrics: {
    type: Object,
    default: null
  },
  memory: {
    type: Object,
    default: null
  }
})

const hasContent = computed(() =>
  props.task || props.status || props.steps.length || props.route || props.usage || props.metrics || props.memory
)
const taskState = computed(() => props.status?.state || props.task?.state || 'PENDING')
const shortTaskId = computed(() => (props.task?.id ? props.task.id.slice(0, 8) : '—'))
const persistenceLabel = computed(() => (props.status?.persistence === 'memory' ? '内存降级' : '已落库'))
const taskStateClass = computed(() => stateClass(taskState.value))
const canRetry = computed(() =>
  ['FAILED', 'TIMED_OUT', 'INTERRUPTED', 'CANCELLED'].includes(taskState.value) && Boolean(props.task?.id)
)
const keywordText = computed(() => props.route?.matchedKeywords?.length ? props.route.matchedKeywords.join(', ') : '无')
const actionLabel = computed(() => {
  const labels = { CONTINUE: '沿用当前任务', SWITCH: '切换任务', CLARIFY: '需要澄清' }
  return labels[props.route?.action] || props.route?.action
})
const successRate = computed(() => {
  if (!props.metrics) return '0%'
  const rate = typeof props.metrics.toolSuccessRate === 'number' ? props.metrics.toolSuccessRate : 0
  return `${Math.round(rate * 100)}% (${props.metrics.toolSuccess || 0}/${props.metrics.toolTotal || 0})`
})

function stepStateClass(step) {
  return stateClass(step?.status)
}

function stateClass(state) {
  if (state === 'SUCCEEDED' || state === 'SUCCESS') return 'ok'
  if (state === 'FAILED' || state === 'TIMED_OUT') return 'fail'
  if (state === 'RUNNING' || state === 'RETRYING') return 'active'
  return 'muted'
}
</script>
