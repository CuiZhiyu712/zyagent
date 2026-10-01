import test from 'node:test'
import assert from 'node:assert/strict'
import { renderMarkdown } from './markdownRenderer.js'

test('renderMarkdown converts markdown headings instead of showing hash marks', () => {
  const html = renderMarkdown('### Agent 任务规划可视化\n正文')

  assert.match(html, /<h3>Agent 任务规划可视化<\/h3>/)
  assert.doesNotMatch(html, /### Agent/)
})

test('renderMarkdown keeps hash marks inside fenced code blocks', () => {
  const html = renderMarkdown('```md\n### raw heading\n```')

  assert.match(html, /<pre class="code-block"><code>md\n### raw heading\n<\/code><\/pre>/)
})

test('renderMarkdown converts simple bullet lists', () => {
  const html = renderMarkdown('- Planner\n- Retriever')

  assert.match(html, /<ul><li>Planner<\/li><li>Retriever<\/li><\/ul>/)
})
