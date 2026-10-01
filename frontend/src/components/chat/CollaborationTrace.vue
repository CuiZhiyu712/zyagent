<template>
  <div v-if="hasTrace" class="collaboration-trace">
    <details open>
      <summary>
        <span>Multi-Agent Collaboration</span>
        <strong>{{ successCount }}/{{ agents.length }} agents</strong>
      </summary>
      <div class="agent-lane">
        <article v-for="agent in agents" :key="agent.role" class="sub-agent">
          <div class="sub-agent-head">
            <strong>{{ roleLabel(agent.role) }}</strong>
            <span :class="['sub-agent-status', String(agent.status || '').toLowerCase()]">
              {{ statusLabel(agent.status) }}
            </span>
          </div>
          <p>{{ agent.summary || '等待执行' }}</p>
          <small v-if="agent.durationMs">{{ agent.durationMs }}ms</small>
          <strong v-if="agent.errorMessage" class="sub-agent-error">{{ agent.errorMessage }}</strong>
        </article>
      </div>
      <div v-if="artifacts.length" class="artifact-list">
        <article v-for="artifact in artifacts" :key="artifact.artifactId" class="artifact-item">
          <div>
            <strong>{{ roleLabel(artifact.producer) }} · {{ artifact.type }}</strong>
            <span>{{ confidenceLabel(artifact.confidence) }}</span>
          </div>
          <p>{{ artifact.summary }}</p>
          <small v-if="artifact.evidenceRefs?.length">引用：{{ artifact.evidenceRefs.join(', ') }}</small>
        </article>
      </div>
      <p v-if="collaboration.finalReview" class="final-review">{{ collaboration.finalReview }}</p>
    </details>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  collaboration: {
    type: Object,
    default: null
  }
})

const agents = computed(() => props.collaboration?.agents || [])
const artifacts = computed(() => props.collaboration?.artifacts || [])
const hasTrace = computed(() => agents.value.length || artifacts.value.length || props.collaboration?.finalReview)
const successCount = computed(() => agents.value.filter(agent => agent.status === 'SUCCESS').length)

function roleLabel(role) {
  return {
    PLANNER: 'Planner',
    RETRIEVER: 'Retriever',
    EVALUATOR: 'Evaluator',
    REVIEWER: 'Reviewer'
  }[role] || role || 'Agent'
}

function statusLabel(status) {
  return {
    PENDING: '等待',
    RUNNING: '执行中',
    SUCCESS: '成功',
    FAILED: '失败',
    SKIPPED: '跳过'
  }[status] || status || '未知'
}

function confidenceLabel(value) {
  if (typeof value !== 'number') return 'confidence -'
  return `confidence ${Math.round(value * 100)}%`
}
</script>
