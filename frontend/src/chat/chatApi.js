import { streamChat } from './chatStream'

async function parse(response) {
  const data = await response.json()
  if (!response.ok || data.success === false) throw new Error(data.message || '请求失败')
  return data.data
}

export const chatApi = {
  stream: streamChat,
  complete: payload => fetch('/api/chat/complete', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }).then(parse),
  listSessions: () => fetch('/api/chat/sessions').then(parse),
  createSession: payload => fetch('/api/chat/sessions', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }).then(parse),
  listMessages: sessionId => fetch(`/api/chat/sessions/${sessionId}/messages`).then(parse),
  deleteSession: sessionId => fetch(`/api/chat/sessions/${sessionId}`, { method: 'DELETE' }).then(parse),
  getTaskSteps: taskId => fetch(`/api/agent/tasks/${encodeURIComponent(taskId)}/steps`).then(parse),
  retryTask: taskId => fetch(`/api/agent/tasks/${encodeURIComponent(taskId)}/retry`, { method: 'POST' }).then(parse)
}
