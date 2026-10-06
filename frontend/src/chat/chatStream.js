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
        events.forEach(event => dispatchEvent(event, handlers))
      }

      const trailing = parseSseChunk('\n\n', state)
      trailing.forEach(event => dispatchEvent(event, handlers))
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

const handlerByEvent = {
  skill: 'onSkill', plan: 'onPlan', tools: 'onTools', references: 'onReferences',
  task: 'onTask', step: 'onStep', status: 'onStatus', route: 'onRoute', usage: 'onUsage',
  metrics: 'onMetrics', memory: 'onMemory', collaboration: 'onCollaboration',
  pipeline: 'onPipeline', message: 'onMessage'
}

function dispatchEvent(event, handlers) {
  handlers.onEvent?.(event)
  const handler = handlers[handlerByEvent[event.event]]
  if (handler) handler(event.event === 'message' ? String(event.data) : event.data)
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
