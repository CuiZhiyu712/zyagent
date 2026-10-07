export function parseSseChunk(chunk, state) {
  state.buffer += chunk
  const frames = state.buffer.split(/\r?\n\r?\n/)
  state.buffer = frames.pop() ?? ''

  return frames
    .map(parseFrame)
    .filter(Boolean)
}

export async function streamChat(payload, handlers = {}) {
  const controller = new AbortController()
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
    signal: controller.signal
  })

  if (!response.ok || !response.body) {
    throw new Error(`流式请求失败：${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  const state = { buffer: '' }

  const done = (async () => {
    try {
      while (true) {
        const { value, done } = await reader.read()
        if (done) break
        const events = parseSseChunk(decoder.decode(value, { stream: true }), state)
        for (const event of events) {
          handlers.onEvent?.(event)
          if (event.event === 'skill') handlers.onSkill?.(event.data)
          if (event.event === 'plan') handlers.onPlan?.(event.data)
          if (event.event === 'tools') handlers.onTools?.(event.data)
          if (event.event === 'references') handlers.onReferences?.(event.data)
          if (event.event === 'task') handlers.onTask?.(event.data)
          if (event.event === 'step') handlers.onStep?.(event.data)
          if (event.event === 'status') handlers.onStatus?.(event.data)
          if (event.event === 'route') handlers.onRoute?.(event.data)
          if (event.event === 'usage') handlers.onUsage?.(event.data)
          if (event.event === 'metrics') handlers.onMetrics?.(event.data)
          if (event.event === 'memory') handlers.onMemory?.(event.data)
          if (event.event === 'collaboration') handlers.onCollaboration?.(event.data)
          if (event.event === 'pipeline') handlers.onPipeline?.(event.data)
          if (event.event === 'message') handlers.onMessage?.(String(event.data))
        }
      }

      const trailing = parseSseChunk('\n\n', state)
      for (const event of trailing) {
        handlers.onEvent?.(event)
        if (event.event === 'skill') handlers.onSkill?.(event.data)
        if (event.event === 'plan') handlers.onPlan?.(event.data)
        if (event.event === 'tools') handlers.onTools?.(event.data)
        if (event.event === 'references') handlers.onReferences?.(event.data)
        if (event.event === 'task') handlers.onTask?.(event.data)
        if (event.event === 'step') handlers.onStep?.(event.data)
        if (event.event === 'status') handlers.onStatus?.(event.data)
        if (event.event === 'route') handlers.onRoute?.(event.data)
        if (event.event === 'usage') handlers.onUsage?.(event.data)
        if (event.event === 'metrics') handlers.onMetrics?.(event.data)
        if (event.event === 'memory') handlers.onMemory?.(event.data)
        if (event.event === 'collaboration') handlers.onCollaboration?.(event.data)
        if (event.event === 'pipeline') handlers.onPipeline?.(event.data)
        if (event.event === 'message') handlers.onMessage?.(String(event.data))
      }
      handlers.onDone?.()
    } catch (error) {
      if (error.name === 'AbortError') {
        handlers.onAbort?.()
      } else {
        handlers.onError?.(error)
      }
    }
  })()

  return { controller, done }
}

function parseFrame(frame) {
  const lines = frame.split(/\r?\n/)
  let event = 'message'
  const dataLines = []

  for (const line of lines) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }

  if (!dataLines.length) return null
  const rawData = dataLines.join('\n')
  return { event, data: parseData(rawData) }
}

function parseData(rawData) {
  try {
    return JSON.parse(rawData)
  } catch {
    return rawData
  }
}
