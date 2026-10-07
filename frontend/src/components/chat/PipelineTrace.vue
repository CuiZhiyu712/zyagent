<template>
  <div v-if="pipeline?.stages?.length" class="pipeline-trace">
    <details>
      <summary>完整 Agent 调用链</summary>
      <div class="pipeline-lane">
        <template v-for="(stage, index) in pipeline.stages" :key="stage.id">
          <div class="pipeline-stage">
            <span class="pipeline-index">{{ index + 1 }}</span>
            <strong>{{ stage.name }}</strong>
            <small :class="String(stage.status || '').toLowerCase()">{{ statusLabel(stage.status) }}</small>
            <p>{{ stage.detail }}</p>
          </div>
          <span v-if="index < pipeline.stages.length - 1" class="pipeline-arrow">→</span>
        </template>
      </div>
    </details>
  </div>
</template>

<script setup>
defineProps({ pipeline: { type: Object, default: null } })

function statusLabel(status) {
  return { SUCCESS: '完成', PENDING: '待生成', SKIPPED: '跳过', FAILED: '失败' }[status] || status || '未知'
}
</script>
