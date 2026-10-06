import test from 'node:test'
import assert from 'node:assert/strict'
import { mergeChatMessages, readChatCache, restoreActiveSessionId, writeChatCache } from './chatSessionStore.js'

function createStorage() {
  const values = new Map()
  return {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: key => values.delete(key)
  }
}

test('chat cache restores the selected session and its messages after remount', () => {
  const storage = createStorage()
  const sessions = [
    { id: 'session-1', title: '第一条', messages: [{ id: 'm1', role: 'user', content: 'A' }], count: 1 },
    { id: 'session-2', title: '当前对话', messages: [{ id: 'm2', role: 'assistant', content: 'B' }], count: 1 }
  ]

  writeChatCache(sessions, 'session-2', storage)
  const cache = readChatCache(storage)

  assert.equal(restoreActiveSessionId(sessions, cache), 'session-2')
  assert.deepEqual(cache.sessions, sessions)
})

test('stale cached session falls back to the first available session', () => {
  const sessions = [{ id: 'session-1', title: '第一条', messages: [], count: 0 }]
  assert.equal(
    restoreActiveSessionId(sessions, { activeSessionId: 'deleted-session' }),
    'session-1'
  )
})

test('an empty history response does not erase messages already held by the session', () => {
  const current = [{ id: 'm1', role: 'assistant', content: '已生成的回答' }]
  assert.deepEqual(mergeChatMessages(current, []), current)
  assert.deepEqual(mergeChatMessages(
    [{ id: 'm1', role: 'user', content: '问题' }, { id: 'm2', role: 'assistant', content: '回答' }],
    [{ id: 'remote-1', role: 'user', content: '问题' }]
  ), [{ id: 'm1', role: 'user', content: '问题' }, { id: 'm2', role: 'assistant', content: '回答' }])
  assert.deepEqual(mergeChatMessages(
    [{ id: 'm1', role: 'user', content: '问题' }, { id: 'm2', role: 'assistant', content: '回答' }],
    [{ id: 'remote-1', role: 'user', content: '问题' }, { id: 'remote-2', role: 'assistant', content: '' }]
  ), [{ id: 'm1', role: 'user', content: '问题' }, { id: 'm2', role: 'assistant', content: '回答' }])
  assert.deepEqual(
    mergeChatMessages(current, [{ id: 'm2', role: 'assistant', content: '服务端历史' }]),
    [{ id: 'm2', role: 'assistant', content: '服务端历史' }]
  )
})
