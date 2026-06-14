<template>
  <details v-if="references?.hits?.length" class="reference-trace" open>
    <summary>
      <span>引用来源</span>
      <strong>{{ modeLabel }}</strong>
      <em>{{ references.hits.length }} 条命中</em>
    </summary>
    <div class="reference-list">
      <article v-for="hit in references.hits" :key="`${hit.documentId}-${hit.chunkIndex}`" class="reference-item">
        <div class="reference-meta">
          <strong>{{ hit.filename || hit.documentId }}</strong>
          <span>{{ typeLabel(hit.knowledgeType) }}</span>
          <span>chunk #{{ hit.chunkIndex }}</span>
          <span v-if="typeof hit.score === 'number'">score {{ hit.score.toFixed(3) }}</span>
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

const modeLabel = computed(() => {
  const labels = {
    milvus: 'Milvus 语义检索',
    keyword_fallback: '关键词降级',
    memory_fallback: '内存降级'
  }
  return labels[props.references?.searchMode] || props.references?.searchMode || '未知模式'
})

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
