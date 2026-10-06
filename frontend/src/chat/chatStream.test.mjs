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

test('parseSseChunk parses task lifecycle events', () => {
  const events = parseSseChunk('event: task\ndata: {"id":"task-1","state":"RUNNING"}\n\n', { buffer: '' })

  assert.deepEqual(events, [
    { event: 'task', data: { id: 'task-1', state: 'RUNNING' } }
  ])
})

test('parseSseChunk parses the unified pipeline event', () => {
  const events = parseSseChunk('event: pipeline\ndata: {"stages":[{"id":"route","status":"SUCCESS"}]}\n\n', { buffer: '' })

  assert.deepEqual(events, [
    { event: 'pipeline', data: { stages: [{ id: 'route', status: 'SUCCESS' }] } }
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

test('streamChat dispatches route usage metrics and memory events', async () => {
  const originalFetch = globalThis.fetch
  const encoder = new TextEncoder()
  globalThis.fetch = async () => ({
    ok: true,
    body: new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode([
          'event: route',
          'data: {"category":"JOB","skillId":"job_analysis_skill","reason":"命中岗位"}',
          '',
          'event: usage',
          'data: {"promptTokens":10,"completionTokens":5,"totalTokens":15,"estimated":true}',
          '',
          'event: metrics',
          'data: {"toolTotal":2,"toolSuccess":1,"toolFailed":1,"ragHitCount":3}',
          '',
          'event: memory',
          'data: {"recentMessageCount":2,"summary":"最近对话上下文"}',
          '',
          ''
        ].join('\n')))
        controller.close()
      }
    })
  })

  try {
    const received = {}
    const stream = await streamChat({ message: 'test' }, {
      onRoute: route => {
        received.route = route
      },
      onUsage: usage => {
        received.usage = usage
      },
      onMetrics: metrics => {
        received.metrics = metrics
      },
      onMemory: memory => {
        received.memory = memory
      }
    })
    await stream.done

    assert.equal(received.route.skillId, 'job_analysis_skill')
    assert.equal(received.usage.totalTokens, 15)
    assert.equal(received.metrics.ragHitCount, 3)
    assert.equal(received.memory.recentMessageCount, 2)
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('streamChat dispatches collaboration events', async () => {
  const originalFetch = globalThis.fetch
  const encoder = new TextEncoder()
  globalThis.fetch = async () => ({
    ok: true,
    body: new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode([
          'event: collaboration',
          'data: {"agents":[{"role":"PLANNER","status":"SUCCESS"}],"artifacts":[{"producer":"PLANNER","type":"plan"}],"finalReview":"回答需要引用中间结果"}',
          '',
          ''
        ].join('\n')))
        controller.close()
      }
    })
  })

  try {
    let received = null
    const stream = await streamChat({ message: 'test' }, {
      onCollaboration: collaboration => {
        received = collaboration
      }
    })
    await stream.done

    assert.equal(received.agents[0].role, 'PLANNER')
    assert.equal(received.artifacts[0].type, 'plan')
    assert.equal(received.finalReview, '回答需要引用中间结果')
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('streamChat dispatches task step and status events', async () => {
  const originalFetch = globalThis.fetch
  const encoder = new TextEncoder()
  globalThis.fetch = async () => ({
    ok: true,
    body: new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode([
          'event: task',
          'data: {"id":"task-1","state":"RUNNING"}',
          '',
          'event: step',
          'data: {"taskId":"task-1","stepNo":1,"status":"SUCCESS"}',
          '',
          'event: status',
          'data: {"taskId":"task-1","state":"SUCCEEDED","persistence":"memory"}',
          '',
          ''
        ].join('\n')))
        controller.close()
      }
    })
  })

  try {
    const tasks = []
    const steps = []
    let status = null
    const stream = await streamChat({ message: 'test' }, {
      onTask: task => tasks.push(task),
      onStep: step => steps.push(step),
      onStatus: value => {
        status = value
      }
    })
    await stream.done

    assert.equal(tasks[0].id, 'task-1')
    assert.equal(steps[0].stepNo, 1)
    assert.equal(steps[0].status, 'SUCCESS')
    assert.equal(status.state, 'SUCCEEDED')
    assert.equal(status.persistence, 'memory')
  } finally {
    globalThis.fetch = originalFetch
  }
})
