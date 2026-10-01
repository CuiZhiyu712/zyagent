<template>
  <div v-if="hasContent" class="observability-trace">
    <details v-if="route" open>
      <summary>
        <span>路由决策</span>
        <strong>{{ route.category || 'GENERAL' }}</strong>
      </summary>
      <div class="trace-grid">
        <div>
          <span>Skill</span>
          <strong>{{ route.skillName || route.skillId }}</strong>
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

const props = defineProps({
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

const hasContent = computed(() => props.route || props.usage || props.metrics || props.memory)
const keywordText = computed(() => props.route?.matchedKeywords?.length ? props.route.matchedKeywords.join(', ') : '无')
const successRate = computed(() => {
  if (!props.metrics) return '0%'
  const rate = typeof props.metrics.toolSuccessRate === 'number' ? props.metrics.toolSuccessRate : 0
  return `${Math.round(rate * 100)}% (${props.metrics.toolSuccess || 0}/${props.metrics.toolTotal || 0})`
})
</script>
