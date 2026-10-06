import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const template = readFileSync(resolve(process.cwd(), 'src/components/chat/MessageList.vue'), 'utf8')

test('assistant execution traces are inside a collapsed details panel', () => {
  assert.match(template, /<details v-if="message\.role === 'assistant'" class="agent-details">/)
  assert.match(template, /<summary>Agent 执行详情<\/summary>/)
  assert.match(template, /<ToolTrace[\s\S]*<\/details>/)
})

test('evidence audit is rendered as a parallel collapsed module', () => {
  assert.match(template, /<AuditTrace[\s\S]*:references="message\.references"/)
  const audit = readFileSync(resolve(process.cwd(), 'src/components/chat/AuditTrace.vue'), 'utf8')
  assert.match(audit, /<details v-if="hasAudit" class="audit-details">/)
  assert.match(audit, /<summary>证据来源 · 置信度 · 风险<\/summary>/)
})
