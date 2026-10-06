<template>
  <details v-if="references?.hits?.length" class="reference-trace">
    <summary>
      <span>引用来源</span>
      <strong>{{ modeLabel }}</strong>
      <em>{{ references.hits.length }} 条命中</em>
    </summary>
    <div v-if="trace" class="reference-trace-meta">
      <span :class="['channel-chip', statusClass(trace.vectorStatus)]">
        向量 {{ statusLabel(trace.vectorStatus) }} · {{ trace.vectorCandidates ?? 0 }} 条 · {{ trace.vectorLatencyMs ?? 0 }}ms
      </span>
      <span :class="['channel-chip', statusClass(trace.keywordStatus)]">
        关键词 {{ statusLabel(trace.keywordStatus) }} · {{ trace.keywordCandidates ?? 0 }} 条 · {{ trace.keywordLatencyMs ?? 0 }}ms
      </span>
      <span :class="['channel-chip', trace.rerankStatus === 'failed' || trace.rerankMode === 'rrf_fallback' ? 'warn' : 'ok']">
        重排 {{ trace.rerankMode || 'none' }}<template v-if="trace.rerankStatus === 'failed'">（降级）</template> · {{ trace.rerankLatencyMs ?? 0 }}ms
      </span>
      <span class="channel-chip muted">融合 {{ trace.fusedCandidates ?? 0 }} → 最终 {{ references.hits.length }}</span>
      <span v-if="trace.note" class="channel-chip note">{{ trace.note }}</span>
    </div>
    <div class="reference-list">
      <article v-for="hit in references.hits" :key="`${hit.documentId}-${hit.chunkIndex}`" class="reference-item">
        <div class="reference-meta">
          <strong>{{ hit.filename || hit.documentId }}</strong>
          <span>{{ typeLabel(hit.knowledgeType) }}</span>
          <span>chunk #{{ hit.chunkIndex }}</span>
          <span v-if="typeof hit.score === 'number'">score {{ hit.score.toFixed(4) }}</span>
        </div>
        <p>{{ hit.content }}</p>
      </article>
    </div>
  </details>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  references: {
    type: Object,
    default: null
  }
})

const trace = computed(() => props.references?.trace || null)

const modeLabel = computed(() => {
  const current = trace.value
  if (current) {
    return `${statusLabel(current.vectorStatus)}向量 + ${statusLabel(current.keywordStatus)}关键词 · 重排 ${current.rerankMode || 'none'}`
  }
  const labels = {
    milvus: 'Milvus 语义检索',
    keyword_fallback: '关键词降级',
    memory_fallback: '内存降级'
  }
  return labels[props.references?.searchMode] || props.references?.searchMode || '未知模式'
})

function statusLabel(status) {
  const labels = { ok: '正常', failed: '失败', unavailable: '不可用', skipped: '跳过' }
  return labels[status] || status || '未知'
}

function statusClass(status) {
  if (status === 'ok') return 'ok'
  if (status === 'failed') return 'fail'
  return 'muted'
}

function typeLabel(type) {
  const labels = {
    RESUME: '简历库',
    PROJECT: '项目库',
    STUDY: '学习库',
    JOB: '岗位库',
    INTERVIEW: '面试题库',
    REVIEW: '复盘库'
  }
  return labels[type] || type || '知识库'
}
</script>
