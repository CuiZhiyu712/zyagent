import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const componentFiles = [
  'ReferenceTrace.vue',
  'CollaborationTrace.vue',
  'PipelineTrace.vue',
  'ToolTrace.vue',
  'ObservabilityTrace.vue'
]

test('nested agent trace panels are collapsed by default', () => {
  for (const file of componentFiles) {
    const source = readFileSync(resolve(process.cwd(), `src/components/chat/${file}`), 'utf8')
    assert.doesNotMatch(source, /<details[^>]*\sopen(?:\s|>)/, `${file} should not force details open`)
  }
})
