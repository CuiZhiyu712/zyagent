export function renderMarkdown(text) {
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

  html = html
    .replace(/^######\s+(.+)$/gm, '<h6>$1</h6>')
    .replace(/^#####\s+(.+)$/gm, '<h5>$1</h5>')
    .replace(/^####\s+(.+)$/gm, '<h4>$1</h4>')
    .replace(/^###\s+(.+)$/gm, '<h3>$1</h3>')
    .replace(/^##\s+(.+)$/gm, '<h2>$1</h2>')
    .replace(/^#\s+(.+)$/gm, '<h1>$1</h1>')
    .replace(/^\s*[-*]\s+(.+)$/gm, '<li>$1</li>')
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

function escapeHtml(value) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}
