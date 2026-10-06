import test from 'node:test'
import assert from 'node:assert/strict'
import { renderAuditMarkdown, renderMarkdown } from './markdownRenderer.js'

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

test('renderMarkdown converts pipe tables and hides separator rows', () => {
  const html = renderMarkdown('| 技能 | 说明 |\n|--------|------|\n| Redis | 缓存 |')

  assert.match(html, /<table class="markdown-table">/)
  assert.match(html, /<th>技能<\/th>/)
  assert.match(html, /<td>Redis<\/td>/)
  assert.doesNotMatch(html, /--------|\| 技能 \|/)
})

test('renderMarkdown converts numbered lists and blockquotes', () => {
  const html = renderMarkdown('1. 第一步\n2. 第二步\n> 结论')

  assert.match(html, /<ul><li>第一步<\/li><li>第二步<\/li><\/ul>/)
  assert.match(html, /<blockquote>结论<\/blockquote>/)
})

test('renderMarkdown removes audit sections from the answer body', () => {
  const html = renderMarkdown('回答正文\n\n## 证据来源\n- resume.md\n\n### 置信度\n- 86%\n\n### 风险\n- 资料不足')

  assert.match(html, /回答正文/)
  assert.doesNotMatch(html, /证据来源|置信度|风险/)
})

test('renderAuditMarkdown renders escaped audit headings with emoji titles', () => {
  const html = renderAuditMarkdown('正文\\n\\n## 🧾证据来源、置信度与风险（按协作链路披露）\\n\\n- PLANNER：路由偏弱\\n\\n> 风险声明：需要修正路由')

  assert.match(html, /<h2>🧾证据来源/)
  assert.match(html, /PLANNER：路由偏弱/)
})

test('renderMarkdown normalizes escaped line breaks in the answer body', () => {
  const html = renderMarkdown('第一段\\n\\n第二段')

  assert.match(html, /第一段<br><br>第二段/)
})
