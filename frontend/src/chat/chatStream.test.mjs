import test from 'node:test'
import assert from 'node:assert/strict'
import { parseSseChunk, streamChat } from './chatStream.js'

test('parseSseChunk parses named SSE events and preserves unfinished frame', () => {
  const state = { buffer: '' }
  const first = parseSseChunk('event: plan\ndata: {"steps":["检索资料"]}\n\nevent: message\ndata: 你好', state)

  assert.deepEqual(first, [
    { event: 'plan', data: { steps: ['检索资料'] } }
  ])
  assert.equal(state.buffer, 'event: message\ndata: 你好')

  const second = parseSseChunk('，zyagent\n\n', state)

  assert.deepEqual(second, [
    { event: 'message', data: '你好，zyagent' }
  ])
  assert.equal(state.buffer, '')
})

test('parseSseChunk parses skill events', () => {
  const state = { buffer: '' }
  const events = parseSseChunk('event: skill\ndata: {"id":"resume_coach_skill","name":"简历顾问 Skill"}\n\n', state)

  assert.deepEqual(events, [
    { event: 'skill', data: { id: 'resume_coach_skill', name: '简历顾问 Skill' } }
  ])
})

test('parseSseChunk parses references events with search mode and hits', () => {
  const state = { buffer: '' }
  const events = parseSseChunk('event: references\ndata: {"searchMode":"milvus","hits":[{"filename":"resume-demo.md","score":0.91}]}\n\n', state)

  assert.deepEqual(events, [
    {
      event: 'references',
      data: {
        searchMode: 'milvus',
        hits: [{ filename: 'resume-demo.md', score: 0.91 }]
      }
    }
  ])
})

test('streamChat dispatches references events', async () => {
  const originalFetch = globalThis.fetch
  const encoder = new TextEncoder()
  globalThis.fetch = async () => ({
    ok: true,
    body: new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode('event: references\ndata: {"searchMode":"milvus","hits":[{"filename":"resume-demo.md","score":0.91}]}\n\n'))
        controller.close()
      }
    })
  })

  try {
    let received = null
    const stream = await streamChat({ message: 'test' }, {
      onReferences: references => {
        received = references
      }
    })
    await stream.done

    assert.deepEqual(received, {
      searchMode: 'milvus',
      hits: [{ filename: 'resume-demo.md', score: 0.91 }]
    })
  } finally {
    globalThis.fetch = originalFetch
  }
})
