<template>
  <details v-if="hasAudit" class="audit-details">
    <summary>证据来源 · 置信度 · 风险</summary>
    <div class="audit-details-body">
      <section class="audit-section">
        <strong>证据来源</strong>
        <div v-if="references?.hits?.length" class="audit-evidence-list">
          <span v-for="hit in references.hits" :key="`${hit.documentId}-${hit.chunkIndex}`">
            {{ hit.filename || hit.documentId }}#chunk-{{ hit.chunkIndex }}
          </span>
        </div>
        <p v-else>本轮未返回引用片段。</p>
        <p v-if="references?.trace" class="audit-muted">
          {{ references.trace.searchMode || references.searchMode || 'unknown' }} · {{ references.hits?.length || 0 }} 条命中
        </p>
      </section>

      <section class="audit-section">
        <strong>置信度</strong>
        <p v-if="evaluatorArtifact">Evaluator 综合置信度：{{ percent(evaluatorArtifact.confidence) }}</p>
        <p v-else>暂无结构化综合置信度。</p>
      </section>

      <section class="audit-section">
        <strong>风险</strong>
        <ul v-if="risks.length"><li v-for="risk in risks" :key="risk">{{ risk }}</li></ul>
        <p v-else>当前链路未记录明显风险。</p>
      </section>

      <div v-if="auditHtml" class="audit-legacy" v-html="auditHtml"></div>
    </div>
  </details>
</template>

<script setup>
import { computed } from 'vue'
import { renderAuditMarkdown } from '../../chat/markdownRenderer'

const props = defineProps({
  content: {
    type: String,
    default: ''
  },
  references: {
    type: Object,
    default: null
  },
  collaboration: {
    type: Object,
    default: null
  },
  route: {
    type: Object,
    default: null
  },
  metrics: {
    type: Object,
    default: null
  }
})

const auditHtml = computed(() => renderAuditMarkdown(props.content))
const artifacts = computed(() => props.collaboration?.artifacts || [])
const evaluatorArtifact = computed(() => artifacts.value.find(item => item.producer === 'EVALUATOR'))
const risks = computed(() => {
  const items = []
  const failedAgents = (props.collaboration?.agents || []).filter(item => item.status === 'FAILED')
  failedAgents.forEach(item => items.push(`${item.role || 'Agent'} 执行失败${item.errorMessage ? `：${item.errorMessage}` : ''}`))
  if (props.references?.trace?.rerankMode === 'rrf_fallback') items.push('Reranker 未启用，沿用 RRF 顺序。')
  if (props.metrics?.toolFailed > 0) items.push(`工具失败 ${props.metrics.toolFailed} 次。`)
  if (props.references && !props.references.hits?.length) items.push('未检索到引用片段，回答需要谨慎核验。')
  return items
})
const hasAudit = computed(() => Boolean(
  auditHtml.value || props.references || props.collaboration || props.route || props.metrics
))

function percent(value) {
  return typeof value === 'number' ? `${Math.round(value * 100)}%` : '暂无'
}
</script>
