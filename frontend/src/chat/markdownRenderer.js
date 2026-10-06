export function renderMarkdown(text) {
  const audit = extractAuditSection(normalizeMarkdownLineBreaks(text || ''))
  const bodyHtml = renderMarkdownBody(audit.body)
  return bodyHtml
}

export function renderAuditMarkdown(text) {
  const audit = extractAuditSection(normalizeMarkdownLineBreaks(text || ''))
  return audit.audit ? renderMarkdownBody(audit.audit) : ''
}

function normalizeMarkdownLineBreaks(text) {
  return String(text || '')
    .replace(/\\r\\n/g, '\n')
    .replace(/\\n/g, '\n')
    .replace(/\\\s*\n/g, '\n')
}

function renderMarkdownBody(text) {
  const blocks = []
  const inlines = []
  let html = escapeHtml(text || '')

  html = html.replace(/```([\s\S]*?)```/g, (_, code) => {
    const token = `@@CODE_BLOCK_${blocks.length}@@`
    blocks.push(`<pre class="code-block"><code>${code}</code></pre>`)
    return token
  })

  html = html.replace(/`([^`]+)`/g, (_, code) => {
    const token = `@@INLINE_CODE_${inlines.length}@@`
    inlines.push(`<code>${code}</code>`)
    return token
  })

  html = html.replace(/(^|<br>)(\|[^\n]+\|\n\|\s*:?-{2,}:?\s*(?:\|\s*:?-{2,}:?\s*)+\|\n(?:\|[^\n]+\|\n?)+)/gm, (_, prefix, table) => {
    return prefix + renderTable(table)
  })

  html = html
    .replace(/^######\s*(.+)$/gm, '<h6>$1</h6>')
    .replace(/^#####\s*(.+)$/gm, '<h5>$1</h5>')
    .replace(/^####\s*(.+)$/gm, '<h4>$1</h4>')
    .replace(/^###\s*(.+)$/gm, '<h3>$1</h3>')
    .replace(/^##\s*(.+)$/gm, '<h2>$1</h2>')
    .replace(/^#\s*(.+)$/gm, '<h1>$1</h1>')
    .replace(/^\s*(?:[-*]|\d+[.)])\s+(.+)$/gm, '<li>$1</li>')
    .replace(/^(?:>|&gt;)\s?(.+)$/gm, '<blockquote>$1</blockquote>')
    .replace(/^\s*([-*_])(?:\s*\1){2,}\s*$/gm, '<hr>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\n/g, '<br>')
    .replace(/(<li>.*?<\/li>)(?:<br>(<li>.*?<\/li>))+/g, match => `<ul>${match.replace(/<br>/g, '')}</ul>`)

  blocks.forEach((block, index) => {
    html = html.replace(`@@CODE_BLOCK_${index}@@`, block)
  })
  inlines.forEach((inline, index) => {
    html = html.replace(`@@INLINE_CODE_${index}@@`, inline)
  })

  return html
}

function extractAuditSection(text) {
  const normalized = normalizeMarkdownLineBreaks(text)
  const lines = normalized.split(/\r?\n/)
  let fenced = false
  let start = -1
  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index].trim()
    if (line.startsWith('```')) {
      fenced = !fenced
      continue
    }
    if (fenced || start >= 0) continue
    const heading = line.match(/^\\?\s*#{1,6}\s+(.+?)\s*$/)
    if (heading && /证据来源|置信度|风险/.test(heading[1])) {
      start = index
    }
  }
  if (start < 0) return { body: text, audit: '' }
  return {
    body: lines.slice(0, start).join('\n').trimEnd(),
    audit: lines.slice(start).join('\n').trim()
  }
}

function renderTable(source) {
  const rows = source.trim().split(/\r?\n/).filter(Boolean)
  if (rows.length < 2) return source
  const cells = line => line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map(cell => cell.trim())
  const header = cells(rows[0])
  const body = rows.slice(2).map(cells)
  const headHtml = header.map(cell => `<th>${cell}</th>`).join('')
  const bodyHtml = body.map(row => `<tr>${header.map((_, index) => `<td>${row[index] || ''}</td>`).join('')}</tr>`).join('')
  return `<div class="markdown-table-wrap"><table class="markdown-table"><thead><tr>${headHtml}</tr></thead><tbody>${bodyHtml}</tbody></table></div>`
}

function escapeHtml(value) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}
