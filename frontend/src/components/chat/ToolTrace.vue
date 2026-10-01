<template>
  <div v-if="skill || plan || tools?.length" class="tool-trace">
    <div v-if="skill" class="skill-line">
      <span>Skill</span>
      <strong>{{ skill.name || skill.id }}</strong>
    </div>
    <details v-if="plan" open>
      <summary>Plan-Executor</summary>
      <ol>
        <li v-for="step in plan.steps || []" :key="stepKey(step)" class="plan-step">
          <span>{{ stepTitle(step) }}</span>
          <small v-if="stepStatus(step)" :class="['step-status', stepStatus(step).toLowerCase()]">{{ stepStatusLabel(step) }}</small>
          <em v-if="step.toolName">{{ step.toolName }}</em>
          <em v-if="step.inputSummary">输入：{{ step.inputSummary }}</em>
          <em v-if="step.outputSummary">输出：{{ step.outputSummary }}</em>
          <strong v-if="step.errorMessage">{{ step.errorMessage }}</strong>
        </li>
      </ol>
    </details>
    <details v-if="tools?.length">
      <summary>MCP / ReAct 工具调用</summary>
      <div v-for="tool in tools" :key="`${tool.toolName}-${tool.success}`" class="tool-row">
        <span class="tool-name">{{ tool.toolName }}</span>
        <span :class="['tool-status', tool.success ? 'ok' : 'fail']">{{ tool.success ? '成功' : '失败' }}</span>
        <pre>{{ formatOutput(tool.output || tool.errorMessage) }}</pre>
      </div>
    </details>
  </div>
</template>

<script setup>
defineProps({
  skill: {
    type: Object,
    default: null
  },
  plan: {
    type: Object,
    default: null
  },
  tools: {
    type: Array,
    default: () => []
  }
})

function formatOutput(value) {
  if (value == null) return ''
  if (typeof value === 'string') return value
  return JSON.stringify(value, null, 2)
}

function stepKey(step) {
  if (typeof step === 'string') return step
  return `${step.title || ''}-${step.toolName || ''}-${step.status || ''}`
}

function stepTitle(step) {
  return typeof step === 'string' ? step : step.title
}

function stepStatus(step) {
  return typeof step === 'string' ? '' : step.status || ''
}

function stepStatusLabel(step) {
  const labels = {
    PLANNED: '计划中',
    RUNNING: '执行中',
    SUCCESS: '成功',
    FAILED: '失败',
    RETRYING: '重试中',
    SKIPPED: '已跳过',
    REPLANNED: '已重规划'
  }
  return labels[stepStatus(step)] || stepStatus(step)
}
</script>
